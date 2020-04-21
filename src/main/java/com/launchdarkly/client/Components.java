package com.launchdarkly.client;

import com.launchdarkly.client.DiagnosticEvent.ConfigProperty;
import com.launchdarkly.client.integrations.EventProcessorBuilder;
import com.launchdarkly.client.integrations.HttpConfigurationBuilder;
import com.launchdarkly.client.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.client.integrations.PollingDataSourceBuilder;
import com.launchdarkly.client.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.client.interfaces.DiagnosticDescription;
import com.launchdarkly.client.interfaces.HttpAuthentication;
import com.launchdarkly.client.interfaces.HttpConfiguration;
import com.launchdarkly.client.interfaces.PersistentDataStoreFactory;
import com.launchdarkly.client.utils.CachingStoreWrapper;
import com.launchdarkly.client.utils.FeatureStoreCore;
import com.launchdarkly.client.value.LDValue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.concurrent.Future;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import okhttp3.Credentials;

/**
 * Provides configurable factories for the standard implementations of LaunchDarkly component interfaces.
 * <p>
 * Some of the configuration options in {@link LDConfig.Builder} affect the entire SDK, but others are
 * specific to one area of functionality, such as how the SDK receives feature flag updates or processes
 * analytics events. For the latter, the standard way to specify a configuration is to call one of the
 * static methods in {@link Components} (such as {@link #streamingDataSource()}), apply any desired
 * configuration change to the object that that method returns (such as {@link StreamingDataSourceBuilder#initialReconnectDelayMillis(long)},
 * and then use the corresponding method in {@link LDConfig.Builder} (such as {@link LDConfig.Builder#dataSource(UpdateProcessorFactory)})
 * to use that configured component in the SDK.
 * 
 * @since 4.0.0
 */
