package com.launchdarkly.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.interfaces.Event;
import com.launchdarkly.client.interfaces.EventProcessor;
import com.launchdarkly.client.interfaces.EventProcessorFactory;
import com.launchdarkly.client.interfaces.FeatureStore;
import com.launchdarkly.client.interfaces.FeatureStoreFactory;
import com.launchdarkly.client.interfaces.UpdateProcessor;
import com.launchdarkly.client.interfaces.UpdateProcessorFactory;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;
import com.launchdarkly.client.value.LDValue;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class TestUtil {

  public static FeatureStoreFactory specificFeatureStore(final FeatureStore store) {
    return new FeatureStoreFactory() {
      public FeatureStore createFeatureStore() {
        return store;
      }
    };
  }
  
  public static FeatureStore initedFeatureStore() {
    FeatureStore store = new InMemoryFeatureStore();
    store.init(Collections.<VersionedDataKind<?>, Map<String, ? extends VersionedData>>emptyMap());
    return store;
  }
  
  public static EventProcessorFactory specificEventProcessor(final EventProcessor ep) {
    return new EventProcessorFactory() {
      public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
        return ep;
      }
    };
  }
  
  public static UpdateProcessorFactory specificUpdateProcessor(final UpdateProcessor up) {
    return new UpdateProcessorFactory() {
      public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
        return up;
      }
    };
  }

  public static UpdateProcessorFactory updateProcessorWithData(final Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> data) {
    return new UpdateProcessorFactory() {
      public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, final FeatureStore featureStore) {
        return new UpdateProcessor() {
          public Future<Void> start() {
            featureStore.init(data);
            return Futures.immediateFuture(null);
          }

          public boolean initialized() {
            return true;
          }

          public void close() throws IOException {
          }          
        };
      }
    };
  }
  
  public static FeatureStore featureStoreThatThrowsException(final RuntimeException e) {
    return new FeatureStore() {
      @Override
      public void close() throws IOException { }

      @Override
      public <T extends VersionedData> T get(VersionedDataKind<T> kind, String key) {
        throw e;
      }

      @Override
      public <T extends VersionedData> Map<String, T> all(VersionedDataKind<T> kind) {
        throw e;
      }

      @Override
      public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) { }

      @Override
      public <T extends VersionedData> void delete(VersionedDataKind<T> kind, String key, int version) { }

      @Override
      public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) { }

      @Override
      public boolean initialized() {
        return true;
      }      
    };
  }
  
  public static UpdateProcessor failedUpdateProcessor() {
    return new UpdateProcessor() {
      @Override
      public Future<Void> start() {
        return SettableFuture.create();
      }

      @Override
      public boolean initialized() {
        return false;
      }

      @Override
      public void close() throws IOException {
      }          
    };
  }
    
  public static class TestEventProcessor implements EventProcessor {
    List<Event> events = new ArrayList<>();

    @Override
    public void close() throws IOException {}

    @Override
    public void sendEvent(Event e) {
      events.add(e);
    }

    @Override
    public void flush() {}
  }
  
  public static JsonPrimitive js(String s) {
    return new JsonPrimitive(s);
  }

  public static JsonPrimitive jint(int n) {
    return new JsonPrimitive(n);
  }

  public static JsonPrimitive jdouble(double d) {
    return new JsonPrimitive(d);
  }
  
  public static JsonPrimitive jbool(boolean b) {
    return new JsonPrimitive(b);
  }
  
  public static class DataBuilder {
    private Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> data = new HashMap<>();
    
    @SuppressWarnings("unchecked")
    public DataBuilder add(VersionedDataKind<?> kind, VersionedData... items) {
      Map<String, VersionedData> itemsMap = (Map<String, VersionedData>) data.get(kind);
      if (itemsMap == null) {
        itemsMap = new HashMap<>();
        data.put(kind, itemsMap);
      }
      for (VersionedData item: items) {
        itemsMap.put(item.getKey(), item);
      }
      return this;
    }
    
    public Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> build() {
      return data;
    }
  }
  
  public static Evaluator.EvalResult simpleEvaluation(int variation, LDValue value) {
    return new Evaluator.EvalResult(value, variation, EvaluationReason.fallthrough());
  }
  
  public static Matcher<JsonElement> hasJsonProperty(final String name, JsonElement value) {
    return hasJsonProperty(name, equalTo(value));
  }

  @SuppressWarnings("deprecation")
  public static Matcher<JsonElement> hasJsonProperty(final String name, LDValue value) {
    return hasJsonProperty(name, equalTo(value.asUnsafeJsonElement()));
  }

  public static Matcher<JsonElement> hasJsonProperty(final String name, String value) {
    return hasJsonProperty(name, new JsonPrimitive(value));
  }
    
  public static Matcher<JsonElement> hasJsonProperty(final String name, int value) {
    return hasJsonProperty(name, new JsonPrimitive(value));
  }
    
  public static Matcher<JsonElement> hasJsonProperty(final String name, double value) {
    return hasJsonProperty(name, new JsonPrimitive(value));
  }
    
  public static Matcher<JsonElement> hasJsonProperty(final String name, boolean value) {
    return hasJsonProperty(name, new JsonPrimitive(value));
  }
    
  public static Matcher<JsonElement> hasJsonProperty(final String name, final Matcher<JsonElement> matcher) {
    return new TypeSafeDiagnosingMatcher<JsonElement>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(name + ": ");
        matcher.describeTo(description);
      }
      
      @Override
      protected boolean matchesSafely(JsonElement item, Description mismatchDescription) {
        JsonElement value = item.getAsJsonObject().get(name);
        if (!matcher.matches(value)) {
          matcher.describeMismatch(value, mismatchDescription);
          return false;
        }
        return true;
      }
    };
  }

  public static Matcher<JsonElement> isJsonArray(final Matcher<Iterable<? extends JsonElement>> matcher) {
    return new TypeSafeDiagnosingMatcher<JsonElement>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("array: ");
        matcher.describeTo(description);
      }
      
      @Override
      protected boolean matchesSafely(JsonElement item, Description mismatchDescription) {
        JsonArray value = item.getAsJsonArray();
        if (!matcher.matches(value)) {
          matcher.describeMismatch(value, mismatchDescription);
          return false;
        }
        return true;
      }
    };
  }
}
