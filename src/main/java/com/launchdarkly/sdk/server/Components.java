package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.server.ComponentsImpl.EventProcessorBuilderImpl;
import com.launchdarkly.sdk.server.ComponentsImpl.HooksConfigurationBuilderImpl;
import com.launchdarkly.sdk.server.ComponentsImpl.HttpBasicAuthentication;
import com.launchdarkly.sdk.server.ComponentsImpl.HttpConfigurationBuilderImpl;
import com.launchdarkly.sdk.server.ComponentsImpl.InMemoryDataStoreFactory;
import com.launchdarkly.sdk.server.ComponentsImpl.LoggingConfigurationBuilderImpl;
import com.launchdarkly.sdk.server.ComponentsImpl.NullDataSourceFactory;
import com.launchdarkly.sdk.server.ComponentsImpl.PersistentDataStoreBuilderImpl;
import com.launchdarkly.sdk.server.ComponentsImpl.PollingDataSourceBuilderImpl;
import com.launchdarkly.sdk.server.ComponentsImpl.ServiceEndpointsBuilderImpl;
import com.launchdarkly.sdk.server.ComponentsImpl.StreamingDataSourceBuilderImpl;
import com.launchdarkly.sdk.server.ComponentsImpl.WrapperInfoBuilderImpl;
import com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder;
import com.launchdarkly.sdk.server.integrations.BigSegmentsConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.integrations.HooksConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder;
import com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.sdk.server.integrations.WrapperInfoBuilder;
import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;

import static com.launchdarkly.sdk.server.ComponentsImpl.NOOP_EVENT_PROCESSOR_FACTORY;

/**
 * Provides configurable factories for the standard implementations of LaunchDarkly component interfaces.
 * <p>
 * Some of the configuration options in {@link LDConfig.Builder} affect the entire SDK, but others are
 * specific to one area of functionality, such as how the SDK receives feature flag updates or processes
 * analytics events. For the latter, the standard way to specify a configuration is to call one of the
 * static methods in {@link Components} (such as {@link #streamingDataSource()}), apply any desired
 * configuration change to the object that that method returns (such as {@link StreamingDataSourceBuilder#initialReconnectDelay(java.time.Duration)},
 * and then use the corresponding method in {@link LDConfig.Builder} (such as {@link LDConfig.Builder#dataSource(ComponentConfigurer)})
 * to use that configured component in the SDK.
 * 
 * @since 4.0.0
 */
public abstract class Components {
  private Components() {}

  /**
   * Returns a configuration builder for the SDK's Big Segments feature.
   * <p>
   * Big Segments are a specific type of user segments. For more information, read the
   * <a href="https://docs.launchdarkly.com/home/users/big-segments">LaunchDarkly documentation</a>.
   * <p>
   * After configuring this object, use
   * {@link LDConfig.Builder#bigSegments(ComponentConfigurer)} to store it in your SDK
   * configuration. For example, using the Redis integration:
   *
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .bigSegments(Components.bigSegments(Redis.dataStore().prefix("app1"))
   *             .userCacheSize(2000))
   *         .build();
   * </code></pre>
   *
   * <p>
   * You must always specify the {@code storeFactory} parameter, to tell the SDK what database you
   * are using. Several database integrations exist for the LaunchDarkly SDK, each with its own
   * behavior and options specific to that database; this is described via some implementation of
   * {@link BigSegmentStore}. The {@link BigSegmentsConfigurationBuilder} adds configuration
   * options for aspects of SDK behavior that are independent of the database. In the example above,
   * {@code prefix} is an option specifically for the Redis integration, whereas
   * {@code userCacheSize} is an option that can be used for any data store type.
   *
   * @param storeConfigurer the factory for the underlying data store
   * @return a {@link BigSegmentsConfigurationBuilder}
   * @since 5.7.0
   * @see Components#bigSegments(ComponentConfigurer)
   */
  public static BigSegmentsConfigurationBuilder bigSegments(ComponentConfigurer<BigSegmentStore> storeConfigurer) {
    return new BigSegmentsConfigurationBuilder(storeConfigurer);
  }