@SuppressWarnings("deprecation")
public abstract class Components {
  private static final FeatureStoreFactory inMemoryFeatureStoreFactory = new InMemoryFeatureStoreFactory();
  private static final EventProcessorFactory defaultEventProcessorFactory = new DefaultEventProcessorFactory();
  private static final EventProcessorFactory nullEventProcessorFactory = new NullEventProcessorFactory();
  private static final UpdateProcessorFactory defaultUpdateProcessorFactory = new DefaultUpdateProcessorFactory();
  private static final NullUpdateProcessorFactory nullUpdateProcessorFactory = new NullUpdateProcessorFactory();
  
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
    return new PersistentDataStoreBuilderImpl(storeFactory);
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
   * Returns a configuration builder for analytics event delivery.
   * <p>
   * The default configuration has events enabled with default settings. If you want to
   * customize this behavior, call this method to obtain a builder, change its properties
   * with the {@link EventProcessorBuilder} properties, and pass it to {@link LDConfig.Builder#events(EventProcessorFactory)}:
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .events(Components.sendEvents().capacity(5000).flushIntervalSeconds(2))
   *         .build();
   * </code></pre>
   * To completely disable sending analytics events, use {@link #noEvents()} instead.
   *
   * @return a builder for setting streaming connection properties
   * @see #noEvents()
   * @see LDConfig.Builder#events
   * @since 4.12.0
   */
  public static EventProcessorBuilder sendEvents() {
    return new EventProcessorBuilderImpl();
  }
  
  /**
   * Deprecated method for using the default analytics events implementation.
   * <p>
   * If you pass the return value of this method to {@link LDConfig.Builder#events(EventProcessorFactory)},
   * the behavior is as follows:
   * <ul>
   * <li> If you have set {@link LDConfig.Builder#offline(boolean)} to {@code true}, or
   * {@link LDConfig.Builder#sendEvents(boolean)} to {@code false}, the SDK will <i>not</i> send events to
   * LaunchDarkly.
   * <li> Otherwise, it will send events, using the properties set by the deprecated events configuration
   * methods such as {@link LDConfig.Builder#capacity(int)}.
   * </ul>
   * 
   * @return a factory object
   * @deprecated Use {@link #sendEvents()} or {@link #noEvents}.
   */
  @Deprecated
  public static EventProcessorFactory defaultEventProcessor() {
    return defaultEventProcessorFactory;
  }
  
  /**
   * Returns a configuration object that disables analytics events.
   * <p>
   * Passing this to {@link LDConfig.Builder#events(EventProcessorFactory)} causes the SDK
   * to discard all analytics events and not send them to LaunchDarkly, regardless of any other configuration.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .events(Components.noEvents())
   *         .build();
   * </code></pre>
   * 
   * @return a factory object
   * @see #sendEvents()
   * @see LDConfig.Builder#events(EventProcessorFactory)
   * @since 4.12.0
   */
  public static EventProcessorFactory noEvents() {
    return nullEventProcessorFactory;
  }

  /**
   * Deprecated name for {@link #noEvents()}.
   * @return a factory object
   * @see LDConfig.Builder#events(EventProcessorFactory)
   * @deprecated Use {@link #noEvents()}.
   */
  @Deprecated
  public static EventProcessorFactory nullEventProcessor() {
    return nullEventProcessorFactory;
  }

  /**
   * Returns a configurable factory for using streaming mode to get feature flag data.
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
   * @see LDConfig.Builder#dataSource(UpdateProcessorFactory)
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
   * @see LDConfig.Builder#dataSource(UpdateProcessorFactory)
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
   * Returns a configuration object that disables a direct connection with LaunchDarkly for feature flag updates.
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
  
  /**
   * Returns a configurable factory for the SDK's networking configuration.
   * <p>
   * Passing this to {@link LDConfig.Builder#http(com.launchdarkly.client.interfaces.HttpConfigurationFactory)}
   * applies this configuration to all HTTP/HTTPS requests made by the SDK.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .http(
   *              Components.httpConfiguration()
   *                  .connectTimeoutMillis(3000)
   *                  .proxyHostAndPort("my-proxy", 8080)
   *         )
   *         .build();
   * </code></pre>
   * <p>
   * These properties will override any equivalent deprecated properties that were set with {@code LDConfig.Builder},
   * such as {@link LDConfig.Builder#connectTimeout(int)}. However, setting {@link LDConfig.Builder#offline(boolean)}
   * to {@code true} will supersede these settings and completely disable network requests.
   * 
   * @return a factory object
   * @since 4.13.0
   * @see LDConfig.Builder#http(com.launchdarkly.client.interfaces.HttpConfigurationFactory)
   */
  public static HttpConfigurationBuilder httpConfiguration() {
    return new HttpConfigurationBuilderImpl();
  }
  
  /**
   * Configures HTTP basic authentication, for use with a proxy server.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .http(
   *              Components.httpConfiguration()
   *                  .proxyHostAndPort("my-proxy", 8080)
   *                  .proxyAuthentication(Components.httpBasicAuthentication("username", "password"))
   *         )
   *         .build();
   * </code></pre>
   * 
   * @param username the username
   * @param password the password
   * @return the basic authentication strategy
   * @since 4.13.0
   * @see HttpConfigurationBuilder#proxyAuth(HttpAuthentication)
   */
  public static HttpAuthentication httpBasicAuthentication(String username, String password) {
    return new HttpBasicAuthentication(username, password);
  }
  
  private static final class InMemoryFeatureStoreFactory implements FeatureStoreFactory, DiagnosticDescription {
    @Override
    public FeatureStore createFeatureStore() {
      return new InMemoryFeatureStore();
    }

    @Override
    public LDValue describeConfiguration(LDConfig config) {
      return LDValue.of("memory");
    }
  }
  
  // This can be removed once the deprecated event config options have been removed.
  private static final class DefaultEventProcessorFactory implements EventProcessorFactoryWithDiagnostics, DiagnosticDescription {
    @Override
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      return createEventProcessor(sdkKey, config, null);
    }

    public EventProcessor createEventProcessor(String sdkKey, LDConfig config,
                                               DiagnosticAccumulator diagnosticAccumulator) {
      if (config.offline || !config.deprecatedSendEvents) {
        return new NullEventProcessor();
      }
      return new DefaultEventProcessor(sdkKey,
          config,
          new EventsConfiguration(
              config.deprecatedAllAttributesPrivate,
              config.deprecatedCapacity,
              config.deprecatedEventsURI == null ? LDConfig.DEFAULT_EVENTS_URI : config.deprecatedEventsURI,
              config.deprecatedFlushInterval,
              config.deprecatedInlineUsersInEvents,
              config.deprecatedPrivateAttrNames,
              config.deprecatedSamplingInterval,
              config.deprecatedUserKeysCapacity,
              config.deprecatedUserKeysFlushInterval,
              EventProcessorBuilder.DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL_SECONDS
              ),
          config.httpConfig,
          diagnosticAccumulator
          );
    }
    
    @Override
    public LDValue describeConfiguration(LDConfig config) {
      return LDValue.buildObject()
          .put(ConfigProperty.ALL_ATTRIBUTES_PRIVATE.name, config.deprecatedAllAttributesPrivate)
          .put(ConfigProperty.CUSTOM_EVENTS_URI.name, config.deprecatedEventsURI != null &&
              !config.deprecatedEventsURI.equals(LDConfig.DEFAULT_EVENTS_URI))
          .put(ConfigProperty.DIAGNOSTIC_RECORDING_INTERVAL_MILLIS.name,
              EventProcessorBuilder.DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL_SECONDS * 1000) // not configurable via deprecated API
          .put(ConfigProperty.EVENTS_CAPACITY.name, config.deprecatedCapacity)
          .put(ConfigProperty.EVENTS_FLUSH_INTERVAL_MILLIS.name, config.deprecatedFlushInterval * 1000)
          .put(ConfigProperty.INLINE_USERS_IN_EVENTS.name, config.deprecatedInlineUsersInEvents)
          .put(ConfigProperty.SAMPLING_INTERVAL.name, config.deprecatedSamplingInterval)
          .put(ConfigProperty.USER_KEYS_CAPACITY.name, config.deprecatedUserKeysCapacity)
          .put(ConfigProperty.USER_KEYS_FLUSH_INTERVAL_MILLIS.name, config.deprecatedUserKeysFlushInterval * 1000)
          .build();
    }
  }
  
  private static final class NullEventProcessorFactory implements EventProcessorFactory {
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      return new NullEventProcessor();
    }
  }
  
