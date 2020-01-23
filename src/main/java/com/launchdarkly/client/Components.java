package com.launchdarkly.client;

import com.launchdarkly.client.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.client.interfaces.DiagnosticDescription;
import com.launchdarkly.client.interfaces.PersistentDataStoreFactory;
import com.launchdarkly.client.value.LDValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Provides factories for the standard implementations of LaunchDarkly component interfaces.
 * @since 4.0.0
 */
public abstract class Components {
  private static final FeatureStoreFactory inMemoryFeatureStoreFactory = new InMemoryFeatureStoreFactory();
  private static final EventProcessorFactory defaultEventProcessorFactory = new DefaultEventProcessorFactory();
  private static final EventProcessorFactory nullEventProcessorFactory = new NullEventProcessorFactory();
  private static final UpdateProcessorFactory defaultUpdateProcessorFactory = new DefaultUpdateProcessorFactory();
  private static final UpdateProcessorFactory nullUpdateProcessorFactory = new NullUpdateProcessorFactory();
  
  /**
   * Returns a factory for the default in-memory implementation of a data store.
   * <p>
   * Note that the interface is still named {@link FeatureStoreFactory}, but in a future version it
   * will be renamed to {@code DataStoreFactory}.
   * 
   * @return a factory object
   * @see LDConfig.Builder#dataStore(FeatureStoreFactory)
   * @since 4.11.0
   */
  public static FeatureStoreFactory inMemoryDataStore() {
    return inMemoryFeatureStoreFactory;
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
   * @see LDConfig.Builder#dataStore(FeatureStoreFactory)
   * @see com.launchdarkly.client.integrations.Redis
   * @since 4.11.0
   */
  public static PersistentDataStoreBuilder persistentDataStore(PersistentDataStoreFactory storeFactory) {
    return new PersistentDataStoreBuilder(storeFactory);
  }

  /**
   * Deprecated name for {@link #inMemoryDataStore()}.
   * @return a factory object
   * @deprecated Use {@link #inMemoryDataStore()}.
   */
  @Deprecated
  public static FeatureStoreFactory inMemoryFeatureStore() {
    return inMemoryFeatureStoreFactory;
  }
  
  /**
   * Deprecated name for {@link com.launchdarkly.client.integrations.Redis#dataStore()}.
   * @return a factory/builder object
   * @deprecated Use {@link #persistentDataStore(PersistentDataStoreFactory)} with
   * {@link com.launchdarkly.client.integrations.Redis#dataStore()}.
   */
  @Deprecated
  public static RedisFeatureStoreBuilder redisFeatureStore() {
    return new RedisFeatureStoreBuilder();
  }
  
  /**
   * Deprecated name for {@link com.launchdarkly.client.integrations.Redis#dataStore()}.
   * @param redisUri the URI of the Redis host
   * @return a factory/builder object
   * @deprecated Use {@link #persistentDataStore(PersistentDataStoreFactory)} with
   * {@link com.launchdarkly.client.integrations.Redis#dataStore()} and
   * {@link com.launchdarkly.client.integrations.RedisDataStoreBuilder#uri(URI)}.
   */
  @Deprecated
  public static RedisFeatureStoreBuilder redisFeatureStore(URI redisUri) {
    return new RedisFeatureStoreBuilder(redisUri);
  }
  
  /**
   * Returns a factory for the default implementation of {@link EventProcessor}, which
   * forwards all analytics events to LaunchDarkly (unless the client is offline or you have
   * set {@link LDConfig.Builder#sendEvents(boolean)} to {@code false}).
   * @return a factory object
   * @see LDConfig.Builder#eventProcessorFactory(EventProcessorFactory)
   */
  public static EventProcessorFactory defaultEventProcessor() {
    return defaultEventProcessorFactory;
  }
  
  /**
   * Returns a factory for a null implementation of {@link EventProcessor}, which will discard
   * all analytics events and not send them to LaunchDarkly, regardless of any other configuration.
   * @return a factory object
   * @see LDConfig.Builder#eventProcessorFactory(EventProcessorFactory)
   */
  public static EventProcessorFactory nullEventProcessor() {
    return nullEventProcessorFactory;
  }
  
  /**
   * Returns a factory for the default implementation of the component for receiving feature flag data
   * from LaunchDarkly. Based on your configuration, this implementation uses either streaming or
   * polling, or does nothing if the client is offline, or in LDD mode.
   * 
   * Note that the interface is still named {@link UpdateProcessorFactory}, but in a future version it
   * will be renamed to {@code DataSourceFactory}.
   *  
   * @return a factory object
   * @since 4.11.0
   * @see LDConfig.Builder#dataSource(UpdateProcessorFactory)
   */
  public static UpdateProcessorFactory defaultDataSource() {
    return defaultUpdateProcessorFactory;
  }

  /**
   * Deprecated name for {@link #defaultDataSource()}.
   * @return a factory object
   * @deprecated Use {@link #defaultDataSource()}.
   */
  @Deprecated
  public static UpdateProcessorFactory defaultUpdateProcessor() {
    return defaultUpdateProcessorFactory;
  }
  
  /**
   * Returns a factory for a null implementation of {@link UpdateProcessor}, which does not
   * connect to LaunchDarkly, regardless of any other configuration.
   * 
   * Note that the interface is still named {@link UpdateProcessorFactory}, but in a future version it
   * will be renamed to {@code DataSourceFactory}.
   * 
   * @return a factory object
   * @since 4.11.0
   * @see LDConfig.Builder#dataSource(UpdateProcessorFactory)
   */
  public static UpdateProcessorFactory nullDataSource() {
    return nullUpdateProcessorFactory;
  }

  /**
   * Deprecated name for {@link #nullDataSource()}.
   * @return a factory object
   * @deprecated Use {@link #nullDataSource()}.
   */
  @Deprecated
  public static UpdateProcessorFactory nullUpdateProcessor() {
    return nullUpdateProcessorFactory;
  }
  
  private static final class InMemoryFeatureStoreFactory implements FeatureStoreFactory, DiagnosticDescription {
    @Override
    public FeatureStore createFeatureStore() {
      return new InMemoryFeatureStore();
    }

    @Override
    public LDValue describeConfiguration() {
      return LDValue.of("memory");
    }
  }
  
  private static final class DefaultEventProcessorFactory implements EventProcessorFactoryWithDiagnostics {
    @Override
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      return createEventProcessor(sdkKey, config, null);
    }

    public EventProcessor createEventProcessor(String sdkKey, LDConfig config,
                                               DiagnosticAccumulator diagnosticAccumulator) {
      if (config.offline || !config.sendEvents) {
        return new EventProcessor.NullEventProcessor();
      } else {
        return new DefaultEventProcessor(sdkKey, config, diagnosticAccumulator);
      }
    }
  }
  
