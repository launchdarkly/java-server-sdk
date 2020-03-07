package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeListener;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class TestUtil {
  public static ClientContext clientContext(final String sdkKey, final LDConfig config) {
    return new ClientContextImpl(sdkKey, config, null);
  }

  public static ClientContext clientContext(final String sdkKey, final LDConfig config, DiagnosticAccumulator diagnosticAccumulator) {
    return new ClientContextImpl(sdkKey, config, diagnosticAccumulator);
  }

  public static DataStoreUpdates dataStoreUpdates(final DataStore store) {
    return new DataStoreUpdatesImpl(store, null);
  }
  
  public static DataStoreFactory specificDataStore(final DataStore store) {
    return context -> store;
  }
  
  public static DataStore inMemoryDataStore() {
    return new InMemoryDataStore(); // this is for tests in other packages which can't see this concrete class
  }
  
  public static DataStore initedDataStore() {
    DataStore store = new InMemoryDataStore();
    store.init(new FullDataSet<ItemDescriptor>(null));
    return store;
  }
  
  public static void upsertFlag(DataStore store, FeatureFlag flag) {
    store.upsert(FEATURES, flag.getKey(), new ItemDescriptor(flag.getVersion(), flag));
  }
  
  public static void upsertSegment(DataStore store, Segment segment) {
    store.upsert(SEGMENTS, segment.getKey(), new ItemDescriptor(segment.getVersion(), segment));
  }
  
  public static EventProcessorFactory specificEventProcessor(final EventProcessor ep) {
    return context -> ep;
  }
  
  public static DataSourceFactory specificDataSource(final DataSource up) {
    return (context, dataStoreUpdates) -> up;
  }

  public static DataSourceFactory dataSourceWithData(final FullDataSet<ItemDescriptor> data) {
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
      public ItemDescriptor get(DataKind kind, String key) {
        throw e;
      }

      @Override
      public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
        throw e;
      }

      @Override
      public void init(FullDataSet<ItemDescriptor> allData) { }

      @Override
      public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
        return true;
      }

      @Override
      public boolean isInitialized() {
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
  
  public static class DataSourceFactoryThatExposesUpdater implements DataSourceFactory {
    private final FullDataSet<ItemDescriptor> initialData;
    private DataStoreUpdates dataStoreUpdates;

    public DataSourceFactoryThatExposesUpdater(FullDataSet<ItemDescriptor> initialData) {
      this.initialData = initialData;
    }
    
    @Override
    public DataSource createDataSource(ClientContext context, DataStoreUpdates dataStoreUpdates) {
      this.dataStoreUpdates = dataStoreUpdates;
      return dataSourceWithData(initialData).createDataSource(context, dataStoreUpdates);
    }
    
    public void updateFlag(FeatureFlag flag) {
      dataStoreUpdates.upsert(FEATURES, flag.getKey(), new ItemDescriptor(flag.getVersion(), flag));
    }
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
  
  public static class FlagChangeEventSink extends FlagChangeEventSinkBase<FlagChangeEvent> implements FlagChangeListener {
    @Override
    public void onFlagChange(FlagChangeEvent event) {
      events.add(event);
    }
  }

  public static class FlagValueChangeEventSink extends FlagChangeEventSinkBase<FlagValueChangeEvent> implements FlagValueChangeListener {
    @Override
    public void onFlagValueChange(FlagValueChangeEvent event) {
      events.add(event);
    }
  }

  private static class FlagChangeEventSinkBase<T extends FlagChangeEvent> {
    protected final BlockingQueue<T> events = new ArrayBlockingQueue<>(100);

    public T awaitEvent() {
      try {
        T event = events.poll(1, TimeUnit.SECONDS);
        assertNotNull("expected flag change event", event);
        return event;
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    
    public void expectEvents(String... flagKeys) {
      Set<String> expectedChangedFlagKeys = ImmutableSet.copyOf(flagKeys);
      Set<String> actualChangedFlagKeys = new HashSet<>();
      for (int i = 0; i < expectedChangedFlagKeys.size(); i++) {
        try {
          T e = events.poll(1, TimeUnit.SECONDS);
          if (e == null) {
            fail("expected change events for " + expectedChangedFlagKeys + " but got " + actualChangedFlagKeys);
          }
          actualChangedFlagKeys.add(e.getKey());
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      assertThat(actualChangedFlagKeys, equalTo(expectedChangedFlagKeys));
      expectNoEvents();
    }
    
    public void expectNoEvents() {
      try {
        T event = events.poll(100, TimeUnit.MILLISECONDS);
        assertNull("expected no more flag change events", event);
      } catch (InterruptedException e) {}
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
      Set<UserAttribute> privateAttributes) {
    return new EventsConfiguration(
        allAttributesPrivate,
        0, null, EventProcessorBuilder.DEFAULT_FLUSH_INTERVAL,
        inlineUsersInEvents,
        privateAttributes,
        0, 0, EventProcessorBuilder.DEFAULT_USER_KEYS_FLUSH_INTERVAL,
        EventProcessorBuilder.DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL);
  }

  static EventsConfiguration defaultEventsConfig() {
    return makeEventsConfig(false, false, null);
  }
}
