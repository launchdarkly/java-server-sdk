package com.launchdarkly.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.JsonElement;
import com.launchdarkly.client.interfaces.HttpConfiguration;
import com.launchdarkly.client.interfaces.SerializationException;
import com.launchdarkly.eventsource.ConnectionErrorHandler;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.UnsuccessfulResponseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.launchdarkly.client.Util.configureHttpClientBuilder;
import static com.launchdarkly.client.Util.getHeadersBuilderFor;
import static com.launchdarkly.client.Util.httpErrorMessage;
import static com.launchdarkly.client.Util.isHttpErrorRecoverable;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;

import okhttp3.Headers;
import okhttp3.OkHttpClient;

/**
 * Implementation of the streaming data source, not including the lower-level SSE implementation which is in
 * okhttp-eventsource.
 * 
 * Error handling works as follows:
 * 1. If any event is malformed, we must assume the stream is broken and we may have missed updates. Restart it.
 * 2. If we try to put updates into the data store and we get an error, we must assume something's wrong with the
 * data store. We must assume that updates have been lost, so we'll restart the stream. (Starting in version 5.0,
 * we will be able to do this in a smarter way and not restart the stream until the store is actually working
 * again, but in 4.x we don't have the monitoring mechanism for this.)
 * 3. If we receive an unrecoverable error like HTTP 401, we close the stream and don't retry. Any other HTTP
 * error or network error causes a retry with backoff.
 * 4. We set the Future returned by start() to tell the client initialization logic that initialization has either
 * succeeded (we got an initial payload and successfully stored it) or permanently failed (we got a 401, etc.).
 * Otherwise, the client initialization method may time out but we will still be retrying in the background, and
 * if we succeed then the client can detect that we're initialized now by calling our Initialized method.
 */
final class StreamProcessor implements UpdateProcessor {
  private static final String PUT = "put";
  private static final String PATCH = "patch";
  private static final String DELETE = "delete";
  private static final String INDIRECT_PUT = "indirect/put";
  private static final String INDIRECT_PATCH = "indirect/patch";
  private static final Logger logger = LoggerFactory.getLogger(StreamProcessor.class);
  private static final int DEAD_CONNECTION_INTERVAL_MS = 300 * 1000;

  private final FeatureStore store;
  private final HttpConfiguration httpConfig;
  private final Headers headers;
  @VisibleForTesting final URI streamUri;
  @VisibleForTesting final long initialReconnectDelayMillis;
  @VisibleForTesting final FeatureRequestor requestor;
  private final DiagnosticAccumulator diagnosticAccumulator;
  private final EventSourceCreator eventSourceCreator;
  private volatile EventSource es;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private volatile long esStarted = 0;
  private volatile boolean lastStoreUpdateFailed = false;

  ConnectionErrorHandler connectionErrorHandler = createDefaultConnectionErrorHandler(); // exposed for testing
  
  public static interface EventSourceCreator {
    EventSource createEventSource(EventHandler handler, URI streamUri, long initialReconnectDelayMillis,
        ConnectionErrorHandler errorHandler, Headers headers, HttpConfiguration httpConfig);
  }
  
  StreamProcessor(
      String sdkKey,
      HttpConfiguration httpConfig,
      FeatureRequestor requestor,
      FeatureStore featureStore,
      EventSourceCreator eventSourceCreator,
      DiagnosticAccumulator diagnosticAccumulator,
      URI streamUri,
      long initialReconnectDelayMillis
      ) {
    this.store = featureStore;
    this.httpConfig = httpConfig;
    this.requestor = requestor;
    this.diagnosticAccumulator = diagnosticAccumulator;
    this.eventSourceCreator = eventSourceCreator != null ? eventSourceCreator : new DefaultEventSourceCreator();
    this.streamUri = streamUri;
    this.initialReconnectDelayMillis = initialReconnectDelayMillis;

    this.headers = getHeadersBuilderFor(sdkKey, httpConfig)
        .add("Accept", "text/event-stream")
        .build();
  }

  private ConnectionErrorHandler createDefaultConnectionErrorHandler() {
    return new ConnectionErrorHandler() {
      @Override
      public Action onConnectionError(Throwable t) {
        recordStreamInit(true);
        if (t instanceof UnsuccessfulResponseException) {
          int status = ((UnsuccessfulResponseException)t).getCode();
          logger.error(httpErrorMessage(status, "streaming connection", "will retry"));
          if (!isHttpErrorRecoverable(status)) {
            return Action.SHUTDOWN;
          }
        }
        esStarted = System.currentTimeMillis();
        return Action.PROCEED;
      }
    };
  }
  
