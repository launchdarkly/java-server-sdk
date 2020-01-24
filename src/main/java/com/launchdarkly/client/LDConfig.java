package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.launchdarkly.client.integrations.PollingDataSourceBuilder;
import com.launchdarkly.client.integrations.StreamingDataSourceBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * This class exposes advanced configuration options for the {@link LDClient}. Instances of this class must be constructed with a {@link com.launchdarkly.client.LDConfig.Builder}.
 */
public final class LDConfig {
  private static final Logger logger = LoggerFactory.getLogger(LDConfig.class);
  final Gson gson = new GsonBuilder().registerTypeAdapter(LDUser.class, new LDUser.UserAdapterWithPrivateAttributeBehavior(this)).create();

  static final URI DEFAULT_BASE_URI = URI.create("https://app.launchdarkly.com");
  static final URI DEFAULT_EVENTS_URI = URI.create("https://events.launchdarkly.com");
  static final URI DEFAULT_STREAM_URI = URI.create("https://stream.launchdarkly.com");
  private static final int DEFAULT_CAPACITY = 10000;
  private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
  private static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 10000;
  private static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 5;
  private static final long MIN_POLLING_INTERVAL_MILLIS = PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL_MILLIS;
  private static final long DEFAULT_START_WAIT_MILLIS = 5000L;
  private static final int DEFAULT_SAMPLING_INTERVAL = 0;
  private static final int DEFAULT_USER_KEYS_CAPACITY = 1000;
  private static final int DEFAULT_USER_KEYS_FLUSH_INTERVAL_SECONDS = 60 * 5;
  private static final long DEFAULT_RECONNECT_TIME_MILLIS = StreamingDataSourceBuilder.DEFAULT_INITIAL_RECONNECT_DELAY_MILLIS;
  private static final int DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL_MILLIS = 900_000; // 15 minutes
  private static final int MIN_DIAGNOSTIC_RECORDING_INTERVAL_MILLIS = 60_000; // 1 minute

  protected static final LDConfig DEFAULT = new Builder().build();

  final URI deprecatedBaseURI;
  final URI eventsURI;
  final URI deprecatedStreamURI;
  final int capacity;
  final int flushInterval;
  final Proxy proxy;
  final Authenticator proxyAuthenticator;
  final boolean stream;
  final FeatureStore deprecatedFeatureStore;
  final FeatureStoreFactory dataStoreFactory;
  final EventProcessorFactory eventProcessorFactory;
  final UpdateProcessorFactory dataSourceFactory;
  final boolean offline;
  final boolean allAttributesPrivate;
  final Set<String> privateAttrNames;
  final boolean sendEvents;
  final long deprecatedPollingIntervalMillis;
  final long startWaitMillis;
  final int samplingInterval;
  final long deprecatedReconnectTimeMs;
  final int userKeysCapacity;
  final int userKeysFlushInterval;
  final boolean inlineUsersInEvents;
  final int diagnosticRecordingIntervalMillis;
  final boolean diagnosticOptOut;
  final String wrapperName;
  final String wrapperVersion;
  final SSLSocketFactory sslSocketFactory;
  final X509TrustManager trustManager;
  final int connectTimeout;
  final TimeUnit connectTimeoutUnit;
  final int socketTimeout;
  final TimeUnit socketTimeoutUnit;

