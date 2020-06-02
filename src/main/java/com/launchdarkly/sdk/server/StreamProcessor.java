package com.launchdarkly.sdk.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.launchdarkly.eventsource.ConnectionErrorHandler;
import com.launchdarkly.eventsource.ConnectionErrorHandler.Action;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.UnsuccessfulResponseException;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;
import com.launchdarkly.sdk.server.interfaces.SerializationException;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.sdk.server.DataModel.ALL_DATA_KINDS;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.Util.checkIfErrorIsRecoverableAndLog;
import static com.launchdarkly.sdk.server.Util.configureHttpClientBuilder;
import static com.launchdarkly.sdk.server.Util.getHeadersBuilderFor;
import static com.launchdarkly.sdk.server.Util.httpErrorDescription;

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
  private static final String INDIRECT_PUT = "indirect/put";
  private static final String INDIRECT_PATCH = "indirect/patch";
  private static final Logger logger = Loggers.DATA_SOURCE;
  private static final Duration DEAD_CONNECTION_INTERVAL = Duration.ofSeconds(300);
  private static final String ERROR_CONTEXT_MESSAGE = "in stream connection";
  private static final String WILL_RETRY_MESSAGE = "will retry";

  private final DataSourceUpdates dataSourceUpdates;
  private final HttpConfiguration httpConfig;
  private final Headers headers;
  @VisibleForTesting final URI streamUri;
  @VisibleForTesting final Duration initialReconnectDelay;
  @VisibleForTesting final FeatureRequestor requestor;
  private final DiagnosticAccumulator diagnosticAccumulator;
  private final EventSourceCreator eventSourceCreator;
  private final int threadPriority;
  private final DataStoreStatusProvider.StatusListener statusListener;
  private volatile EventSource es;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private volatile long esStarted = 0;
  private volatile boolean lastStoreUpdateFailed = false;

  ConnectionErrorHandler connectionErrorHandler = createDefaultConnectionErrorHandler(); // exposed for testing
  
  static final class EventSourceParams {
    final EventHandler handler;
    final URI streamUri;
    final Duration initialReconnectDelay;
    final ConnectionErrorHandler errorHandler;
    final Headers headers;
    final HttpConfiguration httpConfig;
    
    EventSourceParams(EventHandler handler, URI streamUri, Duration initialReconnectDelay,
        ConnectionErrorHandler errorHandler, Headers headers, HttpConfiguration httpConfig) {
      this.handler = handler;
      this.streamUri = streamUri;
      this.initialReconnectDelay = initialReconnectDelay;
      this.errorHandler = errorHandler;
      this.headers = headers;
      this.httpConfig = httpConfig;
    }
  }
  
  @FunctionalInterface
  static interface EventSourceCreator {
    EventSource createEventSource(EventSourceParams params);
  }
  
  StreamProcessor(
      HttpConfiguration httpConfig,
      FeatureRequestor requestor,
      DataSourceUpdates dataSourceUpdates,
      EventSourceCreator eventSourceCreator,
      int threadPriority,
      DiagnosticAccumulator diagnosticAccumulator,
      URI streamUri,
      Duration initialReconnectDelay
      ) {
    this.dataSourceUpdates = dataSourceUpdates;
    this.httpConfig = httpConfig;
    this.requestor = requestor;
    this.diagnosticAccumulator = diagnosticAccumulator;
    this.eventSourceCreator = eventSourceCreator != null ? eventSourceCreator : this::defaultEventSourceCreator;
    this.threadPriority = threadPriority;
    this.streamUri = streamUri;
    this.initialReconnectDelay = initialReconnectDelay;

    this.headers = getHeadersBuilderFor(httpConfig)
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
    
    es = eventSourceCreator.createEventSource(new EventSourceParams(handler,
        URI.create(streamUri.toASCIIString() + "/all"),
        initialReconnectDelay,
        wrappedConnectionErrorHandler,
        headers,
        httpConfig));
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
    requestor.close();
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
    public void onMessage(String name, MessageEvent event) throws Exception {
      try {
        switch (name) {
          case PUT:
            handlePut(event.getData());
            break;
         
          case PATCH:
            handlePatch(event.getData());
            break;
            
          case DELETE:
            handleDelete(event.getData()); 
            break;

          case INDIRECT_PUT:
            handleIndirectPut();
            break;
            
          case INDIRECT_PATCH:
            handleIndirectPatch(event.getData());
            break;
            
          default:
            logger.warn("Unexpected event found in stream: " + name);
            break;
        }
        lastStoreUpdateFailed = false;
        dataSourceUpdates.updateStatus(State.VALID, null);
      } catch (StreamInputException e) {
        logger.error("LaunchDarkly service request failed or received invalid data: {}", e.toString());
        logger.debug(e.toString(), e);
        
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
        logger.warn("Unexpected error from stream processor: {}", e.toString());
        logger.debug(e.toString(), e);
      }
    }

    private void handlePut(String eventData) throws StreamInputException, StreamStoreException {
      recordStreamInit(false);
      esStarted = 0;
      PutData putData = parseStreamJson(PutData.class, eventData);
      FullDataSet<ItemDescriptor> allData = putData.data.toFullDataSet();
      if (!dataSourceUpdates.init(allData)) {
        throw new StreamStoreException();
      }
      if (!initialized.getAndSet(true)) {
        initFuture.complete(null);
        logger.info("Initialized LaunchDarkly client.");
      }
    }

    private void handlePatch(String eventData) throws StreamInputException, StreamStoreException {
      PatchData data = parseStreamJson(PatchData.class, eventData);
      Map.Entry<DataKind, String> kindAndKey = getKindAndKeyFromStreamApiPath(data.path);
      if (kindAndKey == null) {
        return;
      }
      DataKind kind = kindAndKey.getKey();
      String key = kindAndKey.getValue();
      VersionedData item = deserializeFromParsedJson(kind, data.data);
      if (!dataSourceUpdates.upsert(kind, key, new ItemDescriptor(item.getVersion(), item))) {
        throw new StreamStoreException();
      }
    }

    private void handleDelete(String eventData) throws StreamInputException, StreamStoreException {
      DeleteData data = parseStreamJson(DeleteData.class, eventData);
      Map.Entry<DataKind, String> kindAndKey = getKindAndKeyFromStreamApiPath(data.path);
      if (kindAndKey == null) {
        return;
      }
      DataKind kind = kindAndKey.getKey();
      String key = kindAndKey.getValue();
      ItemDescriptor placeholder = new ItemDescriptor(data.version, null);
      if (!dataSourceUpdates.upsert(kind, key, placeholder)) {
        throw new StreamStoreException();
      }
    }

    private void handleIndirectPut() throws StreamInputException, StreamStoreException {
      FeatureRequestor.AllData putData;
      try {
        putData = requestor.getAllData();
      } catch (Exception e) {
        throw new StreamInputException(e);
      }
      FullDataSet<ItemDescriptor> allData = putData.toFullDataSet();
      if (!dataSourceUpdates.init(allData)) {
        throw new StreamStoreException();
      }
      if (!initialized.getAndSet(true)) {
        initFuture.complete(null);
        logger.info("Initialized LaunchDarkly client.");
      }
    }

    private void handleIndirectPatch(String path) throws StreamInputException, StreamStoreException {
      Map.Entry<DataKind, String> kindAndKey = getKindAndKeyFromStreamApiPath(path);
      DataKind kind = kindAndKey.getKey();
      String key = kindAndKey.getValue();
      VersionedData item;
      try {
        item = kind == SEGMENTS ? requestor.getSegment(key) : requestor.getFlag(key);
      } catch (Exception e) {
        throw new StreamInputException(e);
        // In this case, StreamInputException doesn't necessarily represent malformed data from the service - it
        // could be that the request to the polling endpoint failed in some other way. But either way, we must
        // assume that we did not get valid data from LD so we have missed an update.
      }
      if (!dataSourceUpdates.upsert(kind, key, new ItemDescriptor(item.getVersion(), item))) {
        throw new StreamStoreException();
      }
    }

    @Override
    public void onComment(String comment) {
      logger.debug("Received a heartbeat");
    }

    @Override
    public void onError(Throwable throwable) {
      logger.warn("Encountered EventSource error: {}", throwable.toString());
      logger.debug(throwable.toString(), throwable);
    }  
  }

  private EventSource defaultEventSourceCreator(EventSourceParams params) {
    EventSource.Builder builder = new EventSource.Builder(params.handler, params.streamUri)
        .threadPriority(threadPriority)
        .loggerBaseName(Loggers.DATA_SOURCE_LOGGER_NAME)
        .clientBuilderActions(new EventSource.Builder.ClientConfigurer() {
          public void configure(OkHttpClient.Builder builder) {
            configureHttpClientBuilder(params.httpConfig, builder);
          }
        })
        .connectionErrorHandler(params.errorHandler)
        .headers(params.headers)
        .reconnectTime(params.initialReconnectDelay)
        .readTimeout(DEAD_CONNECTION_INTERVAL);
    // Note that this is not the same read timeout that can be set in LDConfig.  We default to a smaller one
    // there because we don't expect long delays within any *non*-streaming response that the LD client gets.
    // A read timeout on the stream will result in the connection being cycled, so we set this to be slightly
    // more than the expected interval between heartbeat signals.

    return builder.build();
  }

  private static Map.Entry<DataKind, String> getKindAndKeyFromStreamApiPath(String path) throws StreamInputException {
    if (path == null) {
      throw new StreamInputException("missing item path");
    }
    for (DataKind kind: ALL_DATA_KINDS) {
      String prefix = (kind == SEGMENTS) ? "/segments/" : "/flags/";
      if (path.startsWith(prefix)) {
        return new AbstractMap.SimpleEntry<DataKind, String>(kind, path.substring(prefix.length()));
      }
    }
    return null; // we don't recognize the path - the caller should ignore this event, just as we ignore unknown event types
  }

  private static <T> T parseStreamJson(Class<T> c, String json) throws StreamInputException {
    try {
      return JsonHelpers.deserialize(json, c);
    } catch (SerializationException e) {
      throw new StreamInputException(e);
    }
  }

  private static VersionedData deserializeFromParsedJson(DataKind kind, JsonElement parsedJson)
      throws StreamInputException {
    try {
      return JsonHelpers.deserializeFromParsedJson(kind, parsedJson);
    } catch (SerializationException e) {
      throw new StreamInputException(e);
    }
  }

  // StreamInputException is either a JSON parsing error *or* a failure to query another endpoint
  // (for indirect/put or indirect/patch); either way, it implies that we were unable to get valid data from LD services.
  @SuppressWarnings("serial")
  private static final class StreamInputException extends Exception {
    public StreamInputException(String message) {
      super(message);
    }
    
    public StreamInputException(Throwable cause) {
      super(cause);
    }
  }
  
  // This exception class indicates that the data store failed to persist an update.
  @SuppressWarnings("serial")
  private static final class StreamStoreException extends Exception {}

  private static final class PutData {
    FeatureRequestor.AllData data;
    
    @SuppressWarnings("unused") // used by Gson
    public PutData() { }
  }
  
  private static final class PatchData {
    String path;
    JsonElement data;

    @SuppressWarnings("unused") // used by Gson
    public PatchData() { }
  }

  private static final class DeleteData {
    String path;
    int version;

    @SuppressWarnings("unused") // used by Gson
    public DeleteData() { }
  }
}
