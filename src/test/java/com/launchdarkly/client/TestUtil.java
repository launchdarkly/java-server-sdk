package com.launchdarkly.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.launchdarkly.client.interfaces.DataSource;
import com.launchdarkly.client.interfaces.DataSourceFactory;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.DataStoreFactory;
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
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class TestUtil {

  public static DataStoreFactory specificDataStore(final DataStore store) {
    return new DataStoreFactory() {
      public DataStore createDataStore() {
        return store;
      }
    };
  }
  
  public static DataStore initedDataStore() {
    DataStore store = new InMemoryDataStore();
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
  
  public static DataSourceFactory specificDataSource(final DataSource up) {
    return new DataSourceFactory() {
      public DataSource createDataSource(String sdkKey, LDConfig config, DataStore dataStore) {
        return up;
      }
    };
  }

  public static DataSourceFactory dataSourceWithData(final Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> data) {
    return new DataSourceFactory() {
      public DataSource createDataSource(String sdkKey, LDConfig config, final DataStore dataStore) {
        return new DataSource() {
          public Future<Void> start() {
            dataStore.init(data);
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
}