  static final class NullEventProcessor implements EventProcessor {
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

  // This can be removed once the deprecated polling/streaming config options have been removed.
  private static final class DefaultUpdateProcessorFactory implements UpdateProcessorFactoryWithDiagnostics,
      DiagnosticDescription {
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
      return createUpdateProcessor(sdkKey, config, featureStore, null);
    }
    
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore,
        DiagnosticAccumulator diagnosticAccumulator) {
      if (config.offline) {
        return Components.externalUpdatesOnly().createUpdateProcessor(sdkKey, config, featureStore);
      }
      // We don't need to check config.offline or config.useLdd here; the former is checked automatically
      // by StreamingDataSourceBuilder and PollingDataSourceBuilder, and setting the latter is translated
      // into using externalUpdatesOnly() by LDConfig.Builder.
      if (config.deprecatedStream) {
        StreamingDataSourceBuilderImpl builder = (StreamingDataSourceBuilderImpl)streamingDataSource()
            .baseURI(config.deprecatedStreamURI)
            .pollingBaseURI(config.deprecatedBaseURI)
            .initialReconnectDelayMillis(config.deprecatedReconnectTimeMs);
        return builder.createUpdateProcessor(sdkKey, config, featureStore, diagnosticAccumulator);
      } else {
        return pollingDataSource()
            .baseURI(config.deprecatedBaseURI)
            .pollIntervalMillis(config.deprecatedPollingIntervalMillis)
            .createUpdateProcessor(sdkKey, config, featureStore);
      }
    }
    
