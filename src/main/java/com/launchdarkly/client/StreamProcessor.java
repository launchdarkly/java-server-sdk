package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import okhttp3.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

class StreamProcessor implements UpdateProcessor {
  private static final String PUT = "put";
  private static final String PATCH = "patch";
  private static final String DELETE = "delete";
  private static final String INDIRECT_PUT = "indirect/put";
  private static final String INDIRECT_PATCH = "indirect/patch";
  private static final Logger logger = LoggerFactory.getLogger(StreamProcessor.class);

  private final FeatureStore store;
  private final LDConfig config;
  private final String apiKey;
  private final FeatureRequestor requestor;
  private EventSource es;
  private AtomicBoolean initialized = new AtomicBoolean(false);


  StreamProcessor(String apiKey, LDConfig config, FeatureRequestor requestor) {
    this.store = config.featureStore;
    this.config = config;
    this.apiKey = apiKey;
    this.requestor = requestor;
  }

  @Override
  public Future<Void> start() {
    final VeryBasicFuture initFuture = new VeryBasicFuture();

    Headers headers = new Headers.Builder()
        .add("Authorization", "api_key " + this.apiKey)
        .add("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION)
        .add("Accept", "text/event-stream")
        .build();

    EventHandler handler = new EventHandler() {

      @Override
      public void onOpen() throws Exception {

      }

      @Override
      public void onMessage(String name, MessageEvent event) throws Exception {
        Gson gson = new Gson();
        if (name.equals(PUT)) {
          Type type = new TypeToken<Map<String,FeatureRep<?>>>(){}.getType();
          Map<String, FeatureRep<?>> features = gson.fromJson(event.getData(), type);
          store.init(features);
          if (!initialized.getAndSet(true)) {
            initFuture.completed(null);
            logger.info("Initialized LaunchDarkly client.");
          }
        }
        else if (name.equals(PATCH)) {
          FeaturePatchData data = gson.fromJson(event.getData(), FeaturePatchData.class);
          store.upsert(data.key(), data.feature());
        }
        else if (name.equals(DELETE)) {
          FeatureDeleteData data = gson.fromJson(event.getData(), FeatureDeleteData.class);
          store.delete(data.key(), data.version());
        }
        else if (name.equals(INDIRECT_PUT)) {
          try {
            Map<String, FeatureRep<?>> features = requestor.makeAllRequest(true);
            store.init(features);
            if (!initialized.getAndSet(true)) {
              initFuture.completed(null);
              logger.info("Initialized LaunchDarkly client.");
            }
          } catch (IOException e) {
            logger.error("Encountered exception in LaunchDarkly client", e);
          }
        }
        else if (name.equals(INDIRECT_PATCH)) {
          String key = event.getData();
          try {
            FeatureRep<?> feature = requestor.makeRequest(key, true);
            store.upsert(key, feature);
          } catch (IOException e) {
            logger.error("Encountered exception in LaunchDarkly client", e);
          }
        }
        else {
          logger.warn("Unexpected event found in stream: " + event.getData());
        }
      }

      @Override
      public void onError(Throwable throwable) {
        logger.error("Encountered exception in LaunchDarkly client: " + throwable.getMessage());
      }
    };


    es = new EventSource.Builder(handler, URI.create(config.streamURI.toASCIIString() + "/features"))
        .headers(headers)
        .build();

    es.start();
    return initFuture;
  }

  @Override
  public void close() throws IOException {
    if (es != null) {
      es.stop();
    }
    if (store != null) {
      store.close();
    }
  }

  @Override
  public boolean initialized() {
    return initialized.get();
  }

  FeatureRep<?> getFeature(String key) {
    return store.get(key);
  }

  private static final class FeaturePatchData {
    String path;
    FeatureRep<?> data;

    public FeaturePatchData() {

    }

    String key() {
      return path.substring(1);
    }

    FeatureRep<?> feature() {
      return data;
    }

  }

  private static final class FeatureDeleteData {
    String path;
    int version;

    public FeatureDeleteData() {

    }

    String key() {
      return path.substring(1);
    }

    int version() {
      return version;
    }

  }
}
