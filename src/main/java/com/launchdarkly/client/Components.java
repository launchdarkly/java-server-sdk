package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.EventProcessor;
import com.launchdarkly.client.interfaces.EventProcessorFactory;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.DataStoreFactory;
import com.launchdarkly.client.events.Event;
import com.launchdarkly.client.interfaces.DataSource;
import com.launchdarkly.client.interfaces.DataSourceFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Future;

import static com.google.common.util.concurrent.Futures.immediateFuture;

/**
 * Provides factories for the standard implementations of LaunchDarkly component interfaces.
 * @since 4.0.0
 */
public abstract class Components {
  /**
   * Returns a factory for the default in-memory implementation of {@link DataStore}.
   * @return a factory object
   */
  public static DataStoreFactory inMemoryDataStore() {
    return InMemoryDataStoreFactory.INSTANCE;
  }
  
  /**
   * Returns a factory with builder methods for creating a Redis-backed implementation of {@link DataStore},
   * using {@link RedisDataStoreBuilder#DEFAULT_URI}.
   * @return a factory/builder object
   */
  public static RedisDataStoreBuilder redisDataStore() {
    return new RedisDataStoreBuilder();
  }
  
  /**
   * Returns a factory with builder methods for creating a Redis-backed implementation of {@link DataStore},
   * specifying the Redis URI.
   * @param redisUri the URI of the Redis host
   * @return a factory/builder object
   */
  public static RedisDataStoreBuilder redisDataStore(URI redisUri) {
    return new RedisDataStoreBuilder(redisUri);
  }
  
  /**
   * Returns a factory for the default implementation of {@link EventProcessor}, which
   * forwards all analytics events to LaunchDarkly (unless the client is offline or you have
   * set {@link LDConfig.Builder#sendEvents(boolean)} to {@code false}).
   * @return a factory object
   */
  public static EventProcessorFactory defaultEventProcessor() {
    return DefaultEventProcessorFactory.INSTANCE;
  }
  
  /**
   * Returns a factory for a null implementation of {@link EventProcessor}, which will discard
   * all analytics events and not send them to LaunchDarkly, regardless of any other configuration.
   * @return a factory object
   */
  public static EventProcessorFactory nullEventProcessor() {
    return NullEventProcessorFactory.INSTANCE;
  }
  
  /**
   * Returns a factory for the default implementation of {@link DataSource}, which receives
   * feature flag data from LaunchDarkly using either streaming or polling as configured (or does
   * nothing if the client is offline, or in LDD mode).
   * @return a factory object
   */
  public static DataSourceFactory defaultDataSource() {
    return DefaultDataSourceFactory.INSTANCE;
  }
  
  /**
   * Returns a factory for a null implementation of {@link DataSource}, which does not
   * connect to LaunchDarkly, regardless of any other configuration.
   * @return a factory object
   */
  public static DataSourceFactory nullDataSource() {
    return NullDataSourceFactory.INSTANCE;
  }
  
  private static final class InMemoryDataStoreFactory implements DataStoreFactory {
    static final InMemoryDataStoreFactory INSTANCE = new InMemoryDataStoreFactory();
    
    private InMemoryDataStoreFactory() {}
    
    @Override
    public DataStore createDataStore() {
      return new InMemoryDataStore();
    }
  }
  
  private static final class DefaultEventProcessorFactory implements EventProcessorFactory {
    static final DefaultEventProcessorFactory INSTANCE = new DefaultEventProcessorFactory();
    
    private DefaultEventProcessorFactory() {}
    
    @Override
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      if (config.offline || !config.sendEvents) {
        return new NullEventProcessor();
      } else {
        return new DefaultEventProcessor(sdkKey, config);
      }
    }
  }
  
  private static final class NullEventProcessorFactory implements EventProcessorFactory {
    static final NullEventProcessorFactory INSTANCE = new NullEventProcessorFactory();
    
    private NullEventProcessorFactory() {}
    
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      return NullEventProcessor.INSTANCE;
    }
  }
  
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
  
  private static final class DefaultDataSourceFactory implements DataSourceFactory {
    // Note, logger uses LDClient class name for backward compatibility
    private static final Logger logger = LoggerFactory.getLogger(LDClient.class);
    static final DefaultDataSourceFactory INSTANCE = new DefaultDataSourceFactory();
    
    private DefaultDataSourceFactory() {}
    
    @Override
    public DataSource createDataSource(String sdkKey, LDConfig config, DataStore dataStore) {
      if (config.offline) {
        logger.info("Starting LaunchDarkly client in offline mode");
        return new NullDataSource();
      } else if (config.useLdd) {
        logger.info("Starting LaunchDarkly in LDD mode. Skipping direct feature retrieval.");
        return new NullDataSource();
      } else {
        DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(sdkKey, config);
        if (config.stream) {
          logger.info("Enabling streaming API");
          return new StreamProcessor(sdkKey, config, requestor, dataStore, null);
        } else {
          logger.info("Disabling streaming API");
          logger.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");
          return new PollingProcessor(config, requestor, dataStore);
        }
      }
    }
  }
  
  private static final class NullDataSourceFactory implements DataSourceFactory {
    static final NullDataSourceFactory INSTANCE = new NullDataSourceFactory();
    
    private NullDataSourceFactory() {}
    
    @Override
    public DataSource createDataSource(String sdkKey, LDConfig config, DataStore dataStore) {
      return NullDataSource.INSTANCE;
    }
  }

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
