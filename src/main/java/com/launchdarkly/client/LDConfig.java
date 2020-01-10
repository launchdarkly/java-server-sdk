package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.launchdarkly.client.interfaces.DataSourceFactory;
import com.launchdarkly.client.interfaces.DataStoreFactory;
import com.launchdarkly.client.interfaces.EventProcessor;
import com.launchdarkly.client.interfaces.EventProcessorFactory;

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

  private static final URI DEFAULT_BASE_URI = URI.create("https://app.launchdarkly.com");
  private static final URI DEFAULT_EVENTS_URI = URI.create("https://events.launchdarkly.com");
  private static final URI DEFAULT_STREAM_URI = URI.create("https://stream.launchdarkly.com");
  private static final int DEFAULT_CAPACITY = 10000;
  private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
  private static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 10000;
  private static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 5;
  private static final long MIN_POLLING_INTERVAL_MILLIS = 30000L;
  private static final long DEFAULT_START_WAIT_MILLIS = 5000L;
  private static final int DEFAULT_SAMPLING_INTERVAL = 0;
  private static final int DEFAULT_USER_KEYS_CAPACITY = 1000;
  private static final int DEFAULT_USER_KEYS_FLUSH_INTERVAL_SECONDS = 60 * 5;
  private static final long DEFAULT_RECONNECT_TIME_MILLIS = 1000;

  protected static final LDConfig DEFAULT = new Builder().build();

  final URI baseURI;
  final URI eventsURI;
  final URI streamURI;
  final int capacity;
  final int flushInterval;
  final Proxy proxy;
  final Authenticator proxyAuthenticator;
  final boolean stream;
  final DataStoreFactory dataStoreFactory;
  final EventProcessorFactory eventProcessorFactory;
  final DataSourceFactory dataSourceFactory;
  final boolean useLdd;
  final boolean offline;
  final boolean allAttributesPrivate;
  final Set<String> privateAttrNames;
  final boolean sendEvents;
  final long pollingIntervalMillis;
  final long startWaitMillis;
  final int samplingInterval;
  final long reconnectTimeMs;
  final int userKeysCapacity;
  final int userKeysFlushInterval;
  final boolean inlineUsersInEvents;
  final SSLSocketFactory sslSocketFactory;
  final X509TrustManager trustManager;
  final int connectTimeout;
  final TimeUnit connectTimeoutUnit;
  final int socketTimeout;
  final TimeUnit socketTimeoutUnit;
  
  protected LDConfig(Builder builder) {
    this.baseURI = builder.baseURI;
    this.eventsURI = builder.eventsURI;
    this.capacity = builder.capacity;
    this.flushInterval = builder.flushIntervalSeconds;
    this.proxy = builder.proxy();
    this.proxyAuthenticator = builder.proxyAuthenticator();
    this.streamURI = builder.streamURI;
    this.stream = builder.stream;
    this.dataStoreFactory = builder.dataStoreFactory;
    this.eventProcessorFactory = builder.eventProcessorFactory;
    this.dataSourceFactory = builder.dataSourceFactory;
    this.useLdd = builder.useLdd;
    this.offline = builder.offline;
    this.allAttributesPrivate = builder.allAttributesPrivate;
    this.privateAttrNames = new HashSet<>(builder.privateAttrNames);
    this.sendEvents = builder.sendEvents;
    if (builder.pollingIntervalMillis < MIN_POLLING_INTERVAL_MILLIS) {
      this.pollingIntervalMillis = MIN_POLLING_INTERVAL_MILLIS;
    } else {
      this.pollingIntervalMillis = builder.pollingIntervalMillis;
    }
    this.startWaitMillis = builder.startWaitMillis;
    this.samplingInterval = builder.samplingInterval;
    this.reconnectTimeMs = builder.reconnectTimeMillis;
    this.userKeysCapacity = builder.userKeysCapacity;
    this.userKeysFlushInterval = builder.userKeysFlushInterval;
    this.inlineUsersInEvents = builder.inlineUsersInEvents;
    this.sslSocketFactory = builder.sslSocketFactory;
    this.trustManager = builder.trustManager;
    this.connectTimeout = builder.connectTimeout;
    this.connectTimeoutUnit = builder.connectTimeoutUnit;
    this.socketTimeout = builder.socketTimeout;
    this.socketTimeoutUnit = builder.socketTimeoutUnit;

    if (proxy != null) {
      if (proxyAuthenticator != null) {
        logger.info("Using proxy: " + proxy + " with authentication.");
      } else {
        logger.info("Using proxy: " + proxy + " without authentication.");
      }
    }
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
    private boolean useLdd = false;
    private boolean offline = false;
    private boolean allAttributesPrivate = false;
    private boolean sendEvents = true;
    private long pollingIntervalMillis = MIN_POLLING_INTERVAL_MILLIS;
    private DataStoreFactory dataStoreFactory = Components.inMemoryDataStore();
    private EventProcessorFactory eventProcessorFactory = Components.defaultEventProcessor();
    private DataSourceFactory dataSourceFactory = Components.defaultDataSource();
    private long startWaitMillis = DEFAULT_START_WAIT_MILLIS;
    private int samplingInterval = DEFAULT_SAMPLING_INTERVAL;
    private long reconnectTimeMillis = DEFAULT_RECONNECT_TIME_MILLIS;
    private Set<String> privateAttrNames = new HashSet<>();
    private int userKeysCapacity = DEFAULT_USER_KEYS_CAPACITY;
    private int userKeysFlushInterval = DEFAULT_USER_KEYS_FLUSH_INTERVAL_SECONDS;
    private boolean inlineUsersInEvents = false;
    private SSLSocketFactory sslSocketFactory = null;
    private X509TrustManager trustManager = null;

    /**
     * Creates a builder with all configuration parameters set to the default
     */
    public Builder() {
    }

    /**
     * Set the base URL of the LaunchDarkly server for this configuration.
     *
     * @param baseURI the base URL of the LaunchDarkly server for this configuration.
     * @return the builder
     */
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
     * Set the base URL of the LaunchDarkly streaming server for this configuration.
     *
     * @param streamURI the base URL of the LaunchDarkly streaming server
     * @return the builder
     */
    public Builder streamURI(URI streamURI) {
      this.streamURI = streamURI;
      return this;
    }

    /**
     * Sets the implementation of the data store to be used for holding feature flags and
     * related data received from LaunchDarkly, using a factory object. The default is
     * {@link Components#inMemoryDataStore()}, but you may use a custom implementation such as
     * {@link com.launchdarkly.client.integrations.Redis#dataStore()}.
     * 
     * @param factory the factory object
     * @return the builder
     * @since 4.11.0
     */
    public Builder dataStore(DataStoreFactory factory) {
      this.dataStoreFactory = factory;
      return this;
    }
    
    /**
     * Sets the implementation of {@link EventProcessor} to be used for processing analytics events,
     * using a factory object. The default is {@link Components#defaultEventProcessor()}, but
     * you may choose to use a custom implementation (for instance, a test fixture).
     * @param factory the factory object
     * @return the builder
     * @since 4.11.0
     */
    public Builder eventProcessor(EventProcessorFactory factory) {
      this.eventProcessorFactory = factory;
      return this;
    }
    
    /**
     * Sets the implementation of the component that receives feature flag data from LaunchDarkly,
     * using a factory object. The default is {@link Components#defaultDataSource()}, but
     * you may choose to use a custom implementation (for instance, a test fixture).
     * 
     * @param factory the factory object
     * @return the builder
     * @since 4.11.0
     */
    public Builder dataSource(DataSourceFactory factory) {
      this.dataSourceFactory = factory;
      return this;
    }
    
    /**
     * Set whether streaming mode should be enabled. By default, streaming is enabled. It should only be
     * disabled on the advice of LaunchDarkly support.
     *
     * @param stream whether streaming mode should be enabled
     * @return the builder
     */
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
     * Set whether this client should use the <a href="https://docs.launchdarkly.com/docs/the-relay-proxy">LaunchDarkly
     * relay</a> in daemon mode, versus subscribing to the streaming or polling API.
     *
     * @param useLdd true to use the relay in daemon mode; false to use streaming or polling
     * @return the builder
     */
    public Builder useLdd(boolean useLdd) {
      this.useLdd = useLdd;
      return this;
    }

    /**
     * Set whether this client is offline.
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
     * Set the polling interval (when streaming is disabled). Values less than the default of
     * 30000 will be set to the default.
     *
     * @param pollingIntervalMillis rule update polling interval in milliseconds
     * @return the builder
     */
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
     * The reconnect base time in milliseconds for the streaming connection. The streaming connection
     * uses an exponential backoff algorithm (with jitter) for reconnects, but will start the backoff
     * with a value near the value specified here.
     *
     * @param reconnectTimeMs the reconnect time base value in milliseconds
     * @return the builder
     */
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