  private static final class NullEventProcessorFactory implements EventProcessorFactory {
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      return new EventProcessor.NullEventProcessor();
    }
  }
  
  private static final class DefaultUpdateProcessorFactory implements UpdateProcessorFactoryWithDiagnostics {
    // Note, logger uses LDClient class name for backward compatibility
    private static final Logger logger = LoggerFactory.getLogger(LDClient.class);
    
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
      return createUpdateProcessor(sdkKey, config, featureStore, null);
    }

    @SuppressWarnings("deprecation")
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore,
                                                 DiagnosticAccumulator diagnosticAccumulator) {
      if (config.offline) {
        logger.info("Starting LaunchDarkly client in offline mode");
        return new UpdateProcessor.NullUpdateProcessor();
      } else if (config.useLdd) {
        logger.info("Starting LaunchDarkly in LDD mode. Skipping direct feature retrieval.");
        return new UpdateProcessor.NullUpdateProcessor();
      } else {
        DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(sdkKey, config);
        if (config.stream) {
          logger.info("Enabling streaming API");
          return new StreamProcessor(sdkKey, config, requestor, featureStore, null, diagnosticAccumulator);
        } else {
          logger.info("Disabling streaming API");
          logger.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");
          return new PollingProcessor(config, requestor, featureStore);
        }
      }
    }
  }
  
  private static final class NullUpdateProcessorFactory implements UpdateProcessorFactory {
    @SuppressWarnings("deprecation")
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
      return new UpdateProcessor.NullUpdateProcessor();
    }
  }
}
