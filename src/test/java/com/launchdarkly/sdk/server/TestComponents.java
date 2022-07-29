package com.launchdarkly.sdk.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.CacheStats;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceFactory;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdates;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreFactory;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.subsystems.DataStoreUpdates;
import com.launchdarkly.sdk.server.subsystems.Event;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;
import com.launchdarkly.sdk.server.subsystems.EventProcessorFactory;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStoreFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.launchdarkly.testhelpers.ConcurrentHelpers.awaitValue;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

@SuppressWarnings("javadoc")
public class TestComponents {
  static ScheduledExecutorService sharedExecutor = newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("TestComponents-sharedExecutor-%d").build());
  
  public static LDLogger nullLogger = LDLogger.withAdapter(Logs.none(), "");
  
  public static ClientContext clientContext(final String sdkKey, final LDConfig config) {
    return ClientContextImpl.fromConfig(sdkKey, config, sharedExecutor, null);
  }

  public static ClientContext clientContext(final String sdkKey, final LDConfig config, DiagnosticAccumulator diagnosticAccumulator) {
    return ClientContextImpl.fromConfig(sdkKey, config, sharedExecutor, diagnosticAccumulator);
  }

  public static HttpConfiguration defaultHttpConfiguration() {
    return clientContext("", LDConfig.DEFAULT).getHttp();
  }
  
  public static DataStore dataStoreThatThrowsException(RuntimeException e) {
    return new DataStoreThatThrowsException(e);
  }

  public static MockDataSourceUpdates dataSourceUpdates(DataStore store) {
    return dataSourceUpdates(store, null);
  }

  public static MockDataSourceUpdates dataSourceUpdates(DataStore store, DataStoreStatusProvider dataStoreStatusProvider) {
    return new MockDataSourceUpdates(store, dataStoreStatusProvider);
  }
  
  static EventsConfiguration defaultEventsConfig() {
    return makeEventsConfig(false, null);
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

  static EventsConfiguration makeEventsConfig(boolean allAttributesPrivate,
      Iterable<AttributeRef> privateAttributes) {
    return new EventsConfiguration(
        allAttributesPrivate,
        0,
        null,
        null,
        EventProcessorBuilder.DEFAULT_FLUSH_INTERVAL,
        privateAttributes,
        0,
        EventProcessorBuilder.DEFAULT_USER_KEYS_FLUSH_INTERVAL,
        EventProcessorBuilder.DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL
        );
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
    volatile List<Event> events = new ArrayList<>();
    volatile int flushCount;
  
    @Override
    public void close() throws IOException {}
  
    @Override
    public void sendEvent(Event e) {
      events.add(e);
    }
  
    @Override
    public void flush() {
      flushCount++;
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
  
  public static class MockDataSourceUpdates implements DataSourceUpdates {
    public static class UpsertParams {
      public final DataKind kind;
      public final String key;
      public final ItemDescriptor item;
      
      UpsertParams(DataKind kind, String key, ItemDescriptor item) {
        super();
        this.kind = kind;
        this.key = key;
        this.item = item;
      }
    }
    
    private final DataSourceUpdatesImpl wrappedInstance;
    private final DataStoreStatusProvider dataStoreStatusProvider;
    public final EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeEventBroadcaster;
    public final EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status>
      statusBroadcaster;
    public final BlockingQueue<FullDataSet<ItemDescriptor>> receivedInits = new LinkedBlockingQueue<>();
    public final BlockingQueue<UpsertParams> receivedUpserts = new LinkedBlockingQueue<>();
    
    public MockDataSourceUpdates(DataStore store, DataStoreStatusProvider dataStoreStatusProvider) {
      this.dataStoreStatusProvider = dataStoreStatusProvider;
      this.flagChangeEventBroadcaster = EventBroadcasterImpl.forFlagChangeEvents(sharedExecutor, nullLogger);
      this.statusBroadcaster = EventBroadcasterImpl.forDataSourceStatus(sharedExecutor, nullLogger);
      this.wrappedInstance = new DataSourceUpdatesImpl(
          store,
          dataStoreStatusProvider,
          flagChangeEventBroadcaster,
          statusBroadcaster,
          sharedExecutor,
          null,
          nullLogger
          );
    }

    @Override
    public boolean init(FullDataSet<ItemDescriptor> allData) {
      boolean result = wrappedInstance.init(allData);
      receivedInits.add(allData);
      return result;
    }

    @Override
    public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
      boolean result = wrappedInstance.upsert(kind, key, item);
      receivedUpserts.add(new UpsertParams(kind, key, item));
      return result;
    }

    @Override
    public DataStoreStatusProvider getDataStoreStatusProvider() {
      return dataStoreStatusProvider;
    }

    @Override
    public void updateStatus(State newState, ErrorInfo newError) {
      wrappedInstance.updateStatus(newState, newError);
    }
    
    public DataSourceStatusProvider.Status getLastStatus() {
      return wrappedInstance.getLastStatus();
    }
    
    // this method is surfaced for use by tests in other packages that can't see the EventBroadcasterImpl class
    public void register(DataSourceStatusProvider.StatusListener listener) {
      statusBroadcaster.register(listener);
    }
    
    public FullDataSet<ItemDescriptor> awaitInit() {
      return awaitValue(receivedInits, 5, TimeUnit.SECONDS);
    }
    
    public UpsertParams awaitUpsert() {
      return awaitValue(receivedUpserts, 5, TimeUnit.SECONDS);
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
  
  public static class DelegatingDataStore implements DataStore {
    private final DataStore store;
    private final Runnable preUpdateHook;
    
    public DelegatingDataStore(DataStore store, Runnable preUpdateHook) {
      this.store = store;
      this.preUpdateHook = preUpdateHook;
    }
    
    @Override
    public void close() throws IOException {
      store.close();
    }

    @Override
    public void init(FullDataSet<ItemDescriptor> allData) {
      if (preUpdateHook != null) {
        preUpdateHook.run();
      }
      store.init(allData);
    }

    @Override
    public ItemDescriptor get(DataKind kind, String key) {
      return store.get(kind, key);
    }

    @Override
    public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
      return store.getAll(kind);
    }

    @Override
    public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
      if (preUpdateHook != null) {
        preUpdateHook.run();
      }
      return store.upsert(kind, key, item);
    }

    @Override
    public boolean isInitialized() {
      return store.isInitialized();
    }

    @Override
    public boolean isStatusMonitoringEnabled() {
      return store.isStatusMonitoringEnabled();
    }

    @Override
    public CacheStats getCacheStats() {
      return store.getCacheStats();
    }
  }
  
  public static class MockDataStoreStatusProvider implements DataStoreStatusProvider {
    public final EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> statusBroadcaster;
    private final AtomicReference<DataStoreStatusProvider.Status> lastStatus;
    private final boolean statusMonitoringEnabled;
    
    public MockDataStoreStatusProvider() {
      this(true);
    }
    
    public MockDataStoreStatusProvider(boolean statusMonitoringEnabled) {
      this.statusBroadcaster = EventBroadcasterImpl.forDataStoreStatus(sharedExecutor, nullLogger);
      this.lastStatus = new AtomicReference<>(new DataStoreStatusProvider.Status(true, false));
      this.statusMonitoringEnabled = statusMonitoringEnabled;
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
    public Status getStatus() {
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
      return statusMonitoringEnabled;
    }

    @Override
    public CacheStats getCacheStats() {
      return null;
    }
  }
}
