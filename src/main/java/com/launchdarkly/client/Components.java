package com.launchdarkly.client;

import com.launchdarkly.client.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.client.interfaces.DataSource;
import com.launchdarkly.client.interfaces.DataSourceFactory;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.DataStoreFactory;
import com.launchdarkly.client.interfaces.Event;
import com.launchdarkly.client.interfaces.EventProcessor;
import com.launchdarkly.client.interfaces.EventProcessorFactory;
import com.launchdarkly.client.interfaces.PersistentDataStoreFactory;

import java.io.IOException;
import java.util.concurrent.Future;

import static com.google.common.util.concurrent.Futures.immediateFuture;

/**
 * Provides factories for the standard implementations of LaunchDarkly component interfaces.
 * @since 4.0.0
 */
public abstract class Components {
  /**
   * Returns a factory for the default in-memory implementation of a data store.
   * 
   * @return a factory object
   * @since 4.11.0
   * @see LDConfig.Builder#dataStore(DataStoreFactory)
   */
  public static DataStoreFactory inMemoryDataStore() {
    return IN_MEMORY_DATA_STORE_FACTORY;
  }

  /**
   * Returns a configurable factory for some implementation of a persistent data store.
   * <p>
   * This method is used in conjunction with another factory object provided by specific components
   * such as the Redis integration. The latter provides builder methods for options that are specific
   * to that integration, while the {@link PersistentDataStoreBuilder} provides options that are
   * applicable to <i>any</i> persistent data store (such as caching). For example:
   * 
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .dataStore(
   *             Components.persistentDataStore(
   *                 Redis.dataStore().url("redis://my-redis-host")
   *             ).cacheSeconds(15)
   *         )
   *         .build();
   * </code></pre>
   * 
   * See {@link PersistentDataStoreBuilder} for more on how this method is used.
   *  
   * @param storeFactory the factory/builder for the specific kind of persistent data store
   * @return a {@link PersistentDataStoreBuilder}
   * @see LDConfig.Builder#dataStore(DataStoreFactory)
   * @see com.launchdarkly.client.integrations.Redis
   * @since 4.11.0
   */
  public static PersistentDataStoreBuilder persistentDataStore(PersistentDataStoreFactory storeFactory) {
    return new PersistentDataStoreBuilder(storeFactory);
  }

  /**
   * Returns a factory for the default implementation of {@link EventProcessor}, which
   * forwards all analytics events to LaunchDarkly (unless the client is offline or you have
   * set {@link LDConfig.Builder#sendEvents(boolean)} to {@code false}).
   * @return a factory object
   * @see LDConfig.Builder#eventProcessor(EventProcessorFactory)
   */
  public static EventProcessorFactory defaultEventProcessor() {
    return DefaultEventProcessorFactory.INSTANCE;
  }
  
  /**
   * Returns a factory for a null implementation of {@link EventProcessor}, which will discard
   * all analytics events and not send them to LaunchDarkly, regardless of any other configuration.
   * @return a factory object
   * @see LDConfig.Builder#eventProcessor(EventProcessorFactory)
   */
  public static EventProcessorFactory nullEventProcessor() {
    return NULL_EVENT_PROCESSOR_FACTORY;
  }
  
  /**
   * Returns a factory for the default implementation of the component for receiving feature flag data
   * from LaunchDarkly. Based on your configuration, this implementation uses either streaming or
   * polling, or does nothing if the client is offline, or in LDD mode.
   * 
   * @return a factory object
   * @since 4.11.0
   * @see LDConfig.Builder#dataSource(DataSourceFactory)
   */
  public static DataSourceFactory defaultDataSource() {
    return DefaultDataSourceFactory.INSTANCE;
  }
  
  /**
   * Returns a factory for a null implementation of {@link DataSource}, which does not
   * connect to LaunchDarkly, regardless of any other configuration.
   * 
   * @return a factory object
   * @since 4.11.0
   * @see LDConfig.Builder#dataSource(DataSourceFactory)
   */
  public static DataSourceFactory nullDataSource() {
    return NULL_DATA_SOURCE_FACTORY;
  }
  
  private static final DataStoreFactory IN_MEMORY_DATA_STORE_FACTORY = () -> new InMemoryDataStore();
  
  private static final class DefaultEventProcessorFactory implements EventProcessorFactoryWithDiagnostics {
    static final EventProcessorFactory INSTANCE = new DefaultEventProcessorFactory();
    
    @Override
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      return createEventProcessor(sdkKey, config, null);
    }

    @Override
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config,
                                               DiagnosticAccumulator diagnosticAccumulator) {
      if (config.offline || !config.sendEvents) {
        return new NullEventProcessor();
      } else {
        return new DefaultEventProcessor(sdkKey, config, diagnosticAccumulator);
      }
    }
  }
  
  private static final EventProcessorFactory NULL_EVENT_PROCESSOR_FACTORY = (sdkKey, config) -> NullEventProcessor.INSTANCE;
  
  /**
   * Stub implementation of {@link EventProcessor} for when we don't want to send any events.
   */
  static final class NullEventProcessor implements EventProcessor {
    static final NullEventProcessor INSTANCE = new NullEventProcessor();
    
    private NullEventProcessor() {}
    
    @Override
    public void sendEvent(Event e) {
    }
    
    @Override
    public void flush() {
    }
    
    @Override
    public void close() {
    }
  }
  
  private static final class DefaultDataSourceFactory implements DataSourceFactoryWithDiagnostics {
    static final DefaultDataSourceFactory INSTANCE = new DefaultDataSourceFactory();
    
    private DefaultDataSourceFactory() {}
    
    @Override
    public DataSource createDataSource(String sdkKey, LDConfig config, DataStore dataStore) {
      return createDataSource(sdkKey, config, dataStore, null);
    }

    @Override
    public DataSource createDataSource(String sdkKey, LDConfig config, DataStore dataStore,
                                       DiagnosticAccumulator diagnosticAccumulator) {
      if (config.offline) {
        LDClient.logger.info("Starting LaunchDarkly client in offline mode");
        return new NullDataSource();
      } else if (config.useLdd) {
        LDClient.logger.info("Starting LaunchDarkly in LDD mode. Skipping direct feature retrieval.");
        return new NullDataSource();
      } else {
        DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(sdkKey, config);
        if (config.stream) {
          LDClient.logger.info("Enabling streaming API");
          return new StreamProcessor(sdkKey, config, requestor, dataStore, null, diagnosticAccumulator);
        } else {
          LDClient.logger.info("Disabling streaming API");
          LDClient.logger.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");
          return new PollingProcessor(config, requestor, dataStore);
        }
      }
    }
  }
  
  private static final DataSourceFactory NULL_DATA_SOURCE_FACTORY = (sdkKey, config, dataStore) -> NullDataSource.INSTANCE;

  // exposed as package-private for testing
  static final class NullDataSource implements DataSource {
    static final NullDataSource INSTANCE = new NullDataSource();
    
    private NullDataSource() {}
    
    @Override
    public Future<Void> start() {
      return immediateFuture(null);
    }

    @Override
    public boolean initialized() {
      return true;
    }

    @Override
    public void close() throws IOException {}
  }
}
