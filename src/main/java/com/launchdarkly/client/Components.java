package com.launchdarkly.client;

import com.launchdarkly.client.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.client.integrations.PollingDataSourceBuilder;
import com.launchdarkly.client.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.client.interfaces.PersistentDataStoreFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Future;

import static com.google.common.util.concurrent.Futures.immediateFuture;

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
  
  private Components() {}
  
  /**
   * Returns a configuration object for using the default in-memory implementation of a data store.
   * <p>
   * Since it is the default, you do not normally need to call this method, unless you need to create
   * a data store instance for testing purposes.
   * <p>
   * Note that the interface is still named {@link FeatureStoreFactory}, but in a future version it
   * will be renamed to {@code DataStoreFactory}.
   * 
   * @return a factory object
   * @see LDConfig.Builder#dataStore(FeatureStoreFactory)
   * @since 4.12.0
   */
  public static FeatureStoreFactory inMemoryDataStore() {
    return inMemoryFeatureStoreFactory;
  }
  
  /**
   * Returns a configuration builder for some implementation of a persistent data store.
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
   * @since 4.12.0
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
   * Returns a configuration object for using streaming mode to get feature flag data.
   * <p>
   * By default, the SDK uses a streaming connection to receive feature flag data from LaunchDarkly. To use the
   * default behavior, you do not need to call this method. However, if you want to customize the behavior of
   * the connection, call this method to obtain a builder, change its properties with the
   * {@link StreamingDataSourceBuilder} methods, and pass it to {@link LDConfig.Builder#dataSource(UpdateProcessorFactory)}:
   * <pre><code> 
   *     LDConfig config = new LDConfig.Builder()
   *         .dataSource(Components.streamingDataSource().initialReconnectDelayMillis(500))
   *         .build();
   * </code></pre>
   * <p>
   * These properties will override any equivalent deprecated properties that were set with {@code LDConfig.Builder},
   * such as {@link LDConfig.Builder#reconnectTimeMs(long)}.
   * <p> 
   * (Note that the interface is still named {@link UpdateProcessorFactory}, but in a future version it
   * will be renamed to {@code DataSourceFactory}.)
   * 
   * @return a builder for setting streaming connection properties
   * @since 4.12.0
   */
  public static StreamingDataSourceBuilder streamingDataSource() {
    return new StreamingDataSourceBuilderImpl();
  }
  
  /**
   * Returns a configurable factory for using polling mode to get feature flag data.
   * <p>
   * This is not the default behavior; by default, the SDK uses a streaming connection to receive feature flag
   * data from LaunchDarkly. In polling mode, the SDK instead makes a new HTTP request to LaunchDarkly at regular
   * intervals. HTTP caching allows it to avoid redundantly downloading data if there have been no changes, but
   * polling is still less efficient than streaming and should only be used on the advice of LaunchDarkly support.
   * <p>
   * To use polling mode, call this method to obtain a builder, change its properties with the
   * {@link PollingDataSourceBuilder} methods, and pass it to {@link LDConfig.Builder#dataSource(UpdateProcessorFactory)}:
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .dataSource(Components.pollingDataSource().pollIntervalMillis(45000))
   *         .build();
   * </code></pre>
   * <p>
   * These properties will override any equivalent deprecated properties that were set with {@code LDConfig.Builder},
   * such as {@link LDConfig.Builder#pollingIntervalMillis(long)}. However, setting {@link LDConfig.Builder#offline(boolean)}
   * to {@code true} will supersede this setting and completely disable network requests.
   * <p> 
   * (Note that the interface is still named {@link UpdateProcessorFactory}, but in a future version it
   * will be renamed to {@code DataSourceFactory}.)
   * 
   * @return a builder for setting polling properties
   * @since 4.12.0
   */
  public static PollingDataSourceBuilder pollingDataSource() {
    return new PollingDataSourceBuilderImpl();
  }
  
  /**
   * Deprecated method for using the default data source implementation.
   * <p>
   * If you pass the return value of this method to {@link LDConfig.Builder#dataSource(UpdateProcessorFactory)},
   * the behavior is as follows:
   * <ul>
   * <li> If you have set {@link LDConfig.Builder#offline(boolean)} or {@link LDConfig.Builder#useLdd(boolean)}
   * to {@code true}, the SDK will <i>not</i> connect to LaunchDarkly for feature flag data.
   * <li> If you have set {@link LDConfig.Builder#stream(boolean)} to {@code false}, it will use polling mode--
   * equivalent to using {@link #pollingDataSource()} with the options set by {@link LDConfig.Builder#baseURI(URI)}
   * and {@link LDConfig.Builder#pollingIntervalMillis(long)}.
   * <li> Otherwise, it will use streaming mode-- equivalent to using {@link #streamingDataSource()} with
   * the options set by {@link LDConfig.Builder#streamURI(URI)} and {@link LDConfig.Builder#reconnectTimeMs(long)}.
   * </ul>
   * 
   * @return a factory object
   * @deprecated Use {@link #streamingDataSource()}, {@link #pollingDataSource()}, or {@link #externalUpdatesOnly()}.
   */
  @Deprecated
  public static UpdateProcessorFactory defaultUpdateProcessor() {
    return defaultUpdateProcessorFactory;
  }
  
  /**
   * Returns a configuration object that disables connecting for feature flag updates.
   * <p>
   * Passing this to {@link LDConfig.Builder#dataSource(UpdateProcessorFactory)} causes the SDK
   * not to retrieve feature flag data from LaunchDarkly, regardless of any other configuration.
   * This is normally done if you are using the <a href="https://docs.launchdarkly.com/docs/the-relay-proxy">Relay Proxy</a>
   * in "daemon mode", where an external process-- the Relay Proxy-- connects to LaunchDarkly and populates
   * a persistent data store with the feature flag data. The data store could also be populated by
   * another process that is running the LaunchDarkly SDK. If there is no external process updating
   * the data store, then the SDK will not have any feature flag data and will return application
   * default values only.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .dataSource(Components.externalUpdatesOnly())
   *         .dataStore(Components.persistentDataStore(Redis.dataStore())) // assuming the Relay Proxy is using Redis
   *         .build();
   * </code></pre>
   * <p>
   * (Note that the interface is still named {@link UpdateProcessorFactory}, but in a future version it
   * will be renamed to {@code DataSourceFactory}.)
   * 
   * @return a factory object
   * @since 4.12.0
   * @see LDConfig.Builder#dataSource(UpdateProcessorFactory)
   */
  public static UpdateProcessorFactory externalUpdatesOnly() {
    return nullUpdateProcessorFactory;
  }

  /**
   * Deprecated name for {@link #externalUpdatesOnly()}.
   * @return a factory object
   * @deprecated Use {@link #externalUpdatesOnly()}.
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
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
      // We don't need to check config.offline or config.useLdd here; the former is checked automatically
      // by StreamingDataSourceBuilder and PollingDataSourceBuilder, and setting the latter is translated
      // into using externalUpdatesOnly() by LDConfig.Builder.
      if (config.stream) {
        return streamingDataSource()
            .baseUri(config.streamURI)
            .initialReconnectDelayMillis(config.reconnectTimeMs)
            .createUpdateProcessor(sdkKey, config, featureStore);
      } else {
        return pollingDataSource()
            .baseUri(config.baseURI)
            .pollIntervalMillis(config.pollingIntervalMillis)
            .createUpdateProcessor(sdkKey, config, featureStore);
      }
    }
  }
  
  private static final class NullUpdateProcessorFactory implements UpdateProcessorFactory {
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
      if (config.offline) {
        // If they have explicitly called offline(true) to disable everything, we'll log this slightly
        // more specific message.
        LDClient.logger.info("Starting LaunchDarkly client in offline mode");
      } else {
        LDClient.logger.info("LaunchDarkly client will not connect to Launchdarkly for feature flag data");
      }
      return new NullUpdateProcessor();
    }
  }
  
  // Package-private for visibility in tests
  static final class NullUpdateProcessor implements UpdateProcessor {
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
  
  private static final class StreamingDataSourceBuilderImpl extends StreamingDataSourceBuilder {
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      if (config.offline) {
        LDClient.logger.info("Starting LaunchDarkly client in offline mode");
        return Components.externalUpdatesOnly().createUpdateProcessor(sdkKey, config, featureStore);
      }
      
      LDClient.logger.info("Enabling streaming API");

      URI streamUri = baseUri == null ? LDConfig.DEFAULT_STREAM_URI : baseUri;
      URI pollUri;
      if (pollingBaseUri != null) {
        pollUri = pollingBaseUri;
      } else {
        // If they have set a custom base URI, and they did *not* set a custom polling URI, then we can
        // assume they're using Relay in which case both of those values are the same.
        pollUri = baseUri == null ? LDConfig.DEFAULT_BASE_URI : baseUri;
      }
      
      DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(
          sdkKey,
          config,
          pollUri,
          false
          );
      
      return new StreamProcessor(
          sdkKey,
          config,
          requestor,
          featureStore,
          null,
          streamUri,
          initialReconnectDelayMillis
          );
    }
  }
  
  private static final class PollingDataSourceBuilderImpl extends PollingDataSourceBuilder {
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      if (config.offline) {
        LDClient.logger.info("Starting LaunchDarkly client in offline mode");
        return Components.externalUpdatesOnly().createUpdateProcessor(sdkKey, config, featureStore);
      }

      LDClient.logger.info("Disabling streaming API");
      LDClient.logger.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");
      
      DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(
          sdkKey,
          config,
          baseUri == null ? LDConfig.DEFAULT_BASE_URI : baseUri,
          true
          );
      return new PollingProcessor(requestor, featureStore, pollIntervalMillis);
    }
  }
}
