package com.launchdarkly.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.launchdarkly.client.integrations.EventProcessorBuilder;
import com.launchdarkly.client.interfaces.ClientContext;
import com.launchdarkly.client.interfaces.DataSource;
import com.launchdarkly.client.interfaces.DataSourceFactory;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.DataStoreFactory;
import com.launchdarkly.client.interfaces.DataStoreUpdates;
import com.launchdarkly.client.interfaces.Event;
import com.launchdarkly.client.interfaces.EventProcessor;
import com.launchdarkly.client.interfaces.EventProcessorFactory;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;
import com.launchdarkly.client.value.LDValue;
import com.launchdarkly.client.value.LDValueType;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class TestUtil {
  public static ClientContext clientContext(final String sdkKey, final LDConfig config) {
    return new ClientContextImpl(sdkKey, config, null);
  }

  public static ClientContext clientContext(final String sdkKey, final LDConfig config, DiagnosticAccumulator diagnosticAccumulator) {
    return new ClientContextImpl(sdkKey, config, diagnosticAccumulator);
  }

  public static DataStoreUpdates dataStoreUpdates(final DataStore store) {
    return new DataStoreUpdatesImpl(store);
  }
  
  public static DataStoreFactory specificDataStore(final DataStore store) {
    return context -> store;
  }
  
  public static DataStore initedDataStore() {
    DataStore store = new InMemoryDataStore();
    store.init(Collections.<VersionedDataKind<?>, Map<String, ? extends VersionedData>>emptyMap());
    return store;
  }
  
  public static EventProcessorFactory specificEventProcessor(final EventProcessor ep) {
    return context -> ep;
  }
  
  public static DataSourceFactory specificDataSource(final DataSource up) {
    return (context, dataStoreUpdates) -> up;
  }

  public static DataSourceFactory dataSourceWithData(final Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> data) {
    return (ClientContext context, final DataStoreUpdates dataStoreUpdates) -> {
      return new DataSource() {
        public Future<Void> start() {
          dataStoreUpdates.init(data);
          return Futures.immediateFuture(null);
        }

        public boolean isInitialized() {
          return true;
        }

        public void close() throws IOException {
        }          
      };
    };
  }
  
  public static DataStore dataStoreThatThrowsException(final RuntimeException e) {
    return new DataStore() {
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
  
  public static DataSource failedDataSource() {
    return new DataSource() {
      @Override
      public Future<Void> start() {
        return SettableFuture.create();
      }

      @Override
      public boolean isInitialized() {
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
    
    // Silly casting helper due to difference in generic signatures between FeatureStore and FeatureStoreCore
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<VersionedDataKind<?>, Map<String, VersionedData>> buildUnchecked() {
      Map uncheckedMap = data;
      return (Map<VersionedDataKind<?>, Map<String, VersionedData>>)uncheckedMap;
    }
  }
  
  public static Evaluator.EvalResult simpleEvaluation(int variation, LDValue value) {
    return new Evaluator.EvalResult(value, variation, EvaluationReason.fallthrough());
  }
  
  public static Matcher<LDValue> hasJsonProperty(final String name, LDValue value) {
    return hasJsonProperty(name, equalTo(value));
  }

  public static Matcher<LDValue> hasJsonProperty(final String name, String value) {
    return hasJsonProperty(name, LDValue.of(value));
  }
    
  public static Matcher<LDValue> hasJsonProperty(final String name, int value) {
    return hasJsonProperty(name, LDValue.of(value));
  }
    
  public static Matcher<LDValue> hasJsonProperty(final String name, double value) {
    return hasJsonProperty(name, LDValue.of(value));
  }
    
  public static Matcher<LDValue> hasJsonProperty(final String name, boolean value) {
    return hasJsonProperty(name, LDValue.of(value));
  }
    
  public static Matcher<LDValue> hasJsonProperty(final String name, final Matcher<LDValue> matcher) {
    return new TypeSafeDiagnosingMatcher<LDValue>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(name + ": ");
        matcher.describeTo(description);
      }
      
      @Override
      protected boolean matchesSafely(LDValue item, Description mismatchDescription) {
        LDValue value = item.get(name);
        if (!matcher.matches(value)) {
          matcher.describeMismatch(value, mismatchDescription);
          return false;
        }
        return true;
      }
    };
  }

  public static Matcher<LDValue> isJsonArray(final Matcher<Iterable<? extends LDValue>> matcher) {
    return new TypeSafeDiagnosingMatcher<LDValue>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("array: ");
        matcher.describeTo(description);
      }
      
      @Override
      protected boolean matchesSafely(LDValue item, Description mismatchDescription) {
        if (item.getType() != LDValueType.ARRAY) {
          matcher.describeMismatch(item, mismatchDescription);
          return false;
        } else {
          Iterable<LDValue> values = item.values();
          if (!matcher.matches(values)) {
            matcher.describeMismatch(values, mismatchDescription);
            return false;
          }
        }
        return true;
      }
    };
  }

  static EventsConfiguration makeEventsConfig(boolean allAttributesPrivate, boolean inlineUsersInEvents,
      Set<String> privateAttrNames) {
    return new EventsConfiguration(
        allAttributesPrivate,
        0, null, EventProcessorBuilder.DEFAULT_FLUSH_INTERVAL,
        inlineUsersInEvents,
        privateAttrNames,
        0, 0, EventProcessorBuilder.DEFAULT_USER_KEYS_FLUSH_INTERVAL,
        EventProcessorBuilder.DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL);
  }

  static EventsConfiguration defaultEventsConfig() {
    return makeEventsConfig(false, false, null);
  }
}