  /**
   * Returns a configuration object for using the default in-memory implementation of a data store.
   * <p>
   * Since it is the default, you do not normally need to call this method, unless you need to create
   * a data store instance for testing purposes.
   * 
   * @return a factory object
   * @see LDConfig.Builder#dataStore(ComponentConfigurer)
   * @since 4.12.0
   */
  public static ComponentConfigurer<DataStore> inMemoryDataStore() {
    return InMemoryDataStoreFactory.INSTANCE;
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
   * <p>
   * For more information on the available persistent data store implementations, see the reference
   * guide on <a href="https://docs.launchdarkly.com/sdk/concepts/data-stores">Using a persistent feature store</a>.
   *  
   * @param storeConfigurer the factory/builder for the specific kind of persistent data store
   * @return a {@link PersistentDataStoreBuilder}
   * @see LDConfig.Builder#dataStore(ComponentConfigurer)
   * @since 4.12.0
   */
  public static PersistentDataStoreBuilder persistentDataStore(ComponentConfigurer<PersistentDataStore> storeConfigurer) {
    return new PersistentDataStoreBuilderImpl(storeConfigurer);
  }
  
  /**
   * Returns a configuration builder for analytics event delivery.
   * <p>
   * The default configuration has events enabled with default settings. If you want to
   * customize this behavior, call this method to obtain a builder, change its properties
   * with the {@link EventProcessorBuilder} properties, and pass it to {@link LDConfig.Builder#events(ComponentConfigurer)}:
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .events(Components.sendEvents().capacity(5000).flushIntervalSeconds(2))
   *         .build();
   * </code></pre>
   * To completely disable sending analytics events, use {@link #noEvents()} instead.
   * <p>
   * Setting {@link LDConfig.Builder#offline(boolean)} to {@code true} will supersede this setting and completely
   * disable network requests.
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
   * Returns a configuration object that disables analytics events.
   * <p>
   * Passing this to {@link LDConfig.Builder#events(ComponentConfigurer)} causes the SDK
   * to discard all analytics events and not send them to LaunchDarkly, regardless of any other configuration.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .events(Components.noEvents())
   *         .build();
   * </code></pre>
   * 
   * @return a factory object
   * @see #sendEvents()
   * @see LDConfig.Builder#events(ComponentConfigurer)
   * @since 4.12.0
   */
  public static ComponentConfigurer<EventProcessor> noEvents() {
    return NOOP_EVENT_PROCESSOR_FACTORY;
  }

  /**
   * Returns a configurable factory for using streaming mode to get feature flag data.
   * <p>
   * By default, the SDK uses a streaming connection to receive feature flag data from LaunchDarkly. To use the
   * default behavior, you do not need to call this method. However, if you want to customize the behavior of
   * the connection, call this method to obtain a builder, change its properties with the
   * {@link StreamingDataSourceBuilder} methods, and pass it to {@link LDConfig.Builder#dataSource(ComponentConfigurer)}:
   * <pre><code> 
   *     LDConfig config = new LDConfig.Builder()
   *         .dataSource(Components.streamingDataSource().initialReconnectDelayMillis(500))
   *         .build();
   * </code></pre>
   * <p>
   * Setting {@link LDConfig.Builder#offline(boolean)} to {@code true} will supersede this setting and completely
   * disable network requests.
   * 
   * @return a builder for setting streaming connection properties
   * @see LDConfig.Builder#dataSource(ComponentConfigurer)
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
   * {@link PollingDataSourceBuilder} methods, and pass it to {@link LDConfig.Builder#dataSource(ComponentConfigurer)}:
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .dataSource(Components.pollingDataSource().pollIntervalMillis(45000))
   *         .build();
   * </code></pre>
   * <p>
   * Setting {@link LDConfig.Builder#offline(boolean)} to {@code true} will supersede this setting and completely
   * disable network requests.
   * 
   * @return a builder for setting polling properties
   * @see LDConfig.Builder#dataSource(ComponentConfigurer)
   * @since 4.12.0
   */
  public static PollingDataSourceBuilder pollingDataSource() {
    return new PollingDataSourceBuilderImpl();
  }
  
  // For testing only - allows us to override the minimum polling interval
  static PollingDataSourceBuilderImpl pollingDataSourceInternal() {
    return new PollingDataSourceBuilderImpl();
  }
  
  /**
   * Returns a configuration object that disables a direct connection with LaunchDarkly for feature flag updates.
   * <p>
   * Passing this to {@link LDConfig.Builder#dataSource(ComponentConfigurer)} causes the SDK
   * not to retrieve feature flag data from LaunchDarkly, regardless of any other configuration.
   * This is normally done if you are using the <a href="https://docs.launchdarkly.com/home/relay-proxy">Relay Proxy</a>
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
   * 
   * @return a factory object
   * @since 4.12.0
   * @see LDConfig.Builder#dataSource(ComponentConfigurer)
   */
  public static ComponentConfigurer<DataSource> externalUpdatesOnly() {
    return NullDataSourceFactory.INSTANCE;
  }

  /**
   * Returns a configuration builder for the SDK's networking configuration.
   * <p>
   * Passing this to {@link LDConfig.Builder#http(ComponentConfigurer)}
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
   * 
   * @return a factory object
   * @since 4.13.0
   * @see LDConfig.Builder#http(ComponentConfigurer)
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

  /**
   * Returns a configuration builder for the SDK's logging configuration.
   * <p>
   * Passing this to {@link LDConfig.Builder#logging(ComponentConfigurer)},
   * after setting any desired properties on the builder, applies this configuration to the SDK.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .logging(
   *              Components.logging()
   *                  .logDataSourceOutageAsErrorAfter(Duration.ofSeconds(120))
   *         )
   *         .build();
   * </code></pre>
   * 
   * @return a configuration builder
   * @since 5.0.0
   * @see LDConfig.Builder#logging(ComponentConfigurer)
   */
  public static LoggingConfigurationBuilder logging() {
    return new LoggingConfigurationBuilderImpl();
  }

  /**
   * Returns a configuration builder for the SDK's logging configuration, specifying the
   * implementation of logging to use.
   * <p>
   * This is a shortcut for <code>Components.logging().adapter(logAdapter)</code>. The
   * <a href="https://github.com/launchdarkly/java-logging"><code>com.launchdarkly.logging</code></a>
   * API defines the {@link LDLogAdapter} interface to specify where log output should be sent.
   * <p>
   * The default logging destination, if no adapter is specified, depends on whether
   * <a href="https://www.slf4j.org/">SLF4J</a> is present in the classpath. If it is, then the SDK uses
   * {@link com.launchdarkly.logging.LDSLF4J#adapter()}, causing output to go to SLF4J; what happens to
   * the output then is determined by the SLF4J configuration. If SLF4J is not present in the classpath,
   * the SDK uses {@link Logs#toConsole()} instead, causing output to go to the {@code System.err} stream.
   * <p>
   * You may use the {@link com.launchdarkly.logging.Logs} factory methods, or a custom implementation,
   * to handle log output differently. For instance, you may specify
   * {@link com.launchdarkly.logging.Logs#toJavaUtilLogging()} to use the <code>java.util.logging</code>
   * framework.
   * <p>
   * Passing this to {@link LDConfig.Builder#logging(ComponentConfigurer)},
   * after setting any desired properties on the builder, applies this configuration to the SDK.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .logging(
   *              Components.logging(Logs.basic())
   *         )
   *         .build();
   * </code></pre>
   * 
   * @param logAdapter the log adapter
   * @return a configuration builder
   * @since 5.10.0
   * @see LDConfig.Builder#logging(ComponentConfigurer)
   * @see LoggingConfigurationBuilder#adapter(LDLogAdapter)
   */
  public static LoggingConfigurationBuilder logging(LDLogAdapter logAdapter) {
    return logging().adapter(logAdapter);
  }
  
  /**
   * Returns a configuration builder that turns off SDK logging.
   * <p>
   * Passing this to {@link LDConfig.Builder#logging(ComponentConfigurer)}
   * applies this configuration to the SDK.
   * <p>
   * It is equivalent to <code>Components.logging(com.launchdarkly.logging.Logs.none())</code>.
   * 
   * @return a configuration builder
   * @since 5.10.0
   */
  public static LoggingConfigurationBuilder noLogging() {
    return logging().adapter(Logs.none());
  }
  
  /**
   * Returns a configuration builder for the SDK's application metadata.
   * <p>
   * Passing this to {@link LDConfig.Builder#applicationInfo(com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder)},
   * after setting any desired properties on the builder, applies this configuration to the SDK.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .applicationInfo(
   *             Components.applicationInfo()
   *                 .applicationId("authentication-service")
   *                 .applicationVersion("1.0.0")
   *         )
   *         .build();
   * </code></pre>
   *
   * @return a builder object
   * @since 5.8.0
   * @see LDConfig.Builder#applicationInfo(com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder)
   */
  public static ApplicationInfoBuilder applicationInfo() {
    return new ApplicationInfoBuilder();
  }

  /**
   * Returns a builder for configuring custom service URIs.
   * <p>
   * Passing this to {@link LDConfig.Builder#serviceEndpoints(com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder)},
   * after setting any desired properties on the builder, applies this configuration to the SDK.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .serviceEndpoints(
   *             Components.serviceEndpoints()
   *                 .relayProxy("http://my-relay-hostname:80")
   *         )
   *         .build();
   * </code></pre>
   * 
   * @return a builder object
   * @since 5.9.0
   * @see LDConfig.Builder#serviceEndpoints(com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder)
   */
  public static ServiceEndpointsBuilder serviceEndpoints() {
    return new ServiceEndpointsBuilderImpl();
  }

  /**
   * Returns a builder for configuring hooks.
   *
   * Passing this to {@link LDConfig.Builder#hooks(com.launchdarkly.sdk.server.integrations.HooksConfigurationBuilder)},
   * after setting any desired hooks on the builder, applies this configuration to the SDK.
   * <pre><code>
   *     List hooks = myCreateHooksFunc();
   *     LDConfig config = new LDConfig.Builder()
   *         .hooks(
   *             Components.hooks()
   *                 .setHooks(hooks)
   *         )
   *         .build();
   * </code></pre>
   * @return a {@link HooksConfigurationBuilder} that can be used for customization
   */
  public static HooksConfigurationBuilder hooks() {
    return new HooksConfigurationBuilderImpl();
  }

  /**
   * Returns a wrapper information builder.
   * <p>
   * This is intended for use by LaunchDarkly in the development of wrapper SDKs.
   *
   * @return a builder object
   * @since 7.1.0
   */
  public static WrapperInfoBuilder wrapperInfo() { return new WrapperInfoBuilderImpl(); }
}
