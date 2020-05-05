package com.launchdarkly.sdk.server;

import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;

@SuppressWarnings("javadoc")
public class TestComponents {
  static ScheduledExecutorService sharedExecutor = Executors.newSingleThreadScheduledExecutor();
  
  public static ClientContext clientContext(final String sdkKey, final LDConfig config) {
    return new ClientContextImpl(sdkKey, config, sharedExecutor, null);
  }

  public static ClientContext clientContext(final String sdkKey, final LDConfig config, DiagnosticAccumulator diagnosticAccumulator) {
    return new ClientContextImpl(sdkKey, config, sharedExecutor, diagnosticAccumulator);
  }

  public static DataSourceFactory dataSourceWithData(FullDataSet<ItemDescriptor> data) {
    return (context, dataStoreUpdates) -> new DataSourceWithData(data, dataStoreUpdates);
  }

  public static DataStore dataStoreThatThrowsException(final RuntimeException e) {
    return new DataStoreThatThrowsException(e);
  }

  public static DataStoreUpdates dataStoreUpdates(final DataStore store) {
    return new DataStoreUpdatesImpl(store, null);
  }

  static EventsConfiguration defaultEventsConfig() {
    return makeEventsConfig(false, false, null);
  }

  public static DataSource failedDataSource() {
    return new DataSourceThatNeverInitializes();
  }

  public static DataStore inMemoryDataStore() {
    return new InMemoryDataStore(); // this is for tests in other packages which can't see this concrete class
  }

  public static DataStore initedDataStore() {
    DataStore store = new InMemoryDataStore();
    store.init(new FullDataSet<ItemDescriptor>(null));
    return store;
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

  public static DataSourceFactory specificDataSource(final DataSource up) {
    return (context, dataStoreUpdates) -> up;
  }

  public static DataStoreFactory specificDataStore(final DataStore store) {
    return context -> store;
  }

  public static EventProcessorFactory specificEventProcessor(final EventProcessor ep) {
    return context -> ep;
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
  
  private static class DataSourceThatNeverInitializes implements DataSource {
    public Future<Void> start() {
      return new CompletableFuture<>();
    }

    public boolean isInitialized() {
      return false;
    }

    public void close() throws IOException {
    }          
  };
  
  private static class DataSourceWithData implements DataSource {
    private final FullDataSet<ItemDescriptor> data;
    private final DataStoreUpdates dataStoreUpdates;
    
    DataSourceWithData(FullDataSet<ItemDescriptor> data, DataStoreUpdates dataStoreUpdates) {
      this.data = data;
      this.dataStoreUpdates = dataStoreUpdates;
    }
    
    public Future<Void> start() {
      dataStoreUpdates.init(data);
      return CompletableFuture.completedFuture(null);
    }

    public boolean isInitialized() {
      return true;
    }

    public void close() throws IOException {
    }
  }
  
  private static class DataStoreThatThrowsException implements DataStore {
    private final RuntimeException e;
    
    DataStoreThatThrowsException(RuntimeException e) {
      this.e = e;
    }

    public void close() throws IOException { }

    public ItemDescriptor get(DataKind kind, String key) {
      throw e;
    }

    public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
      throw e;
    }

    public void init(FullDataSet<ItemDescriptor> allData) {
      throw e;
    }

    public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
      throw e;
    }

    public boolean isInitialized() {
      return true;
    }
  }
  
  public static class DataStoreWithStatusUpdates implements DataStore, DataStoreStatusProvider {
    private final DataStore wrappedStore;
    private final List<StatusListener> listeners = new ArrayList<>();
    volatile Status currentStatus = new Status(true, false);
    
    DataStoreWithStatusUpdates(DataStore wrappedStore) {
      this.wrappedStore = wrappedStore;
    }
    
    public void broadcastStatusChange(final Status newStatus) {
      currentStatus = newStatus;
      final StatusListener[] ls;
      synchronized (this) {
        ls = listeners.toArray(new StatusListener[listeners.size()]);
      }
      Thread t = new Thread(() -> {
        for (StatusListener l: ls) {
          l.dataStoreStatusChanged(newStatus);
        }
      });
      t.start();
    }
    
    public void close() throws IOException {
      wrappedStore.close();
    }

    public ItemDescriptor get(DataKind kind, String key) {
      return wrappedStore.get(kind, key);
    }

    public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
      return wrappedStore.getAll(kind);
    }

    public void init(FullDataSet<ItemDescriptor> allData) {
      wrappedStore.init(allData);
    }

    public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
      return wrappedStore.upsert(kind, key, item);
    }

    public boolean isInitialized() {
      return wrappedStore.isInitialized();
    }

    public Status getStoreStatus() {
      return currentStatus;
    }

    public boolean addStatusListener(StatusListener listener) {
      synchronized (this) {
        listeners.add(listener);
      }
      return true;
    }

    public void removeStatusListener(StatusListener listener) {
      synchronized (this) {
        listeners.remove(listener);
      }
    }

    public CacheStats getCacheStats() {
      return null;
    }
  }
  
  public static class MockEventSourceCreator implements StreamProcessor.EventSourceCreator {
    private final EventSource eventSource;
    private final BlockingQueue<StreamProcessor.EventSourceParams> receivedParams = new LinkedBlockingQueue<>();
    
    MockEventSourceCreator(EventSource eventSource) {
      this.eventSource = eventSource;
    }
    
    public EventSource createEventSource(StreamProcessor.EventSourceParams params) {
      receivedParams.add(params);
      return eventSource;
    }
    
    public StreamProcessor.EventSourceParams getNextReceivedParams() {
      return receivedParams.poll();
    }
  }
}