  protected LDConfig(Builder builder) {
    this.deprecatedBaseURI = builder.baseURI;
    this.eventsURI = builder.eventsURI;
    this.capacity = builder.capacity;
    this.flushInterval = builder.flushIntervalSeconds;
    this.proxy = builder.proxy();
    this.proxyAuthenticator = builder.proxyAuthenticator();
    this.deprecatedStreamURI = builder.streamURI;
    this.stream = builder.stream;
    this.deprecatedFeatureStore = builder.featureStore;
    this.dataStoreFactory = builder.dataStoreFactory;
    this.eventProcessorFactory = builder.eventProcessorFactory;
    this.dataSourceFactory = builder.dataSourceFactory;
    this.offline = builder.offline;
    this.allAttributesPrivate = builder.allAttributesPrivate;
    this.privateAttrNames = new HashSet<>(builder.privateAttrNames);
    this.sendEvents = builder.sendEvents;
    if (builder.pollingIntervalMillis < MIN_POLLING_INTERVAL_MILLIS) {
      this.deprecatedPollingIntervalMillis = MIN_POLLING_INTERVAL_MILLIS;
    } else {
      this.deprecatedPollingIntervalMillis = builder.pollingIntervalMillis;
    }
    this.startWaitMillis = builder.startWaitMillis;
    this.samplingInterval = builder.samplingInterval;
    this.deprecatedReconnectTimeMs = builder.reconnectTimeMillis;
    this.userKeysCapacity = builder.userKeysCapacity;
    this.userKeysFlushInterval = builder.userKeysFlushInterval;
    this.inlineUsersInEvents = builder.inlineUsersInEvents;
    this.sslSocketFactory = builder.sslSocketFactory;
    this.trustManager = builder.trustManager;
    this.connectTimeout = builder.connectTimeout;
    this.connectTimeoutUnit = builder.connectTimeoutUnit;
    this.socketTimeout = builder.socketTimeout;
    this.socketTimeoutUnit = builder.socketTimeoutUnit;

    if (builder.diagnosticRecordingIntervalMillis < MIN_DIAGNOSTIC_RECORDING_INTERVAL_MILLIS) {
      this.diagnosticRecordingIntervalMillis = MIN_DIAGNOSTIC_RECORDING_INTERVAL_MILLIS;
    } else {
      this.diagnosticRecordingIntervalMillis = builder.diagnosticRecordingIntervalMillis;
    }
    this.diagnosticOptOut = builder.diagnosticOptOut;
    this.wrapperName = builder.wrapperName;
    this.wrapperVersion = builder.wrapperVersion;
    
    if (proxy != null) {
      if (proxyAuthenticator != null) {
        logger.info("Using proxy: " + proxy + " with authentication.");
      } else {
        logger.info("Using proxy: " + proxy + " without authentication.");
      }
    }
  }

  LDConfig(LDConfig config) {
    this.deprecatedBaseURI = config.deprecatedBaseURI;
    this.eventsURI = config.eventsURI;
    this.deprecatedStreamURI = config.deprecatedStreamURI;
    this.capacity = config.capacity;
    this.flushInterval = config.flushInterval;
    this.proxy = config.proxy;
    this.proxyAuthenticator = config.proxyAuthenticator;
    this.stream = config.stream;
    this.deprecatedFeatureStore = config.deprecatedFeatureStore;
    this.dataSourceFactory = config.dataSourceFactory;
    this.dataStoreFactory = config.dataStoreFactory;
    this.eventProcessorFactory = config.eventProcessorFactory;
    this.offline = config.offline;
    this.allAttributesPrivate = config.allAttributesPrivate;
    this.privateAttrNames = config.privateAttrNames;
    this.sendEvents = config.sendEvents;
    this.deprecatedPollingIntervalMillis = config.deprecatedPollingIntervalMillis;
    this.startWaitMillis = config.startWaitMillis;
    this.samplingInterval = config.samplingInterval;
    this.deprecatedReconnectTimeMs = config.deprecatedReconnectTimeMs;
    this.userKeysCapacity = config.userKeysCapacity;
    this.userKeysFlushInterval = config.userKeysFlushInterval;
    this.inlineUsersInEvents = config.inlineUsersInEvents;
    this.sslSocketFactory = config.sslSocketFactory;
    this.trustManager = config.trustManager;
    this.connectTimeout = config.connectTimeout;
    this.connectTimeoutUnit = config.connectTimeoutUnit;
    this.socketTimeout = config.socketTimeout;
    this.socketTimeoutUnit = config.socketTimeoutUnit;
    this.diagnosticRecordingIntervalMillis = config.diagnosticRecordingIntervalMillis;
    this.diagnosticOptOut = config.diagnosticOptOut;
    this.wrapperName = config.wrapperName;
    this.wrapperVersion = config.wrapperVersion;
  }