  @Override
  public Future<Void> start() {
    final SettableFuture<Void> initFuture = SettableFuture.create();

    ConnectionErrorHandler wrappedConnectionErrorHandler = new ConnectionErrorHandler() {
      @Override
      public Action onConnectionError(Throwable t) {
        Action result = connectionErrorHandler.onConnectionError(t);
        if (result == Action.SHUTDOWN) {
          initFuture.set(null); // if client is initializing, make it stop waiting; has no effect if already inited
        }
        return result;
      }
    };

    EventHandler handler = new EventHandler() {

      @Override
      public void onOpen() throws Exception {
      }

      @Override
      public void onClosed() throws Exception {
      }

      @Override
      public void onMessage(String name, MessageEvent event) {
        try {
          switch (name) {
            case PUT: {
              recordStreamInit(false);
              esStarted = 0;
              PutData putData = parseStreamJson(PutData.class, event.getData());
              try {
                store.init(DefaultFeatureRequestor.toVersionedDataMap(putData.data));
              } catch (Exception e) {
                throw new StreamStoreException(e);
              }
              if (!initialized.getAndSet(true)) {
                initFuture.set(null);
                logger.info("Initialized LaunchDarkly client.");
              }
              break;
            }
            case PATCH: {
              PatchData data = parseStreamJson(PatchData.class, event.getData());
              Map.Entry<VersionedDataKind<VersionedData>, String> kindAndKey = getKindAndKeyFromStreamApiPath(data.path);
              if (kindAndKey == null) {
                break;
              }
              VersionedDataKind<VersionedData> kind = kindAndKey.getKey();
              VersionedData item = deserializeFromParsedJson(kind, data.data);
              try {
                store.upsert(kind, item);
              } catch (Exception e) {
                throw new StreamStoreException(e);
              }
              break;
            }
            case DELETE: {
              DeleteData data = parseStreamJson(DeleteData.class, event.getData());
              Map.Entry<VersionedDataKind<VersionedData>, String> kindAndKey = getKindAndKeyFromStreamApiPath(data.path);
              if (kindAndKey == null) {
                break;
              }
              VersionedDataKind<VersionedData> kind = kindAndKey.getKey();
              String key = kindAndKey.getValue();
              try {
                store.delete(kind, key, data.version);
              } catch (Exception e) {
                throw new StreamStoreException(e);
              }
              break;
            }
            case INDIRECT_PUT:
              FeatureRequestor.AllData allData;
              try {
                allData = requestor.getAllData();
              } catch (HttpErrorException e) {
                throw new StreamInputException(e);
              } catch (IOException e) {
                throw new StreamInputException(e);
              }
              try {
                store.init(DefaultFeatureRequestor.toVersionedDataMap(allData));
              } catch (Exception e) {
                throw new StreamStoreException(e);
              }
              if (!initialized.getAndSet(true)) {
                initFuture.set(null);
                logger.info("Initialized LaunchDarkly client.");
              }
              break;
            case INDIRECT_PATCH:
              String path = event.getData();
              Map.Entry<VersionedDataKind<VersionedData>, String> kindAndKey = getKindAndKeyFromStreamApiPath(path);
              if (kindAndKey == null) {
                break;
              }
              VersionedDataKind<VersionedData> kind = kindAndKey.getKey();
              String key = kindAndKey.getValue();
              VersionedData item;
              try {
                item = (Object)kind == SEGMENTS ? requestor.getSegment(key) : requestor.getFlag(key);
              } catch (Exception e) {
                throw new StreamInputException(e);
              }
              try {
                store.upsert(kind, item); // silly cast due to our use of generics
              } catch (Exception e) {
                throw new StreamStoreException(e);
              }
              break;
            default:
              logger.warn("Unexpected event found in stream: " + event.getData());
              break;
          }
        } catch (StreamInputException e) {
          logger.error("LaunchDarkly service request failed or received invalid data: {}", e.toString());
          logger.debug(e.toString(), e);
          es.restart();
        } catch (StreamStoreException e) {
          if (!lastStoreUpdateFailed) {
            logger.error("Unexpected data store failure when storing updates from stream: {}",
                e.getCause().toString());
            logger.debug(e.getCause().toString(), e.getCause());
            lastStoreUpdateFailed = true;
          }
          es.restart();
        } catch (Exception e) {
          logger.error("Unexpected exception in stream processor: {}", e.toString());
          logger.debug(e.toString(), e);
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

    es = eventSourceCreator.createEventSource(handler,
        URI.create(streamUri.toASCIIString() + "/all"),
        initialReconnectDelayMillis,
        wrappedConnectionErrorHandler,
        headers,
        httpConfig);
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
  
  @SuppressWarnings("unchecked")
  private static Map.Entry<VersionedDataKind<VersionedData>, String> getKindAndKeyFromStreamApiPath(String path)
      throws StreamInputException {
    if (path == null) {
      throw new StreamInputException("missing item path");
    }
    for (VersionedDataKind<?> kind: VersionedDataKind.ALL) {
      String prefix = (kind == SEGMENTS) ? "/segments/" : "/flags/";
      if (path.startsWith(prefix)) {
        return new AbstractMap.SimpleEntry<VersionedDataKind<VersionedData>, String>(
            (VersionedDataKind<VersionedData>)kind, // cast is required due to our cumbersome use of generics
            path.substring(prefix.length()));
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

  private static VersionedData deserializeFromParsedJson(VersionedDataKind<?> kind, JsonElement parsedJson)
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
  private static final class StreamStoreException extends Exception {
    public StreamStoreException(Throwable cause) {
      super(cause);
    }
  }

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
  
  private class DefaultEventSourceCreator implements EventSourceCreator {
    public EventSource createEventSource(EventHandler handler, URI streamUri, long initialReconnectDelayMillis,
        ConnectionErrorHandler errorHandler, Headers headers, final HttpConfiguration httpConfig) {
      EventSource.Builder builder = new EventSource.Builder(handler, streamUri)
          .clientBuilderActions(new EventSource.Builder.ClientConfigurer() {
            public void configure(OkHttpClient.Builder builder) {
              configureHttpClientBuilder(httpConfig, builder);
            }
          })
          .connectionErrorHandler(errorHandler)
          .headers(headers)
          .reconnectTimeMs(initialReconnectDelayMillis)
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
