package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.glassfish.jersey.internal.util.collection.StringKeyIgnoreCaseMultivaluedMap;
import org.glassfish.jersey.media.sse.InboundEvent;
import org.glassfish.jersey.media.sse.SseFeature;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedMap;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

class StreamProcessor implements Closeable {
  private static final String PUT = "put";
  private static final String PATCH = "patch";
  private static final String DELETE = "delete";

  private final Client client;
  private final FeatureStore store;
  private final LDConfig config;
  private final String apiKey;
  private EventSource es;

  StreamProcessor(String apiKey, LDConfig config) {
    this.client = ClientBuilder.newBuilder().register(SseFeature.class).build();
    this.store = new InMemoryFeatureStore();
    this.config = config;
    this.apiKey = apiKey;
  }

  void subscribe() {
    MultivaluedMap<String, Object> headers = new StringKeyIgnoreCaseMultivaluedMap<Object>();
    headers.putSingle("Authorization", "api_key " + this.apiKey);
    headers.putSingle("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);
    headers.putSingle("Accept", SseFeature.SERVER_SENT_EVENTS_TYPE);

    WebTarget target = client.target(config.streamURI.toASCIIString() + "/features");

    es = new EventSource(target, true, headers) {
      @Override
      public void onEvent(InboundEvent event) {
        Gson gson = new Gson();
        if (event.getName().equals(PUT)) {
          Type type = new TypeToken<Map<String,FeatureRep<?>>>(){}.getType();
          Map<String, FeatureRep<?>> features = gson.fromJson(event.readData(), type);
          store.init(features);
        }
        else if (event.getName().equals(PATCH)) {
          FeaturePatchData data = gson.fromJson(event.readData(), FeaturePatchData.class);
          store.upsert(data.key(), data.feature());
        }
        else if (event.getName().equals(DELETE)) {
          FeatureDeleteData data = gson.fromJson(event.readData(), FeatureDeleteData.class);
          store.delete(data.key(), data.version());
        }
        else {
          // TODO log an error
        }
      }
    };

  }

  @Override
  public void close() throws IOException {
    if (es != null) {
      es.close();
    }
  }

  boolean initialized() {
    return store.initialized();
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
