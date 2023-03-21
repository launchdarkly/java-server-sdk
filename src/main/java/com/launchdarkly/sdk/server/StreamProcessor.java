package com.launchdarkly.sdk.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.ErrorStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.FaultEvent;
import com.launchdarkly.eventsource.HttpConnectStrategy;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.StreamClosedByCallerException;
import com.launchdarkly.eventsource.StreamClosedByServerException;
import com.launchdarkly.eventsource.StreamClosedWithIncompleteMessageException;
import com.launchdarkly.eventsource.StreamEvent;
import com.launchdarkly.eventsource.StreamException;
import com.launchdarkly.eventsource.StreamHttpErrorException;
import com.launchdarkly.eventsource.StreamIOException;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogValues;
import com.launchdarkly.sdk.internal.events.DiagnosticStore;
import com.launchdarkly.sdk.internal.http.HttpConsts;
import com.launchdarkly.sdk.internal.http.HttpHelpers;
import com.launchdarkly.sdk.internal.http.HttpProperties;
import com.launchdarkly.sdk.server.StreamProcessorEvents.DeleteData;
import com.launchdarkly.sdk.server.StreamProcessorEvents.PatchData;
import com.launchdarkly.sdk.server.StreamProcessorEvents.PutData;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.SerializationException;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.launchdarkly.sdk.internal.http.HttpErrors.checkIfErrorIsRecoverableAndLog;
import static com.launchdarkly.sdk.internal.http.HttpErrors.httpErrorDescription;

import okhttp3.Headers;

/**
 * Implementation of the streaming data source, not including the lower-level SSE implementation which is in
 * okhttp-eventsource.
 * 
 * Error handling works as follows:
 * 1. If any event is malformed, we must assume the stream is broken and we may have missed updates. Set the
 * data source state to INTERRUPTED, with an error kind of INVALID_DATA, and restart the stream.
 * 2. If we try to put updates into the data store and we get an error, we must assume something's wrong with the
 * data store. We don't have to log this error because it is logged by DataSourceUpdatesImpl, which will also set
 * our state to INTERRUPTED for us.
 * 2a. If the data store supports status notifications (which all persistent stores normally do), then we can
 * assume it has entered a failed state and will notify us once it is working again. If and when it recovers, then
 * it will tell us whether we need to restart the stream (to ensure that we haven't missed any updates), or
 * whether it has already persisted all of the stream updates we received during the outage.
 * 2b. If the data store doesn't support status notifications (which is normally only true of the in-memory store)
 * then we don't know the significance of the error, but we must assume that updates have been lost, so we'll
 * restart the stream.
 * 3. If we receive an unrecoverable error like HTTP 401, we close the stream and don't retry, and set the state
 * to OFF. Any other HTTP error or network error causes a retry with backoff, with a state of INTERRUPTED.
 * 4. We set the Future returned by start() to tell the client initialization logic that initialization has either
 * succeeded (we got an initial payload and successfully stored it) or permanently failed (we got a 401, etc.).
 * Otherwise, the client initialization method may time out but we will still be retrying in the background, and
 * if we succeed then the client can detect that we're initialized now by calling our Initialized method.
 */
final class StreamProcessor implements DataSource {
  private static final String PUT = "put";
  private static final String PATCH = "patch";
  private static final String DELETE = "delete";
  private static final Duration DEAD_CONNECTION_INTERVAL = Duration.ofSeconds(300);
  private static final String ERROR_CONTEXT_MESSAGE = "in stream connection";
  private static final String WILL_RETRY_MESSAGE = "will retry";

  private final DataSourceUpdateSink dataSourceUpdates;
  private final HttpProperties httpProperties;
  private final Headers headers;
  @VisibleForTesting
  final URI streamUri;
  @VisibleForTesting
  final Duration initialReconnectDelay;
  private final DiagnosticStore diagnosticAccumulator;
  private final int threadPriority;
  private final DataStoreStatusProvider.StatusListener statusListener;
  private volatile EventSource es;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private volatile long esStarted = 0;
  private volatile boolean lastStoreUpdateFailed = false;
  private final LDLogger logger;