  /**
   * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> that helps construct
   * {@link com.launchdarkly.client.LDConfig} objects. Builder calls can be chained, enabling the
   * following pattern:
   * <pre>
   * LDConfig config = new LDConfig.Builder()
   *      .connectTimeoutMillis(3)
   *      .socketTimeoutMillis(3)
   *      .build()
   * </pre>
   */
  public static class Builder {
    private URI baseURI = DEFAULT_BASE_URI;
    private URI eventsURI = DEFAULT_EVENTS_URI;
    private URI streamURI = DEFAULT_STREAM_URI;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    private TimeUnit connectTimeoutUnit = TimeUnit.MILLISECONDS;
    private int socketTimeout = DEFAULT_SOCKET_TIMEOUT_MILLIS;
    private TimeUnit socketTimeoutUnit = TimeUnit.MILLISECONDS;
    private int capacity = DEFAULT_CAPACITY;
    private int flushIntervalSeconds = DEFAULT_FLUSH_INTERVAL_SECONDS;
    private String proxyHost = "localhost";
    private int proxyPort = -1;
    private String proxyUsername = null;
    private String proxyPassword = null;
    private boolean stream = true;
    private boolean offline = false;
    private boolean allAttributesPrivate = false;
    private boolean sendEvents = true;
    private long pollingIntervalMillis = MIN_POLLING_INTERVAL_MILLIS;
    private FeatureStore featureStore = null;
    private FeatureStoreFactory dataStoreFactory = null;
    private EventProcessorFactory eventProcessorFactory = null;
    private UpdateProcessorFactory dataSourceFactory = null;
    private long startWaitMillis = DEFAULT_START_WAIT_MILLIS;
    private int samplingInterval = DEFAULT_SAMPLING_INTERVAL;
    private long reconnectTimeMillis = DEFAULT_RECONNECT_TIME_MILLIS;
    private Set<String> privateAttrNames = new HashSet<>();
    private int userKeysCapacity = DEFAULT_USER_KEYS_CAPACITY;
    private int userKeysFlushInterval = DEFAULT_USER_KEYS_FLUSH_INTERVAL_SECONDS;
    private boolean inlineUsersInEvents = false;
    private SSLSocketFactory sslSocketFactory = null;
    private X509TrustManager trustManager = null;
    private int diagnosticRecordingIntervalMillis = DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL_MILLIS;
    private boolean diagnosticOptOut = false;
    private String wrapperName = null;
    private String wrapperVersion = null;

    /**
     * Creates a builder with all configuration parameters set to the default
     */
    public Builder() {
    }

    /**
     * Deprecated method for setting the base URI for the polling service.
     * <p>
     * This method has no effect if you have used {@link #dataSource(UpdateProcessorFactory)} to
     * specify polling or streaming options, which is the preferred method.
     *
     * @param baseURI the base URL of the LaunchDarkly server for this configuration.
     * @return the builder
     * @deprecated Use {@link Components#streamingDataSource()} with {@link StreamingDataSourceBuilder#pollingBaseUri(URI)},
     * or {@link Components#pollingDataSource()} with {@link PollingDataSourceBuilder#baseUri(URI)}.
     */
    @Deprecated
    public Builder baseURI(URI baseURI) {
      this.baseURI = baseURI;
      return this;
    }

    /**
     * Set the base URL of the LaunchDarkly analytics event server for this configuration.
     *
     * @param eventsURI the events URL of the LaunchDarkly server for this configuration
     * @return the builder
     */
    public Builder eventsURI(URI eventsURI) {
      this.eventsURI = eventsURI;
      return this;
    }

