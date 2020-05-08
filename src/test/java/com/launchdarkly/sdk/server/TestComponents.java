package com.launchdarkly.sdk.server;

import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.CacheStats;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStore;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStoreFactory;

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
import java.util.concurrent.atomic.AtomicReference;

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
    return (context, dataSourceUpdates) -> new DataSourceWithData(data, dataSourceUpdates);
  }

  public static DataStore dataStoreThatThrowsException(final RuntimeException e) {
    return new DataStoreThatThrowsException(e);
  }

  public static DataSourceUpdates dataSourceUpdates(final DataStore store) {
    return new DataSourceUpdatesImpl(store, null, null);
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
    return (context, dataSourceUpdates) -> up;
  }

  public static DataStoreFactory specificDataStore(final DataStore store) {
    return (context, statusUpdater) -> store;
  }

  public static PersistentDataStoreFactory specificPersistentDataStore(final PersistentDataStore store) {
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
    private DataSourceUpdates dataSourceUpdates;
  
    public DataSourceFactoryThatExposesUpdater(FullDataSet<ItemDescriptor> initialData) {
      this.initialData = initialData;
    }
    
    @Override
    public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
      this.dataSourceUpdates = dataSourceUpdates;
      return dataSourceWithData(initialData).createDataSource(context, dataSourceUpdates);
    }
    
    public void updateFlag(FeatureFlag flag) {
      dataSourceUpdates.upsert(FEATURES, flag.getKey(), new ItemDescriptor(flag.getVersion(), flag));
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
    private final DataSourceUpdates dataSourceUpdates;
    
    DataSourceWithData(FullDataSet<ItemDescriptor> data, DataSourceUpdates dataSourceUpdates) {
      this.data = data;
      this.dataSourceUpdates = dataSourceUpdates;
    }
    
    public Future<Void> start() {
      dataSourceUpdates.init(data);
      return CompletableFuture.completedFuture(null);
    }

    public boolean isInitialized() {
      return true;
    }

    public void close() throws IOException {
    }
  }
  
  public static class DataStoreFactoryThatExposesUpdater implements DataStoreFactory {
    public volatile DataStoreUpdates dataStoreUpdates;
    private final DataStoreFactory wrappedFactory;

    public DataStoreFactoryThatExposesUpdater(DataStoreFactory wrappedFactory) {
      this.wrappedFactory = wrappedFactory;
    }
    
    @Override
    public DataStore createDataStore(ClientContext context, DataStoreUpdates dataStoreUpdates) {
      this.dataStoreUpdates = dataStoreUpdates;
      return wrappedFactory.createDataStore(context, dataStoreUpdates);
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

    public boolean isStatusMonitoringEnabled() {
      return false;
    }

    public CacheStats getCacheStats() {
      return null;
    }
  }
  
  public static class MockDataStoreStatusProvider implements DataStoreStatusProvider {
    private final EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> statusBroadcaster;
    private final AtomicReference<DataStoreStatusProvider.Status> lastStatus;

    public MockDataStoreStatusProvider() {
      this.statusBroadcaster = new EventBroadcasterImpl<>(
          DataStoreStatusProvider.StatusListener::dataStoreStatusChanged, sharedExecutor);
      this.lastStatus = new AtomicReference<>(new DataStoreStatusProvider.Status(true, false));
    }
    
    // visible for tests
    public void updateStatus(DataStoreStatusProvider.Status newStatus) {
      if (newStatus != null) {
        DataStoreStatusProvider.Status oldStatus = lastStatus.getAndSet(newStatus);
        if (!newStatus.equals(oldStatus)) {
          statusBroadcaster.broadcast(newStatus);
        }
      }
    }
    
    @Override
    public Status getStoreStatus() {
      return lastStatus.get();
    }

    @Override
    public void addStatusListener(StatusListener listener) {
      statusBroadcaster.register(listener);
    }

    @Override
    public void removeStatusListener(StatusListener listener) {
      statusBroadcaster.unregister(listener);
    }

    @Override
    public boolean isStatusMonitoringEnabled() {
      return true;
    }

    @Override
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