    @Override
    public LDValue describeConfiguration(LDConfig config) {
      if (config.offline) {
        return nullUpdateProcessorFactory.describeConfiguration(config);
      }
      if (config.deprecatedStream) {
        return LDValue.buildObject()
            .put(ConfigProperty.STREAMING_DISABLED.name, false)
            .put(ConfigProperty.CUSTOM_BASE_URI.name,
                config.deprecatedBaseURI != null && !config.deprecatedBaseURI.equals(LDConfig.DEFAULT_BASE_URI))
            .put(ConfigProperty.CUSTOM_STREAM_URI.name,
                config.deprecatedStreamURI != null && !config.deprecatedStreamURI.equals(LDConfig.DEFAULT_STREAM_URI))
            .put(ConfigProperty.RECONNECT_TIME_MILLIS.name, config.deprecatedReconnectTimeMs)
            .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
            .build();
      } else {
        return LDValue.buildObject()
            .put(ConfigProperty.STREAMING_DISABLED.name, true)
            .put(ConfigProperty.CUSTOM_BASE_URI.name,
                config.deprecatedBaseURI != null && !config.deprecatedBaseURI.equals(LDConfig.DEFAULT_BASE_URI))
            .put(ConfigProperty.CUSTOM_STREAM_URI.name, false)
            .put(ConfigProperty.POLLING_INTERVAL_MILLIS.name, config.deprecatedPollingIntervalMillis)
            .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
            .build();
      }
    }

  }
  
  private static final class NullUpdateProcessorFactory implements UpdateProcessorFactory, DiagnosticDescription {
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

    @Override
    public LDValue describeConfiguration(LDConfig config) {
      // We can assume that if they don't have a data source, and they *do* have a persistent data store, then
      // they're using Relay in daemon mode.
      return LDValue.buildObject()
          .put(ConfigProperty.CUSTOM_BASE_URI.name, false)
          .put(ConfigProperty.CUSTOM_STREAM_URI.name, false)
          .put(ConfigProperty.STREAMING_DISABLED.name, false)
          .put(ConfigProperty.USING_RELAY_DAEMON.name,
              config.dataStoreFactory != null && config.dataStoreFactory != Components.inMemoryDataStore())
          .build();
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
  
  private static final class StreamingDataSourceBuilderImpl extends StreamingDataSourceBuilder
      implements UpdateProcessorFactoryWithDiagnostics, DiagnosticDescription {
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
      return createUpdateProcessor(sdkKey, config, featureStore, null);
    }
    
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore,
        DiagnosticAccumulator diagnosticAccumulator) {
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      if (config.offline) {
        return Components.externalUpdatesOnly().createUpdateProcessor(sdkKey, config, featureStore);
      }
      
      LDClient.logger.info("Enabling streaming API");

      URI streamUri = baseURI == null ? LDConfig.DEFAULT_STREAM_URI : baseURI;
      URI pollUri;
      if (pollingBaseURI != null) {
        pollUri = pollingBaseURI;
      } else {
        // If they have set a custom base URI, and they did *not* set a custom polling URI, then we can
        // assume they're using Relay in which case both of those values are the same.
        pollUri = baseURI == null ? LDConfig.DEFAULT_BASE_URI : baseURI;
      }
      
      DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(
          sdkKey,
          config.httpConfig,
          pollUri,
          false
          );
      
      return new StreamProcessor(
          sdkKey,
          config.httpConfig,
          requestor,
          featureStore,
          null,
          diagnosticAccumulator,
          streamUri,
          initialReconnectDelayMillis
          );
    }

    @Override
    public LDValue describeConfiguration(LDConfig config) {
      if (config.offline) {
        return nullUpdateProcessorFactory.describeConfiguration(config);
      }
      return LDValue.buildObject()
          .put(ConfigProperty.STREAMING_DISABLED.name, false)
          .put(ConfigProperty.CUSTOM_BASE_URI.name,
              (pollingBaseURI != null && !pollingBaseURI.equals(LDConfig.DEFAULT_BASE_URI)) ||
              (pollingBaseURI == null && baseURI != null && !baseURI.equals(LDConfig.DEFAULT_STREAM_URI)))
          .put(ConfigProperty.CUSTOM_STREAM_URI.name,
              baseURI != null && !baseURI.equals(LDConfig.DEFAULT_STREAM_URI))
          .put(ConfigProperty.RECONNECT_TIME_MILLIS.name, initialReconnectDelayMillis)
          .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
          .build();
    }
  }
  
  private static final class PollingDataSourceBuilderImpl extends PollingDataSourceBuilder implements DiagnosticDescription {
    @Override
    public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore) {
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      if (config.offline) {
        return Components.externalUpdatesOnly().createUpdateProcessor(sdkKey, config, featureStore);
      }

      LDClient.logger.info("Disabling streaming API");
      LDClient.logger.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");
      
      DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(
          sdkKey,
          config.httpConfig,
          baseURI == null ? LDConfig.DEFAULT_BASE_URI : baseURI,
          true
          );
      return new PollingProcessor(requestor, featureStore, pollIntervalMillis);
    }

    @Override
    public LDValue describeConfiguration(LDConfig config) {
      if (config.offline) {
        return nullUpdateProcessorFactory.describeConfiguration(config);
      }
      return LDValue.buildObject()
          .put(ConfigProperty.STREAMING_DISABLED.name, true)
          .put(ConfigProperty.CUSTOM_BASE_URI.name,
              baseURI != null && !baseURI.equals(LDConfig.DEFAULT_BASE_URI))
          .put(ConfigProperty.CUSTOM_STREAM_URI.name, false)
          .put(ConfigProperty.POLLING_INTERVAL_MILLIS.name, pollIntervalMillis)
          .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
          .build();
    }
  }
  
  private static final class EventProcessorBuilderImpl extends EventProcessorBuilder
      implements EventProcessorFactoryWithDiagnostics, DiagnosticDescription {
    @Override
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
      return createEventProcessor(sdkKey, config, null);
    }
    
    @Override
    public EventProcessor createEventProcessor(String sdkKey, LDConfig config, DiagnosticAccumulator diagnosticAccumulator) {
      if (config.offline) {
        return new NullEventProcessor();
      }
      return new DefaultEventProcessor(sdkKey,
          config,
          new EventsConfiguration(
              allAttributesPrivate,
              capacity,
              baseURI == null ? LDConfig.DEFAULT_EVENTS_URI : baseURI,
              flushIntervalSeconds,
              inlineUsersInEvents,
              privateAttrNames,
              0, // deprecated samplingInterval isn't supported in new builder
              userKeysCapacity,
              userKeysFlushIntervalSeconds,
              diagnosticRecordingIntervalSeconds
              ),
          config.httpConfig,
          diagnosticAccumulator
          );
    }
    
    @Override
    public LDValue describeConfiguration(LDConfig config) {
      return LDValue.buildObject()
          .put(ConfigProperty.ALL_ATTRIBUTES_PRIVATE.name, allAttributesPrivate)
          .put(ConfigProperty.CUSTOM_EVENTS_URI.name, baseURI != null && !baseURI.equals(LDConfig.DEFAULT_EVENTS_URI))
          .put(ConfigProperty.DIAGNOSTIC_RECORDING_INTERVAL_MILLIS.name, diagnosticRecordingIntervalSeconds * 1000)
          .put(ConfigProperty.EVENTS_CAPACITY.name, capacity)
          .put(ConfigProperty.EVENTS_FLUSH_INTERVAL_MILLIS.name, flushIntervalSeconds * 1000)
          .put(ConfigProperty.INLINE_USERS_IN_EVENTS.name, inlineUsersInEvents)
          .put(ConfigProperty.SAMPLING_INTERVAL.name, 0)
          .put(ConfigProperty.USER_KEYS_CAPACITY.name, userKeysCapacity)
          .put(ConfigProperty.USER_KEYS_FLUSH_INTERVAL_MILLIS.name, userKeysFlushIntervalSeconds * 1000)
          .build();
    }
  }
  
  private static final class HttpConfigurationBuilderImpl extends HttpConfigurationBuilder {
    @Override
    public HttpConfiguration createHttpConfiguration() {
      return new HttpConfigurationImpl(
          connectTimeoutMillis,
          proxyHost == null ? null : new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)),
          proxyAuth,
          socketTimeoutMillis,
          sslSocketFactory,
          trustManager,
          wrapperName == null ? null : (wrapperVersion == null ? wrapperName : (wrapperName + "/" + wrapperVersion))
      );
    }
  }
  
  private static final class HttpBasicAuthentication implements HttpAuthentication {
    private final String username;
    private final String password;
    
    HttpBasicAuthentication(String username, String password) {
      this.username = username;
      this.password = password;
    }

    @Override
    public String provideAuthorization(Iterable<Challenge> challenges) {
      return Credentials.basic(username, password);
    }
  }
  
  private static final class PersistentDataStoreBuilderImpl extends PersistentDataStoreBuilder implements DiagnosticDescription {
    public PersistentDataStoreBuilderImpl(PersistentDataStoreFactory persistentDataStoreFactory) {
      super(persistentDataStoreFactory);
    }

    @Override
    public FeatureStore createFeatureStore() {
      FeatureStoreCore core = persistentDataStoreFactory.createPersistentDataStore();
      return CachingStoreWrapper.builder(core)
          .caching(caching)
          .cacheMonitor(cacheMonitor)
          .build();
    }

    @Override
    public LDValue describeConfiguration(LDConfig config) {
      if (persistentDataStoreFactory instanceof DiagnosticDescription) {
        return ((DiagnosticDescription)persistentDataStoreFactory).describeConfiguration(config);
      }
      return LDValue.of("?");
    }
  }
}