    /**
     * Deprecated method for setting the base URI for the streaming service.
     * <p>
     * This method has no effect if you have used {@link #dataSource(UpdateProcessorFactory)} to
     * specify polling or streaming options, which is the preferred method.
     *
     * @param streamURI the base URL of the LaunchDarkly streaming server
     * @return the builder
     * @deprecated Use {@link Components#streamingDataSource()} with {@link StreamingDataSourceBuilder#pollingBaseUri(URI)}.
     */
    @Deprecated
    public Builder streamURI(URI streamURI) {
      this.streamURI = streamURI;
      return this;
    }

    /**
     * Sets the implementation of the data store to be used for holding feature flags and
     * related data received from LaunchDarkly, using a factory object. The default is
     * {@link Components#inMemoryDataStore()}; for database integrations, use
     * {@link Components#persistentDataStore(com.launchdarkly.client.interfaces.PersistentDataStoreFactory)}.
     * <p>
     * Note that the interface is still called {@link FeatureStoreFactory}, but in a future version
     * it will be renamed to {@code DataStoreFactory}.
     * 
     * @param factory the factory object
     * @return the builder
     * @since 4.12.0
     */
    public Builder dataStore(FeatureStoreFactory factory) {
      this.dataStoreFactory = factory;
      return this;
    }
    
    /**
     * Sets the implementation of {@link FeatureStore} to be used for holding feature flags and
     * related data received from LaunchDarkly. The default is {@link InMemoryFeatureStore}, but
     * you may use {@link RedisFeatureStore} or a custom implementation.
     * @param store the feature store implementation
     * @return the builder
     * @deprecated Please use {@link #featureStoreFactory(FeatureStoreFactory)}.
     */
    public Builder featureStore(FeatureStore store) {
      this.featureStore = store;
      return this;
    }

    /**
     * Deprecated name for {@link #dataStore(FeatureStoreFactory)}.
     * @param factory the factory object
     * @return the builder
     * @since 4.0.0
     * @deprecated Use {@link #dataStore(FeatureStoreFactory)}.
     */
    @Deprecated
    public Builder featureStoreFactory(FeatureStoreFactory factory) {
      this.dataStoreFactory = factory;
      return this;
    }

    /**
     * Sets the implementation of {@link EventProcessor} to be used for processing analytics events,
     * using a factory object. The default is {@link Components#defaultEventProcessor()}, but
     * you may choose to use a custom implementation (for instance, a test fixture).
     * @param factory the factory object
     * @return the builder
     * @since 4.12.0
     */
    public Builder eventProcessor(EventProcessorFactory factory) {
      this.eventProcessorFactory = factory;
      return this;
    }

    /**
     * Deprecated name for {@link #eventProcessor(EventProcessorFactory)}.
     * @param factory the factory object
     * @return the builder
     * @since 4.0.0
     * @deprecated Use {@link #eventProcessor(EventProcessorFactory)}.
     */
    public Builder eventProcessorFactory(EventProcessorFactory factory) {
      this.eventProcessorFactory = factory;
      return this;
    }
    
    /**
     * Sets the implementation of the component that receives feature flag data from LaunchDarkly,
     * using a factory object. Depending on the implementation, the factory may be a builder that
     * allows you to set other configuration options as well.
     * <p>
     * The default is {@link Components#streamingDataSource()}. You may instead use
     * {@link Components#pollingDataSource()}, or a test fixture such as
     * {@link com.launchdarkly.client.integrations.FileData#dataSource()}. See those methods
     * for details on how to configure them.
     * <p>
     * Note that the interface is still named {@link UpdateProcessorFactory}, but in a future version
     * it will be renamed to {@code DataSourceFactory}.
     * 
     * @param factory the factory object
     * @return the builder
     * @since 4.12.0
     */
    public Builder dataSource(UpdateProcessorFactory factory) {
      this.dataSourceFactory = factory;
      return this;
    }

