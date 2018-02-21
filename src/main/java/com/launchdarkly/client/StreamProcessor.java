package com.launchdarkly.client;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.launchdarkly.eventsource.ConnectionErrorHandler;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.UnsuccessfulResponseException;

import okhttp3.Headers;

class StreamProcessor implements UpdateProcessor {
  private static final String PUT = "put";
  private static final String PATCH = "patch";
  private static final String DELETE = "delete";
  private static final String INDIRECT_PUT = "indirect/put";
  private static final String INDIRECT_PATCH = "indirect/patch";
  private static final Logger logger = LoggerFactory.getLogger(StreamProcessor.class);
  private static final int DEAD_CONNECTION_INTERVAL_MS = 300 * 1000;

  private final FeatureStore store;
  private final LDConfig config;
  private final String sdkKey;
  private final FeatureRequestor requestor;
  private volatile EventSource es;
  private AtomicBoolean initialized = new AtomicBoolean(false);


  StreamProcessor(String sdkKey, LDConfig config, FeatureRequestor requestor) {
    this.store = config.featureStore;
    this.config = config;
    this.sdkKey = sdkKey;
    this.requestor = requestor;
  }

  @Override
  public Future<Void> start() {
    final SettableFuture<Void> initFuture = SettableFuture.create();

    Headers headers = new Headers.Builder()
        .add("Authorization", this.sdkKey)
        .add("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION)
        .add("Accept", "text/event-stream")
        .build();

    ConnectionErrorHandler connectionErrorHandler = new ConnectionErrorHandler() {
      @Override
      public Action onConnectionError(Throwable t) {
        if ((t instanceof UnsuccessfulResponseException) &&
            ((UnsuccessfulResponseException) t).getCode() == 401) {
          logger.error("Received 401 error, no further streaming connection will be made since SDK key is invalid");
          return Action.SHUTDOWN;
        }
        return Action.PROCEED;
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
      public void onMessage(String name, MessageEvent event) throws Exception {
        Gson gson = new Gson();
        switch (name) {
          case PUT: {
            FeatureRequestor.AllData allData = gson.fromJson(event.getData(), FeatureRequestor.AllData.class); 
            store.init(FeatureRequestor.toVersionedDataMap(allData));
            if (!initialized.getAndSet(true)) {
              initFuture.set(null);
              logger.info("Initialized LaunchDarkly client.");
            }
            break;
          }
          case PATCH: {
            PatchData data = gson.fromJson(event.getData(), PatchData.class);
            if (FEATURES.getKeyFromStreamApiPath(data.path) != null) {
              store.upsert(FEATURES, gson.fromJson(data.data, FeatureFlag.class));
            } else if (SEGMENTS.getKeyFromStreamApiPath(data.path) != null) {
              store.upsert(SEGMENTS, gson.fromJson(data.data, Segment.class));
            }
            break;
          }
          case DELETE: {
            DeleteData data = gson.fromJson(event.getData(), DeleteData.class);
            String featureKey = FEATURES.getKeyFromStreamApiPath(data.path);
            if (featureKey != null) {
              store.delete(FEATURES, featureKey, data.version);
            } else {
              String segmentKey = SEGMENTS.getKeyFromStreamApiPath(data.path);
              if (segmentKey != null) {
                store.delete(SEGMENTS, segmentKey, data.version);
              }
            }
            break;
          }
          case INDIRECT_PUT:
            try {
              FeatureRequestor.AllData allData = requestor.getAllData();
              store.init(FeatureRequestor.toVersionedDataMap(allData));
              if (!initialized.getAndSet(true)) {
                initFuture.set(null);
                logger.info("Initialized LaunchDarkly client.");
              }
            } catch (IOException e) {
              logger.error("Encountered exception in LaunchDarkly client", e);
            }
            break;
          case INDIRECT_PATCH:
            String path = event.getData();
            try {
              String featureKey = FEATURES.getKeyFromStreamApiPath(path);
              if (featureKey != null) {
                FeatureFlag feature = requestor.getFlag(featureKey);
                store.upsert(FEATURES, feature);
              } else {
                String segmentKey = SEGMENTS.getKeyFromStreamApiPath(path);
                if (segmentKey != null) {
                  Segment segment = requestor.getSegment(segmentKey);
                  store.upsert(SEGMENTS, segment);
                }
              }
            } catch (IOException e) {
              logger.error("Encountered exception in LaunchDarkly client", e);
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
        logger.error("Encountered EventSource error: " + throwable.getMessage());
        logger.debug("", throwable);
      }
    };

    EventSource.Builder builder = new EventSource.Builder(handler, URI.create(config.streamURI.toASCIIString() + "/all"))
        .connectionErrorHandler(connectionErrorHandler)
        .headers(headers)
        .reconnectTimeMs(config.reconnectTimeMs)
        .connectTimeoutMs(config.connectTimeoutMillis)
        .readTimeoutMs(DEAD_CONNECTION_INTERVAL_MS);
    // Note that this is not the same read timeout that can be set in LDConfig.  We default to a smaller one
    // there because we don't expect long delays within any *non*-streaming response that the LD client gets.
    // A read timeout on the stream will result in the connection being cycled, so we set this to be slightly
    // more than the expected interval between heartbeat signals.

    if (config.proxy != null) {
      builder.proxy(config.proxy);
      if (config.proxyAuthenticator != null) {
        builder.proxyAuthenticator(config.proxyAuthenticator);
      }
    }

    es = builder.build();
    es.start();
    return initFuture;
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
  }

  @Override
  public boolean initialized() {
    return initialized.get();
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
}