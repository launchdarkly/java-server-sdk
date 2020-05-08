package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DiagnosticEvent.ConfigProperty;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;
import com.launchdarkly.sdk.server.interfaces.DiagnosticDescription;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeListener;
import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStore;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStoreFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import okhttp3.Credentials;

/**
 * Provides configurable factories for the standard implementations of LaunchDarkly component interfaces.
 * <p>
 * Some of the configuration options in {@link LDConfig.Builder} affect the entire SDK, but others are
 * specific to one area of functionality, such as how the SDK receives feature flag updates or processes
 * analytics events. For the latter, the standard way to specify a configuration is to call one of the
 * static methods in {@link Components} (such as {@link #streamingDataSource()}), apply any desired
 * configuration change to the object that that method returns (such as {@link StreamingDataSourceBuilder#initialReconnectDelay(java.time.Duration)},
 * and then use the corresponding method in {@link LDConfig.Builder} (such as {@link LDConfig.Builder#dataSource(DataSourceFactory)})
 * to use that configured component in the SDK.
 * 
 * @since 4.0.0
 */
public abstract class Components {
  /**
   * Returns a configuration object for using the default in-memory implementation of a data store.
   * <p>
   * Since it is the default, you do not normally need to call this method, unless you need to create
   * a data store instance for testing purposes.
   * 
   * @return a factory object
   * @see LDConfig.Builder#dataStore(DataStoreFactory)
   * @since 4.12.0
   */
  public static DataStoreFactory inMemoryDataStore() {
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
   * guide on <a href="https://docs.launchdarkly.com/sdk/concepts/feature-store">Using a persistent feature store</a>.
   *  
   * @param storeFactory the factory/builder for the specific kind of persistent data store
   * @return a {@link PersistentDataStoreBuilder}
   * @see LDConfig.Builder#dataStore(DataStoreFactory)
   * @since 4.12.0
   */
  public static PersistentDataStoreBuilder persistentDataStore(PersistentDataStoreFactory storeFactory) {
    return new PersistentDataStoreBuilderImpl(storeFactory);
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
    return NULL_EVENT_PROCESSOR_FACTORY;
  }

  /**
   * Returns a configurable factory for using streaming mode to get feature flag data.
   * <p>
   * By default, the SDK uses a streaming connection to receive feature flag data from LaunchDarkly. To use the
   * default behavior, you do not need to call this method. However, if you want to customize the behavior of
   * the connection, call this method to obtain a builder, change its properties with the
   * {@link StreamingDataSourceBuilder} methods, and pass it to {@link LDConfig.Builder#dataSource(DataSourceFactory)}:
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
   * @see LDConfig.Builder#dataSource(DataSourceFactory)
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
   * {@link PollingDataSourceBuilder} methods, and pass it to {@link LDConfig.Builder#dataSource(DataSourceFactory)}:
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
   * @see LDConfig.Builder#dataSource(DataSourceFactory)
   * @since 4.12.0
   */
  public static PollingDataSourceBuilder pollingDataSource() {
    return new PollingDataSourceBuilderImpl();
  }
  
  /**
   * Returns a configuration object that disables a direct connection with LaunchDarkly for feature flag updates.
   * <p>
   * Passing this to {@link LDConfig.Builder#dataSource(DataSourceFactory)} causes the SDK
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
   * (Note that the interface is still named {@link DataSourceFactory}, but in a future version it
   * will be renamed to {@code DataSourceFactory}.)
   * 
   * @return a factory object
   * @since 4.12.0
   * @see LDConfig.Builder#dataSource(DataSourceFactory)
   */
  public static DataSourceFactory externalUpdatesOnly() {
    return NullDataSourceFactory.INSTANCE;
  }

  /**
   * Returns a configurable factory for the SDK's networking configuration.
   * <p>
   * Passing this to {@link LDConfig.Builder#http(com.launchdarkly.sdk.server.interfaces.HttpConfigurationFactory)}
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
   * @see LDConfig.Builder#http(com.launchdarkly.sdk.server.interfaces.HttpConfigurationFactory)
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
   * Convenience method for creating a {@link FlagChangeListener} that tracks a flag's value for a specific user.
   * <p>
   * This listener instance should only be used with a single {@link LDClient} instance. When you first
   * register it by calling {@link LDClientInterface#registerFlagChangeListener(FlagChangeListener)}, it
   * immediately evaluates the flag. It then re-evaluates the flag whenever there is an update, and calls
   * your {@link FlagValueChangeListener} if and only if the resulting value has changed.
   * <p>
   * See {@link FlagValueChangeListener} for more information and examples.
   * 
   * @param client the same client instance that you will be registering this listener with
   * @param flagKey the flag key to be evaluated
   * @param user the user properties for evaluation
   * @param valueChangeListener an object that you provide which will be notified of changes
   * @return a {@link FlagChangeListener} to be passed to {@link LDClientInterface#registerFlagChangeListener(FlagChangeListener)}
   * 
   * @since 5.0.0
   * @see FlagValueChangeListener
   * @see FlagChangeListener
   */
  public static FlagChangeListener flagValueMonitoringListener(LDClientInterface client, String flagKey, LDUser user,
      FlagValueChangeListener valueChangeListener) {
    return new FlagValueMonitoringListener(client, flagKey, user, valueChangeListener);
  }
  
  private static final class InMemoryDataStoreFactory implements DataStoreFactory, DiagnosticDescription {
    static final DataStoreFactory INSTANCE = new InMemoryDataStoreFactory();
    @Override
    public DataStore createDataStore(ClientContext context, DataStoreUpdates dataStoreUpdates) {
      return new InMemoryDataStore();
    }

    @Override
    public LDValue describeConfiguration(LDConfig config) {
      return LDValue.of("memory");
    }
  }
  
  private static final EventProcessorFactory NULL_EVENT_PROCESSOR_FACTORY = context -> NullEventProcessor.INSTANCE;
  
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
  
  private static final class NullDataSourceFactory implements DataSourceFactory, DiagnosticDescription {
    static final NullDataSourceFactory INSTANCE = new NullDataSourceFactory();
    
    @Override
    public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
      if (context.isOffline()) {
        // If they have explicitly called offline(true) to disable everything, we'll log this slightly
        // more specific message.
        LDClient.logger.info("Starting LaunchDarkly client in offline mode");
      } else {
        LDClient.logger.info("LaunchDarkly client will not connect to Launchdarkly for feature flag data");
      }
      return NullDataSource.INSTANCE;
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
  static final class NullDataSource implements DataSource {
    static final DataSource INSTANCE = new NullDataSource();
    @Override
    public Future<Void> start() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isInitialized() {
      return true;
    }
    
    @Override
    public void close() throws IOException {}
  }
  
  private static final class StreamingDataSourceBuilderImpl extends StreamingDataSourceBuilder
      implements DiagnosticDescription {
    @Override
    public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      if (context.isOffline()) {
        return Components.externalUpdatesOnly().createDataSource(context, dataSourceUpdates);
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
          context.getSdkKey(),
          context.getHttpConfiguration(),
          pollUri,
          false
          );
      
      return new StreamProcessor(
          context.getSdkKey(),
          context.getHttpConfiguration(),
          requestor,
          dataSourceUpdates,
          null,
          ClientContextImpl.get(context).diagnosticAccumulator,
          streamUri,
          initialReconnectDelay
          );
    }

    @Override
    public LDValue describeConfiguration(LDConfig config) {
      if (config.offline) {
        return NullDataSourceFactory.INSTANCE.describeConfiguration(config);
      }
      return LDValue.buildObject()
          .put(ConfigProperty.STREAMING_DISABLED.name, false)
          .put(ConfigProperty.CUSTOM_BASE_URI.name,
              (pollingBaseURI != null && !pollingBaseURI.equals(LDConfig.DEFAULT_BASE_URI)) ||
              (pollingBaseURI == null && baseURI != null && !baseURI.equals(LDConfig.DEFAULT_STREAM_URI)))
          .put(ConfigProperty.CUSTOM_STREAM_URI.name,
              baseURI != null && !baseURI.equals(LDConfig.DEFAULT_STREAM_URI))
          .put(ConfigProperty.RECONNECT_TIME_MILLIS.name, initialReconnectDelay.toMillis())
          .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
          .build();
    }
  }
  
  private static final class PollingDataSourceBuilderImpl extends PollingDataSourceBuilder implements DiagnosticDescription {
    @Override
    public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      if (context.isOffline()) {
        return Components.externalUpdatesOnly().createDataSource(context, dataSourceUpdates);
      }

      LDClient.logger.info("Disabling streaming API");
      LDClient.logger.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");
      
      DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(
          context.getSdkKey(),
          context.getHttpConfiguration(),
          baseURI == null ? LDConfig.DEFAULT_BASE_URI : baseURI,
          true
          );
      return new PollingProcessor(
          requestor,
          dataSourceUpdates,
          ClientContextImpl.get(context).sharedExecutor,
          pollInterval
          );
    }

    @Override
    public LDValue describeConfiguration(LDConfig config) {
      if (config.offline) {
        return NullDataSourceFactory.INSTANCE.describeConfiguration(config);
      }
      return LDValue.buildObject()
          .put(ConfigProperty.STREAMING_DISABLED.name, true)
          .put(ConfigProperty.CUSTOM_BASE_URI.name,
              baseURI != null && !baseURI.equals(LDConfig.DEFAULT_BASE_URI))
          .put(ConfigProperty.CUSTOM_STREAM_URI.name, false)
          .put(ConfigProperty.POLLING_INTERVAL_MILLIS.name, pollInterval.toMillis())
          .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
          .build();
    }
  }
  