    /**
     * Deprecated name for {@link #dataSource(UpdateProcessorFactory)}.
     * @param factory the factory object
     * @return the builder
     * @since 4.0.0
     * @deprecated Use {@link #dataSource(UpdateProcessorFactory)}.
     */
    @Deprecated
    public Builder updateProcessorFactory(UpdateProcessorFactory factory) {
      this.dataSourceFactory = factory;
      return this;
    }
    
    /**
     * Deprecated method for enabling or disabling streaming mode.
     * <p>
     * By default, streaming is enabled. It should only be disabled on the advice of LaunchDarkly support.
     * <p>
     * This method has no effect if you have specified a data source with {@link #dataSource(UpdateProcessorFactory)},
     * which is the preferred method.
     * 
     * @param stream whether streaming mode should be enabled
     * @return the builder
     * @deprecated Use {@link Components#streamingDataSource()} or {@link Components#pollingDataSource()}.
     */
    @Deprecated
    public Builder stream(boolean stream) {
      this.stream = stream;
      return this;
    }

    /**
     * Set the connection timeout in seconds for the configuration. This is the time allowed for the underlying HTTP client to connect
     * to the LaunchDarkly server. The default is 2 seconds.
     * <p>Both this method and {@link #connectTimeoutMillis(int) connectTimeoutMillis} affect the same property internally.</p>
     *
     * @param connectTimeout the connection timeout in seconds
     * @return the builder
     */
    public Builder connectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
      this.connectTimeoutUnit = TimeUnit.SECONDS;
      return this;
    }

    /**
     * Set the socket timeout in seconds for the configuration. This is the number of seconds between successive packets that the
     * client will tolerate before flagging an error. The default is 10 seconds.
     * <p>Both this method and {@link #socketTimeoutMillis(int) socketTimeoutMillis} affect the same property internally.</p>
     *
     * @param socketTimeout the socket timeout in seconds
     * @return the builder
     */
    public Builder socketTimeout(int socketTimeout) {
      this.socketTimeout = socketTimeout;
      this.socketTimeoutUnit = TimeUnit.SECONDS;
      return this;
    }

    /**
     * Set the connection timeout in milliseconds for the configuration. This is the time allowed for the underlying HTTP client to connect
     * to the LaunchDarkly server. The default is 2000 ms.
     * <p>Both this method and {@link #connectTimeout(int) connectTimeoutMillis} affect the same property internally.</p>
     *
     * @param connectTimeoutMillis the connection timeout in milliseconds
     * @return the builder
     */
    public Builder connectTimeoutMillis(int connectTimeoutMillis) {
      this.connectTimeout = connectTimeoutMillis;
      this.connectTimeoutUnit = TimeUnit.MILLISECONDS;
      return this;
    }

    /**
     * Set the socket timeout in milliseconds for the configuration. This is the number of milliseconds between successive packets that the
     * client will tolerate before flagging an error. The default is 10,000 milliseconds.
     * <p>Both this method and {@link #socketTimeout(int) socketTimeoutMillis} affect the same property internally.</p>
     *
     * @param socketTimeoutMillis the socket timeout in milliseconds
     * @return the builder
     */
    public Builder socketTimeoutMillis(int socketTimeoutMillis) {
      this.socketTimeout = socketTimeoutMillis;
      this.socketTimeoutUnit = TimeUnit.MILLISECONDS;
      return this;
    }

    /**
     * Set the number of seconds between flushes of the event buffer. Decreasing the flush interval means
     * that the event buffer is less likely to reach capacity. The default value is 5 seconds.
     *
     * @param flushInterval the flush interval in seconds
     * @return the builder
     */
    public Builder flushInterval(int flushInterval) {
      this.flushIntervalSeconds = flushInterval;
      return this;
    }