  StreamProcessor(
      HttpProperties httpProperties,
      DataSourceUpdateSink dataSourceUpdates,
      int threadPriority,
      DiagnosticStore diagnosticAccumulator,
      URI streamUri,
      String payloadFilter,
      Duration initialReconnectDelay,
      LDLogger logger) {
    this.dataSourceUpdates = dataSourceUpdates;
    this.httpProperties = httpProperties;
    this.diagnosticAccumulator = diagnosticAccumulator;
    this.threadPriority = threadPriority;
    this.initialReconnectDelay = initialReconnectDelay;
    this.logger = logger;

    URI tempUri = HttpHelpers.concatenateUriPath(streamUri, StandardEndpoints.STREAMING_REQUEST_PATH);
    if (payloadFilter != null) {
      if (!payloadFilter.isEmpty()) {
        tempUri = HttpHelpers.addQueryParam(tempUri, HttpConsts.QUERY_PARAM_FILTER, payloadFilter);
      } else {
        logger.info("Payload filter \"{}\" is not valid, not applying filter.", payloadFilter);
      }
    }
    this.streamUri = tempUri;


    this.headers = httpProperties.toHeadersBuilder()
        .add("Accept", "text/event-stream")
        .build();
    
    if (dataSourceUpdates.getDataStoreStatusProvider() != null &&
        dataSourceUpdates.getDataStoreStatusProvider().isStatusMonitoringEnabled()) {
      this.statusListener = this::onStoreStatusChanged;
      dataSourceUpdates.getDataStoreStatusProvider().addStatusListener(statusListener);
    } else {
      this.statusListener = null;
    }
  }

  private void onStoreStatusChanged(DataStoreStatusProvider.Status newStatus) {
    if (newStatus.isAvailable()) {
      if (newStatus.isRefreshNeeded()) {
        // The store has just transitioned from unavailable to available, and we can't guarantee that
        // all of the latest data got cached, so let's restart the stream to refresh all the data.
        EventSource stream = es;
        if (stream != null) {
          logger.warn("Restarting stream to refresh data after data store outage");
          stream.interrupt();
        }
      }
    }
  }
  
