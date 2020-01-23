package com.launchdarkly.client;

import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.launchdarkly.client.interfaces.DataSource;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.VersionedDataKind;
import com.launchdarkly.eventsource.ConnectionErrorHandler;
import com.launchdarkly.eventsource.ConnectionErrorHandler.Action;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.UnsuccessfulResponseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.client.DataModel.DataKinds.FEATURES;
import static com.launchdarkly.client.DataModel.DataKinds.SEGMENTS;
import static com.launchdarkly.client.Util.configureHttpClientBuilder;
import static com.launchdarkly.client.Util.getHeadersBuilderFor;
import static com.launchdarkly.client.Util.httpErrorMessage;
import static com.launchdarkly.client.Util.isHttpErrorRecoverable;

import okhttp3.Headers;
import okhttp3.OkHttpClient;

final class StreamProcessor implements DataSource {
  private static final String PUT = "put";
  private static final String PATCH = "patch";
  private static final String DELETE = "delete";
  private static final String INDIRECT_PUT = "indirect/put";
  private static final String INDIRECT_PATCH = "indirect/patch";
  private static final Logger logger = LoggerFactory.getLogger(StreamProcessor.class);
  private static final int DEAD_CONNECTION_INTERVAL_MS = 300 * 1000;

  private final DataStore store;
  private final LDConfig config;
  private final String sdkKey;
  private final FeatureRequestor requestor;
  private final DiagnosticAccumulator diagnosticAccumulator;
  private final EventSourceCreator eventSourceCreator;
  private volatile EventSource es;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private volatile long esStarted = 0;

  ConnectionErrorHandler connectionErrorHandler = createDefaultConnectionErrorHandler(); // exposed for testing
  
  public static interface EventSourceCreator {
    EventSource createEventSource(LDConfig config, EventHandler handler, URI streamUri, ConnectionErrorHandler errorHandler, Headers headers);
  }
  
  StreamProcessor(String sdkKey, LDConfig config, FeatureRequestor requestor, DataStore dataStore,
      EventSourceCreator eventSourceCreator, DiagnosticAccumulator diagnosticAccumulator) {
    this.store = dataStore;
    this.config = config;
    this.sdkKey = sdkKey;
    this.requestor = requestor;
    this.diagnosticAccumulator = diagnosticAccumulator;
    this.eventSourceCreator = eventSourceCreator != null ? eventSourceCreator : new DefaultEventSourceCreator();
  }

  private ConnectionErrorHandler createDefaultConnectionErrorHandler() {
    return (Throwable t) -> {
      recordStreamInit(true);
      if (t instanceof UnsuccessfulResponseException) {
        int status = ((UnsuccessfulResponseException)t).getCode();
        logger.error(httpErrorMessage(status, "streaming connection", "will retry"));
        if (!isHttpErrorRecoverable(status)) {
          return Action.SHUTDOWN;
        }
        esStarted = System.currentTimeMillis();
        return Action.PROCEED;
      }
      return Action.PROCEED;
    };
  }
  