    /**
     * Set the capacity of the events buffer. The client buffers up to this many events in memory before flushing. If the capacity is exceeded before the buffer is flushed, events will be discarded.
     * Increasing the capacity means that events are less likely to be discarded, at the cost of consuming more memory. The default value is 10000 elements. The default flush interval (set by flushInterval) is 5 seconds.
     *
     * @param capacity the capacity of the event buffer
     * @return the builder
     */
    public Builder capacity(int capacity) {
      this.capacity = capacity;
      return this;
    }

    /**
     * Set the host to use as an HTTP proxy for making connections to LaunchDarkly. If this is not set, but
     * {@link #proxyPort(int)} is specified, this will default to <code>localhost</code>.
     * <p>
     * If neither {@link #proxyHost(String)} nor {@link #proxyPort(int)}  are specified,
     * a proxy will not be used, and {@link LDClient} will connect to LaunchDarkly directly.
     * </p>
     *
     * @param host the proxy hostname
     * @return the builder
     */
    public Builder proxyHost(String host) {
      this.proxyHost = host;
      return this;
    }

    /**
     * Set the port to use for an HTTP proxy for making connections to LaunchDarkly. This is required for proxied HTTP connections.
     *
     * @param port the proxy port
     * @return the builder
     */
    public Builder proxyPort(int port) {
      this.proxyPort = port;
      return this;
    }

    /**
     * Sets the username for the optional HTTP proxy. Only used when {@link LDConfig.Builder#proxyPassword(String)}
     * is also called.
     *
     * @param username the proxy username
     * @return the builder
     */
    public Builder proxyUsername(String username) {
      this.proxyUsername = username;
      return this;
    }

    /**
     * Sets the password for the optional HTTP proxy. Only used when {@link LDConfig.Builder#proxyUsername(String)}
     * is also called.
     *
     * @param password the proxy password
     * @return the builder
     */
    public Builder proxyPassword(String password) {
      this.proxyPassword = password;
      return this;
    }