  @Override
  public Future<Void> start() {
    final CompletableFuture<Void> initFuture = new CompletableFuture<>();

    // Notes about the configuration of the EventSource below:
    //
    // 1. Setting streamEventData(true) is an optimization to let us read the event's data field directly
    // from HTTP response stream, rather than waiting for the whole event to be buffered in memory. See
    // the Javadoc for EventSource.Builder.streamEventData for more details. This relies on an assumption
    // that the LD streaming endpoints will always send the "event:" field before the "data:" field.
    //
    // 2. The readTimeout here is not the same read timeout that can be set in LDConfig.  We default to a
    // smaller one there because we don't expect long delays within any *non*-streaming response that the
    // LD client gets. A read timeout on the stream will result in the connection being cycled, so we set
    // this to be slightly more than the expected interval between heartbeat signals.

    HttpConnectStrategy eventSourceHttpConfig = ConnectStrategy.http(this.streamUri)
        .headers(headers)
        .clientBuilderActions(clientBuilder -> {
          httpProperties.applyToHttpClientBuilder(clientBuilder);
        })
        // Set readTimeout last, to ensure that this hard-coded value overrides any other read
        // timeout that might have been set by httpProperties (see comment about readTimeout above).
        .readTimeout(DEAD_CONNECTION_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    EventSource.Builder builder = new EventSource.Builder(eventSourceHttpConfig)
        .errorStrategy(ErrorStrategy.alwaysContinue())
          // alwaysContinue means we want EventSource to give us a FaultEvent rather
          // than throwing an exception if the stream fails
        .logger(logger)
        .readBufferSize(5000)
        .streamEventData(true)
        .expectFields("event")        
        .retryDelay(initialReconnectDelay.toMillis(), TimeUnit.MILLISECONDS);
    es = builder.build();
    
    Thread thread = new Thread(() -> {
      esStarted = System.currentTimeMillis();
      
      // We are deliberately not calling es.start() here, but just iterating over es.anyEvents().
      // EventSource will start the stream connection either way, but if we called start(), it
      // would swallow any FaultEvents that happened during the initial conection attempt; we
      // want to know about those.
      try {
        for (StreamEvent event: es.anyEvents()) {
          if (!handleEvent(event, initFuture)) {
            // handleEvent returns false if we should fall through and end the thread
            break;
          }
        }
      } catch (Exception e) {
        // Any uncaught runtime exception at this point would be coming from es.anyEvents().
        // That's not expected-- all deliberate EventSource exceptions are checked exceptions.
        // So we have to assume something is wrong that we can't recover from at this point,
        // and just let the thread terminate. That's better than having the thread be killed
        // by an uncaught exception.
        if (closed.get()) {
          return; // ignore any exception that's just a side effect of stopping the EventSource
        }
        logger.error("Stream thread has ended due to unexpected exception: {}", LogValues.exceptionSummary(e));
        // deliberately log stacktrace at error level since this is an unusual circumstance
        logger.error(LogValues.exceptionTrace(e));
      }
    });
    thread.setName("LaunchDarkly-streaming");
    thread.setDaemon(true);
    thread.setPriority(threadPriority);
    thread.start();
        
    return initFuture;
  }

  private void recordStreamInit(boolean failed) {
    if (diagnosticAccumulator != null && esStarted != 0) {
      diagnosticAccumulator.recordStreamInit(esStarted, System.currentTimeMillis() - esStarted, failed);
    }
  }

  @Override
  public void close() throws IOException {
    if (closed.getAndSet(true)) {
      return; // was already closed
    }
    logger.info("Closing LaunchDarkly StreamProcessor");
    if (statusListener != null) {
      dataSourceUpdates.getDataStoreStatusProvider().removeStatusListener(statusListener);
    }
    if (es != null) {
      es.close();
    }
    dataSourceUpdates.updateStatus(State.OFF, null);
  }

  @Override
  public boolean isInitialized() {
    return initialized.get();
  }

  // Handles a single StreamEvent and returns true if we should keep the stream alive,
  // or false if we should shut down permanently.
  private boolean handleEvent(StreamEvent event, CompletableFuture<Void> initFuture) {
    if (closed.get()) {
      return false;
    }
    logger.debug("Received StreamEvent: {}", event);    
    if (event instanceof MessageEvent) {
      handleMessage((MessageEvent)event, initFuture);
    } else if (event instanceof FaultEvent) {
      return handleError(((FaultEvent)event).getCause(), initFuture);
    }
    return true;
  }
  
  private void handleMessage(MessageEvent event, CompletableFuture<Void> initFuture) {
    try {
      switch (event.getEventName()) {
        case PUT:
          handlePut(event.getDataReader(), initFuture);
          break;
       
        case PATCH:
          handlePatch(event.getDataReader());
          break;
          
        case DELETE:
          handleDelete(event.getDataReader()); 
          break;
          
        default:
          logger.warn("Unexpected event found in stream: {}", event.getEventName());
          break;
      }
      lastStoreUpdateFailed = false;
      dataSourceUpdates.updateStatus(State.VALID, null);
    } catch (StreamInputException e) {
      if (exceptionHasCause(e, StreamClosedWithIncompleteMessageException.class)) {
        // JSON parsing failed because the event was cut off prematurely-- because the
        // stream got closed. In this case we should simply throw the event away; the
        // closing of the stream will be handled separately on our next pass through
        // the loop, and is logged separately. There's no point in logging an error
        // about invalid JSON when the real problem is a broken connection; invalid
        // JSON is significant only if we think we have a complete message.
        return;
      }
      logger.error("LaunchDarkly service request failed or received invalid data: {}",
          LogValues.exceptionSummary(e));
      logger.debug(LogValues.exceptionTrace(e));
       
      ErrorInfo errorInfo = new ErrorInfo(
          e.getCause() instanceof IOException ? ErrorKind.NETWORK_ERROR : ErrorKind.INVALID_DATA,
          0,
          e.getCause() == null ? e.getMessage() : e.getCause().toString(),
          Instant.now()
          );
      dataSourceUpdates.updateStatus(State.INTERRUPTED, errorInfo);
     
      es.interrupt();
    } catch (StreamStoreException e) {
      // See item 2 in error handling comments at top of class
      if (statusListener == null) {
        if (!lastStoreUpdateFailed) {
          logger.warn("Restarting stream to ensure that we have the latest data");
        }
        es.interrupt();
      }
      lastStoreUpdateFailed = true;
    } catch (Exception e) {
      logger.warn("Unexpected error from stream processor: {}", LogValues.exceptionSummary(e));
      logger.debug(LogValues.exceptionTrace(e));
    }
  }

  private static boolean exceptionHasCause(Throwable e, Class<?> c) {
    if (c.isAssignableFrom(e.getClass())) {
      return true;
    }
    return e.getCause() != null && exceptionHasCause(e.getCause(), c);
  }
  
  private void handlePut(Reader eventData, CompletableFuture<Void> initFuture)
      throws StreamInputException, StreamStoreException {
    recordStreamInit(false);
    esStarted = 0;
    PutData putData = parseStreamJson(StreamProcessorEvents::parsePutData, eventData);
    if (!dataSourceUpdates.init(putData.data)) {
      throw new StreamStoreException();
    }
    if (!initialized.getAndSet(true)) {
      initFuture.complete(null);
      logger.info("Initialized LaunchDarkly client.");
    }
  }

  private void handlePatch(Reader eventData) throws StreamInputException, StreamStoreException {
    PatchData data = parseStreamJson(StreamProcessorEvents::parsePatchData, eventData);
    if (data.kind == null) {
      return;
    }
    if (!dataSourceUpdates.upsert(data.kind, data.key, data.item)) {
      throw new StreamStoreException();
    }
  }

  private void handleDelete(Reader eventData) throws StreamInputException, StreamStoreException {
    DeleteData data = parseStreamJson(StreamProcessorEvents::parseDeleteData, eventData);
    if (data.kind == null) {
      return;
    }
    ItemDescriptor placeholder = new ItemDescriptor(data.version, null);
    if (!dataSourceUpdates.upsert(data.kind, data.key, placeholder)) {
      throw new StreamStoreException();
    }
  }

  private boolean handleError(StreamException e, CompletableFuture<Void> initFuture) {
    boolean streamFailed = true;
    if (e instanceof StreamClosedByCallerException) {
      // This indicates that we ourselves deliberately restarted the stream, so we don't
      // treat that as a failure in our analytics.
      streamFailed = false;
    } else {
      logger.warn("Encountered EventSource error: {}", LogValues.exceptionSummary(e));      
    }
    recordStreamInit(streamFailed);
    
    if (e instanceof StreamHttpErrorException) {
      int status = ((StreamHttpErrorException)e).getCode();
      ErrorInfo errorInfo = ErrorInfo.fromHttpError(status);

      boolean recoverable = checkIfErrorIsRecoverableAndLog(logger, httpErrorDescription(status),
          ERROR_CONTEXT_MESSAGE, status, WILL_RETRY_MESSAGE);       
      if (recoverable) {
        dataSourceUpdates.updateStatus(State.INTERRUPTED, errorInfo);
        esStarted = System.currentTimeMillis();
        return true; // allow reconnect
      } else {
        dataSourceUpdates.updateStatus(State.OFF, errorInfo);
        initFuture.complete(null); // if client is initializing, make it stop waiting; has no effect if already inited
        return false; // don't reconnect
      }
    }

    boolean isNetworkError = e instanceof StreamIOException || e instanceof StreamClosedByServerException;
    checkIfErrorIsRecoverableAndLog(logger, e.toString(), ERROR_CONTEXT_MESSAGE, 0, WILL_RETRY_MESSAGE);
    ErrorInfo errorInfo = ErrorInfo.fromException(isNetworkError ? ErrorKind.NETWORK_ERROR : ErrorKind.UNKNOWN, e);
    dataSourceUpdates.updateStatus(State.INTERRUPTED, errorInfo);
    return true; // allow reconnect  
  }
  
  private static <T> T parseStreamJson(Function<JsonReader, T> parser, Reader r) throws StreamInputException {
    try {
      try (JsonReader jr = new JsonReader(r)) {
        return parser.apply(jr);
      }
    } catch (JsonParseException e) {
      throw new StreamInputException(e);
    } catch (SerializationException e) {
      throw new StreamInputException(e);
    } catch (IOException e) {
      throw new StreamInputException(e);
    }
  }

  // StreamInputException is either a JSON parsing error *or* a failure to query another endpoint
  // (for indirect/put or indirect/patch); either way, it implies that we were unable to get valid data from LD services.
  @SuppressWarnings("serial")
  private static final class StreamInputException extends Exception {
    public StreamInputException(Throwable cause) {
      super(cause);
    }
  }
  
  // This exception class indicates that the data store failed to persist an update.
  @SuppressWarnings("serial")
  private static final class StreamStoreException extends Exception {}
}
