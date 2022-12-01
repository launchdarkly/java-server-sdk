package com.launchdarkly.sdk.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.launchdarkly.eventsource.ConnectionErrorHandler;
import com.launchdarkly.eventsource.ConnectionErrorHandler.Action;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.UnsuccessfulResponseException;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogValues;
import com.launchdarkly.sdk.internal.events.DiagnosticStore;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.launchdarkly.sdk.internal.http.HttpErrors.checkIfErrorIsRecoverableAndLog;
import static com.launchdarkly.sdk.internal.http.HttpErrors.httpErrorDescription;

import okhttp3.Headers;
import okhttp3.OkHttpClient;

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
  @VisibleForTesting final URI streamUri;
  @VisibleForTesting final Duration initialReconnectDelay;
  private final DiagnosticStore diagnosticAccumulator;
  private final int threadPriority;
  private final DataStoreStatusProvider.StatusListener statusListener;
  private volatile EventSource es;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private volatile long esStarted = 0;
  private volatile boolean lastStoreUpdateFailed = false;
  private final LDLogger logger;

  ConnectionErrorHandler connectionErrorHandler = createDefaultConnectionErrorHandler(); // exposed for testing
  
  StreamProcessor(
      HttpProperties httpProperties,
      DataSourceUpdateSink dataSourceUpdates,
      int threadPriority,
      DiagnosticStore diagnosticAccumulator,
      URI streamUri,
      Duration initialReconnectDelay,
      LDLogger logger
      ) {
    this.dataSourceUpdates = dataSourceUpdates;
    this.httpProperties = httpProperties;
    this.diagnosticAccumulator = diagnosticAccumulator;
    this.threadPriority = threadPriority;
    this.streamUri = streamUri;
    this.initialReconnectDelay = initialReconnectDelay;
    this.logger = logger;

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
          stream.restart();
        }
      }
    }
  }
  
  private ConnectionErrorHandler createDefaultConnectionErrorHandler() {
    return (Throwable t) -> {
      recordStreamInit(true);
      
      if (t instanceof UnsuccessfulResponseException) {
        int status = ((UnsuccessfulResponseException)t).getCode();
        ErrorInfo errorInfo = ErrorInfo.fromHttpError(status);
 
        boolean recoverable = checkIfErrorIsRecoverableAndLog(logger, httpErrorDescription(status),
            ERROR_CONTEXT_MESSAGE, status, WILL_RETRY_MESSAGE);       
        if (recoverable) {
          dataSourceUpdates.updateStatus(State.INTERRUPTED, errorInfo);
          esStarted = System.currentTimeMillis();
          return Action.PROCEED;
        } else {
          dataSourceUpdates.updateStatus(State.OFF, errorInfo);
          return Action.SHUTDOWN; 
        }
      }
      
      checkIfErrorIsRecoverableAndLog(logger, t.toString(), ERROR_CONTEXT_MESSAGE, 0, WILL_RETRY_MESSAGE);
      ErrorInfo errorInfo = ErrorInfo.fromException(t instanceof IOException ? ErrorKind.NETWORK_ERROR : ErrorKind.UNKNOWN, t);
      dataSourceUpdates.updateStatus(State.INTERRUPTED, errorInfo);
      return Action.PROCEED;
    };
  }
  
  @Override
  public Future<Void> start() {
    final CompletableFuture<Void> initFuture = new CompletableFuture<>();

    ConnectionErrorHandler wrappedConnectionErrorHandler = (Throwable t) -> {
      Action result = connectionErrorHandler.onConnectionError(t);
      if (result == Action.SHUTDOWN) {
        initFuture.complete(null); // if client is initializing, make it stop waiting; has no effect if already inited
      }
      return result;
    };

    EventHandler handler = new StreamEventHandler(initFuture);
    URI endpointUri = HttpHelpers.concatenateUriPath(streamUri, StandardEndpoints.STREAMING_REQUEST_PATH);

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
    
    EventSource.Builder builder = new EventSource.Builder(handler, endpointUri)
        .threadPriority(threadPriority)
        .logger(new EventSourceLoggerAdapter())
        .readBufferSize(5000)
        .streamEventData(true)
        .expectFields("event")
        .loggerBaseName(Loggers.DATA_SOURCE_LOGGER_NAME)
        .clientBuilderActions(new EventSource.Builder.ClientConfigurer() {
          public void configure(OkHttpClient.Builder clientBuilder) {
            httpProperties.applyToHttpClientBuilder(clientBuilder);
          }
        })
        .connectionErrorHandler(wrappedConnectionErrorHandler)
        .headers(headers)
        .reconnectTime(initialReconnectDelay)
        .readTimeout(DEAD_CONNECTION_INTERVAL);

    es = builder.build();
    esStarted = System.currentTimeMillis();
    es.start();
    return initFuture;
  }

  private void recordStreamInit(boolean failed) {
    if (diagnosticAccumulator != null && esStarted != 0) {
      diagnosticAccumulator.recordStreamInit(esStarted, System.currentTimeMillis() - esStarted, failed);
    }
  }

  @Override
  public void close() throws IOException {
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

  private class StreamEventHandler implements EventHandler {
    private final CompletableFuture<Void> initFuture;
    
    StreamEventHandler(CompletableFuture<Void> initFuture) {
      this.initFuture = initFuture;
    }
    
    @Override
    public void onOpen() throws Exception {
    }

    @Override
    public void onClosed() throws Exception {
    }

    @Override
    public void onMessage(String eventName, MessageEvent event) throws Exception {
      try {
        switch (eventName) {
          case PUT:
            handlePut(event.getDataReader());
            break;
         
          case PATCH:
            handlePatch(event.getDataReader());
            break;
            
          case DELETE:
            handleDelete(event.getDataReader()); 
            break;
            
          default:
            logger.warn("Unexpected event found in stream: {}", eventName);
            break;
        }
        lastStoreUpdateFailed = false;
        dataSourceUpdates.updateStatus(State.VALID, null);
      } catch (StreamInputException e) {
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
       
        es.restart();
      } catch (StreamStoreException e) {
        // See item 2 in error handling comments at top of class
        if (statusListener == null) {
          if (!lastStoreUpdateFailed) {
            logger.warn("Restarting stream to ensure that we have the latest data");
          }
          es.restart();
        }
        lastStoreUpdateFailed = true;
      } catch (Exception e) {
        logger.warn("Unexpected error from stream processor: {}", LogValues.exceptionSummary(e));
        logger.debug(LogValues.exceptionTrace(e));
      }
    }

    private void handlePut(Reader eventData) throws StreamInputException, StreamStoreException {
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

    @Override
    public void onComment(String comment) {
      logger.debug("Received a heartbeat");
    }

    @Override
    public void onError(Throwable throwable) {
      logger.warn("Encountered EventSource error: {}", LogValues.exceptionSummary(throwable));
      logger.debug(LogValues.exceptionTrace(throwable));
    }  
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

  private final class EventSourceLoggerAdapter implements com.launchdarkly.eventsource.Logger {
    @Override
    public void debug(String format, Object param) {
      logger.debug(format, param);
    }

    @Override
    public void debug(String format, Object param1, Object param2) {
      logger.debug(format, param1, param2);
    }

    @Override
    public void info(String message) {
      logger.info(message);
    }

    @Override
    public void warn(String message) {
      logger.warn(message);
    }

    @Override
    public void error(String message) {
      logger.error(message);
    }
  }
}