    /**
     * Sets the {@link SSLSocketFactory} used to secure HTTPS connections to LaunchDarkly.
     *
     * @param sslSocketFactory the SSL socket factory
     * @param trustManager the trust manager
     * @return the builder
     * 
     * @since 4.7.0
     */
    public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
      this.sslSocketFactory = sslSocketFactory;
      this.trustManager = trustManager;
      return this;
    }

    /**
     * Deprecated method for using the LaunchDarkly Relay Proxy in daemon mode.
     * <p>
     * See {@link Components#externalUpdatesOnly()} for the preferred way to do this.
     * 
     * @param useLdd true to use the relay in daemon mode; false to use streaming or polling
     * @return the builder
     * @deprecated Use {@link Components#externalUpdatesOnly()}.
     */
    @Deprecated
    public Builder useLdd(boolean useLdd) {
      if (useLdd) {
        return dataSource(Components.externalUpdatesOnly());
      } else {
        return dataSource(null);
      }
    }

    /**
     * Set whether this client is offline.
     * <p>
     * In offline mode, the SDK will not make network connections to LaunchDarkly for any purpose. Feature
     * flag data will only be available if it already exists in the data store, and analytics events will
     * not be sent.
     * <p>
     * This is equivalent to calling {@code dataSource(Components.externalUpdatesOnly())} and
     * {@code sendEvents(false)}. It overrides any other values you may have set for
     * {@link #dataSource(UpdateProcessorFactory)} or {@link #eventProcessor(EventProcessorFactory)}.
     * 
     * @param offline when set to true no calls to LaunchDarkly will be made
     * @return the builder
     */
    public Builder offline(boolean offline) {
      this.offline = offline;
      return this;
    }

    /**
     * Set whether or not user attributes (other than the key) should be hidden from LaunchDarkly. If this is true, all
     * user attribute values will be private, not just the attributes specified in {@link #privateAttributeNames(String...)}. By default,
     * this is false.
     * @param allPrivate true if all user attributes should be private
     * @return the builder
     */
    public Builder allAttributesPrivate(boolean allPrivate) {
      this.allAttributesPrivate = allPrivate;
      return this;
    }

    /**
     * Set whether to send events back to LaunchDarkly. By default, the client will send
     * events. This differs from {@link #offline(boolean)} in that it only affects sending
     * analytics events, not streaming or polling for events from the server.
     *
     * @param sendEvents when set to false, no events will be sent to LaunchDarkly
     * @return the builder
     */
    public Builder sendEvents(boolean sendEvents) {
      this.sendEvents = sendEvents;
      return this;
    }

    /**
     * Deprecated method for setting the polling interval in polling mode.
     * <p>
     * Values less than the default of 30000 will be set to the default.
     * <p>
     * This method has no effect if you have <i>not</i> disabled streaming mode, or if you have specified
     * a non-polling data source with {@link #dataSource(UpdateProcessorFactory)}.
     *
     * @param pollingIntervalMillis rule update polling interval in milliseconds
     * @return the builder
     * @deprecated Use {@link Components#pollingDataSource()} and {@link PollingDataSourceBuilder#pollIntervalMillis(long)}.
     */
    @Deprecated
    public Builder pollingIntervalMillis(long pollingIntervalMillis) {
      this.pollingIntervalMillis = pollingIntervalMillis;
      return this;
    }

    /**
     * Set how long the constructor will block awaiting a successful connection to LaunchDarkly.
     * Setting this to 0 will not block and cause the constructor to return immediately.
     * Default value: 5000
     *
     * @param startWaitMillis milliseconds to wait
     * @return the builder
     */
    public Builder startWaitMillis(long startWaitMillis) {
      this.startWaitMillis = startWaitMillis;
      return this;
    }

    /**
     * Enable event sampling. When set to the default of zero, sampling is disabled and all events
     * are sent back to LaunchDarkly. When set to greater than zero, there is a 1 in
     * <code>samplingInterval</code> chance events will be will be sent.
     * <p>Example: if you want 5% sampling rate, set <code>samplingInterval</code> to 20.
     *
     * @param samplingInterval the sampling interval
     * @return the builder
     * @deprecated This feature will be removed in a future version of the SDK.
     */
    @Deprecated
    public Builder samplingInterval(int samplingInterval) {
      this.samplingInterval = samplingInterval;
      return this;
    }

    /**
     * Deprecated method for setting the initial reconnect delay for the streaming connection.
     * <p>
     * This method has no effect if you have disabled streaming mode, or if you have specified a
     * non-streaming data source with {@link #dataSource(UpdateProcessorFactory)}.
     *
     * @param reconnectTimeMs the reconnect time base value in milliseconds
     * @return the builder
     * @deprecated Use {@link Components#streamingDataSource()} and {@link StreamingDataSourceBuilder#initialReconnectDelayMillis(long)}.
     */
    @Deprecated
    public Builder reconnectTimeMs(long reconnectTimeMs) {
      this.reconnectTimeMillis = reconnectTimeMs;
      return this;
    }

    /**
     * Marks a set of attribute names private. Any users sent to LaunchDarkly with this configuration
     * active will have attributes with these names removed.
     *
     * @param names a set of names that will be removed from user data set to LaunchDarkly
     * @return the builder
     */
    public Builder privateAttributeNames(String... names) {
      this.privateAttrNames = new HashSet<>(Arrays.asList(names));
      return this;
    }

    /**
     * Sets the number of user keys that the event processor can remember at any one time, so that
     * duplicate user details will not be sent in analytics events.
     * 
     * @param capacity the maximum number of user keys to remember
     * @return the builder
     */
    public Builder userKeysCapacity(int capacity) {
      this.userKeysCapacity = capacity;
      return this;
    }

    /**
     * Sets the interval in seconds at which the event processor will reset its set of known user keys. The
     * default value is five minutes.
     *
     * @param flushInterval the flush interval in seconds
     * @return the builder
     */
    public Builder userKeysFlushInterval(int flushInterval) {
      this.userKeysFlushInterval = flushInterval;
      return this;
    }

    /**
     * Sets whether to include full user details in every analytics event. The default is false (events will
     * only include the user key, except for one "index" event that provides the full details for the user).
     * 
     * @param inlineUsersInEvents true if you want full user details in each event
     * @return the builder
     */
    public Builder inlineUsersInEvents(boolean inlineUsersInEvents) {
      this.inlineUsersInEvents = inlineUsersInEvents;
      return this;
    }

    /**
     * Sets the interval at which periodic diagnostic data is sent. The default is every 15 minutes (900,000
     * milliseconds) and the minimum value is 60,000.
     *
     * @see #diagnosticOptOut(boolean)
     *
     * @param diagnosticRecordingIntervalMillis the diagnostics interval in milliseconds
     * @return the builder
     */
    public Builder diagnosticRecordingIntervalMillis(int diagnosticRecordingIntervalMillis) {
      this.diagnosticRecordingIntervalMillis = diagnosticRecordingIntervalMillis;
      return this;
    }

    /**
     * Set to true to opt out of sending diagnostics data.
     *
     * Unless the diagnosticOptOut field is set to true, the client will send some diagnostics data to the
     * LaunchDarkly servers in order to assist in the development of future SDK improvements. These diagnostics
     * consist of an initial payload containing some details of SDK in use, the SDK's configuration, and the platform
     * the SDK is being run on; as well as payloads sent periodically with information on irregular occurrences such
     * as dropped events.
     *
     * @param diagnosticOptOut true if you want to opt out of sending any diagnostics data
     * @return the builder
     */
    public Builder diagnosticOptOut(boolean diagnosticOptOut) {
      this.diagnosticOptOut = diagnosticOptOut;
      return this;
    }

    /**
     * For use by wrapper libraries to set an identifying name for the wrapper being used. This will be included in a
     * header during requests to the LaunchDarkly servers to allow recording metrics on the usage of
     * these wrapper libraries.
     *
     * @param wrapperName an identifying name for the wrapper library
     * @return the builder
     */
    public Builder wrapperName(String wrapperName) {
      this.wrapperName = wrapperName;
      return this;
    }

    /**
     * For use by wrapper libraries to report the version of the library in use. If {@link #wrapperName(String)} is not
     * set, this field will be ignored. Otherwise the version string will be included in a header along
     * with the wrapperName during requests to the LaunchDarkly servers.
     *
     * @param wrapperVersion Version string for the wrapper library
     * @return the builder
     */
    public Builder wrapperVersion(String wrapperVersion) {
      this.wrapperVersion = wrapperVersion;
      return this;
    }

    // returns null if none of the proxy bits were configured. Minimum required part: port.
    Proxy proxy() {
      if (this.proxyPort == -1) {
        return null;
      } else {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
      }
    }

    Authenticator proxyAuthenticator() {
      if (this.proxyUsername != null && this.proxyPassword != null) {
        final String credential = Credentials.basic(proxyUsername, proxyPassword);
        return new Authenticator() {
          public Request authenticate(Route route, Response response) throws IOException {
            if (response.request().header("Proxy-Authorization") != null) {
              return null; // Give up, we've already failed to authenticate with the proxy.
            } else {
              return response.request().newBuilder()
                  .header("Proxy-Authorization", credential)
                  .build();
            }
          }
        };
      }
      return null;
    }

    /**
     * Builds the configured {@link com.launchdarkly.client.LDConfig} object.
     *
     * @return the {@link com.launchdarkly.client.LDConfig} configured by this builder
     */
    public LDConfig build() {
      return new LDConfig(this);
    }
  }
}
