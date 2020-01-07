package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.EventProcessor;
import com.launchdarkly.client.interfaces.EventProcessorFactory;
import com.launchdarkly.client.interfaces.FeatureStore;
import com.launchdarkly.client.interfaces.FeatureStoreFactory;
import com.launchdarkly.client.interfaces.UpdateProcessor;
import com.launchdarkly.client.interfaces.UpdateProcessorFactory;

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
   * 
   * Note that the interface is still named {@link FeatureStoreFactory}, but in a future version it
   * will be renamed to {@code DataStoreFactory}.
   * 
   * @return a factory object
   * @since 4.11.0
   * @see LDConfig.Builder#dataStore(FeatureStoreFactory)
   */
  public static FeatureStoreFactory inMemoryDataStore() {
    return inMemoryFeatureStoreFactory;
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
   * @deprecated Use {@link com.launchdarkly.client.integrations.Redis#dataStore()}.
   */
  @Deprecated
  public static RedisFeatureStoreBuilder redisFeatureStore() {
    return new RedisFeatureStoreBuilder();
  }
  
  /**
   * Deprecated name for {@link com.launchdarkly.client.integrations.Redis#dataStore()}.
   * @param redisUri the URI of the Redis host
   * @return a factory/builder object
   * @deprecated Use {@link com.launchdarkly.client.integrations.Redis#dataStore()} and
   *   {@link com.launchdarkly.client.integrations.RedisDataStoreBuilder#uri(URI)}.
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
  
  private static final class InMemoryFeatureStoreFactory implements FeatureStoreFactory {
    @Override
    public FeatureStore createFeatureStore() {
      return new InMemoryFeatureStore();
    }
  }
  
  private static final class DefaultEventProcessorFactory implements EventProcessorFactory {
    @Override
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      if (config.offline || !config.sendEvents) {
        return new EventProcessor.NullEventProcessor();
      } else {
        return new DefaultEventProcessor(sdkKey, config);
      }
    }
  }
  
  private static final class NullEventProcessorFactory implements EventProcessorFactory {
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      return new EventProcessor.NullEventProcessor();
    }
  }
  
  private static final class DefaultUpdateProcessorFactory implements UpdateProcessorFactory {
    // Note, logger uses LDClient class name for backward compatibility
    private static final Logger logger = LoggerFactory.getLogger(LDClient.class);
    
    @SuppressWarnings("deprecation")
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
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
          return new StreamProcessor(sdkKey, config, requestor, featureStore, null);
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