  @Override
  public Future<Void> start() {
    final SettableFuture<Void> initFuture = SettableFuture.create();

    Headers headers = getHeadersBuilderFor(sdkKey, config)
        .add("Accept", "text/event-stream")
        .build();

    ConnectionErrorHandler wrappedConnectionErrorHandler = (Throwable t) -> {
      Action result = connectionErrorHandler.onConnectionError(t);
      if (result == Action.SHUTDOWN) {
        initFuture.set(null); // if client is initializing, make it stop waiting; has no effect if already inited
      }
      return result;
    };

    EventHandler handler = new EventHandler() {

      @Override
      public void onOpen() throws Exception {
      }

      @Override
      public void onClosed() throws Exception {
      }

      @Override
      public void onMessage(String name, MessageEvent event) throws Exception {
        Gson gson = new Gson();
        switch (name) {
          case PUT: {
            recordStreamInit(false);
            esStarted = 0;
            PutData putData = gson.fromJson(event.getData(), PutData.class); 
            store.init(DefaultFeatureRequestor.toVersionedDataMap(putData.data));
            if (!initialized.getAndSet(true)) {
              initFuture.set(null);
              logger.info("Initialized LaunchDarkly client.");
            }
            break;
          }
          case PATCH: {
            PatchData data = gson.fromJson(event.getData(), PatchData.class);
            if (getKeyFromStreamApiPath(FEATURES, data.path) != null) {
              store.upsert(FEATURES, gson.fromJson(data.data, DataModel.FeatureFlag.class));
            } else if (getKeyFromStreamApiPath(SEGMENTS, data.path) != null) {
              store.upsert(SEGMENTS, gson.fromJson(data.data, DataModel.Segment.class));
            }
            break;
          }
          case DELETE: {
            DeleteData data = gson.fromJson(event.getData(), DeleteData.class);
            String featureKey = getKeyFromStreamApiPath(FEATURES, data.path);
            if (featureKey != null) {
              store.delete(FEATURES, featureKey, data.version);
            } else {
              String segmentKey = getKeyFromStreamApiPath(SEGMENTS, data.path);
              if (segmentKey != null) {
                store.delete(SEGMENTS, segmentKey, data.version);
              }
            }
            break;
          }
          case INDIRECT_PUT:
            try {
              FeatureRequestor.AllData allData = requestor.getAllData();
              store.init(DefaultFeatureRequestor.toVersionedDataMap(allData));
              if (!initialized.getAndSet(true)) {
                initFuture.set(null);
                logger.info("Initialized LaunchDarkly client.");
              }
            } catch (IOException e) {
              logger.error("Encountered exception in LaunchDarkly client: {}", e.toString());
              logger.debug(e.toString(), e);
            }
            break;
          case INDIRECT_PATCH:
            String path = event.getData();
            try {
              String featureKey = getKeyFromStreamApiPath(FEATURES, path);
              if (featureKey != null) {
                DataModel.FeatureFlag feature = requestor.getFlag(featureKey);
                store.upsert(FEATURES, feature);
              } else {
                String segmentKey = getKeyFromStreamApiPath(SEGMENTS, path);
                if (segmentKey != null) {
                  DataModel.Segment segment = requestor.getSegment(segmentKey);
                  store.upsert(SEGMENTS, segment);
                }
              }
            } catch (IOException e) {
              logger.error("Encountered exception in LaunchDarkly client: {}", e.toString());
              logger.debug(e.toString(), e);
            }
            break;
          default:
            logger.warn("Unexpected event found in stream: " + event.getData());
            break;
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
    };

    es = eventSourceCreator.createEventSource(config, handler,
        URI.create(config.streamURI.toASCIIString() + "/all"),
        wrappedConnectionErrorHandler,
        headers);
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
    if (es != null) {
      es.close();
    }
    if (store != null) {
      store.close();
    }
    requestor.close();
  }

  @Override
  public boolean initialized() {
    return initialized.get();
  }

  private static String getKeyFromStreamApiPath(VersionedDataKind<?> kind, String path) {
    return path.startsWith(kind.getStreamApiPath()) ? path.substring(kind.getStreamApiPath().length()) : null;
  }
  
  private static final class PutData {
    FeatureRequestor.AllData data;
    
    public PutData() {
      
    }
  }
  
  private static final class PatchData {
    String path;
    JsonElement data;

    public PatchData() {

    }
  }

  private static final class DeleteData {
    String path;
    int version;

    public DeleteData() {

    }
  }
  
  private class DefaultEventSourceCreator implements EventSourceCreator {
    public EventSource createEventSource(final LDConfig config, EventHandler handler, URI streamUri, ConnectionErrorHandler errorHandler, Headers headers) {
      EventSource.Builder builder = new EventSource.Builder(handler, streamUri)
          .clientBuilderActions(new EventSource.Builder.ClientConfigurer() {
            public void configure(OkHttpClient.Builder builder) {
              configureHttpClientBuilder(config, builder);
            }
          })
          .connectionErrorHandler(errorHandler)
          .headers(headers)
          .reconnectTimeMs(config.reconnectTime.toMillis())
          .readTimeoutMs(DEAD_CONNECTION_INTERVAL_MS)
          .connectTimeoutMs(EventSource.DEFAULT_CONNECT_TIMEOUT_MS)
          .writeTimeoutMs(EventSource.DEFAULT_WRITE_TIMEOUT_MS);
      // Note that this is not the same read timeout that can be set in LDConfig.  We default to a smaller one
      // there because we don't expect long delays within any *non*-streaming response that the LD client gets.
      // A read timeout on the stream will result in the connection being cycled, so we set this to be slightly
      // more than the expected interval between heartbeat signals.

      return builder.build();
    }
  }
}