  private static final class EventProcessorBuilderImpl extends EventProcessorBuilder
      implements DiagnosticDescription {
    @Override
    public EventProcessor createEventProcessor(ClientContext context) {
      if (context.isOffline()) {
        return new NullEventProcessor();
      }
      return new DefaultEventProcessor(
          context.getSdkKey(),
          new EventsConfiguration(
              allAttributesPrivate,
              capacity,
              baseURI == null ? LDConfig.DEFAULT_EVENTS_URI : baseURI,
              flushInterval,
              inlineUsersInEvents,
              privateAttributes,
              0, // deprecated samplingInterval isn't supported in new builder
              userKeysCapacity,
              userKeysFlushInterval,
              diagnosticRecordingInterval
              ),
          context.getHttpConfiguration(),
          ClientContextImpl.get(context).sharedExecutor,
          ClientContextImpl.get(context).diagnosticAccumulator,
          ClientContextImpl.get(context).diagnosticInitEvent
          );
    }
    
    @Override
    public LDValue describeConfiguration(LDConfig config) {
      return LDValue.buildObject()
          .put(ConfigProperty.ALL_ATTRIBUTES_PRIVATE.name, allAttributesPrivate)
          .put(ConfigProperty.CUSTOM_EVENTS_URI.name, baseURI != null && !baseURI.equals(LDConfig.DEFAULT_EVENTS_URI))
          .put(ConfigProperty.DIAGNOSTIC_RECORDING_INTERVAL_MILLIS.name, diagnosticRecordingInterval.toMillis())
          .put(ConfigProperty.EVENTS_CAPACITY.name, capacity)
          .put(ConfigProperty.EVENTS_FLUSH_INTERVAL_MILLIS.name, flushInterval.toMillis())
          .put(ConfigProperty.INLINE_USERS_IN_EVENTS.name, inlineUsersInEvents)
          .put(ConfigProperty.SAMPLING_INTERVAL.name, 0)
          .put(ConfigProperty.USER_KEYS_CAPACITY.name, userKeysCapacity)
          .put(ConfigProperty.USER_KEYS_FLUSH_INTERVAL_MILLIS.name, userKeysFlushInterval.toMillis())
          .build();
    }
  }

  private static final class HttpConfigurationBuilderImpl extends HttpConfigurationBuilder {
    @Override
    public HttpConfiguration createHttpConfiguration() {
      return new HttpConfigurationImpl(
          connectTimeout,
          proxyHost == null ? null : new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)),
          proxyAuth,
          socketTimeout,
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
    public LDValue describeConfiguration(LDConfig config) {
      if (persistentDataStoreFactory instanceof DiagnosticDescription) {
        return ((DiagnosticDescription)persistentDataStoreFactory).describeConfiguration(config);
      }
      return LDValue.of("custom");
    }
    
    /**
     * Called by the SDK to create the data store instance.
     */
    @Override
    public DataStore createDataStore(ClientContext context, DataStoreUpdates dataStoreUpdates) {
      PersistentDataStore core = persistentDataStoreFactory.createPersistentDataStore(context);
      return new PersistentDataStoreWrapper(
          core,
          cacheTime,
          staleValuesPolicy,
          recordCacheStats,
          dataStoreUpdates,
          ClientContextImpl.get(context).sharedExecutor
          );
    }
  }
}
