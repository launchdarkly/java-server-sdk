package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Authenticator;
import okhttp3.Credentials;

/**
 * This class exposes advanced configuration options for the {@link LDClient}. Instances of this class must be constructed with a {@link com.launchdarkly.sdk.server.LDConfig.Builder}.
 */
public final class LDConfig {
  private static final Logger logger = LoggerFactory.getLogger(LDConfig.class);

  static final URI DEFAULT_BASE_URI = URI.create("https://app.launchdarkly.com");
  static final URI DEFAULT_EVENTS_URI = URI.create("https://events.launchdarkly.com");
  static final URI DEFAULT_STREAM_URI = URI.create("https://stream.launchdarkly.com");

  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration DEFAULT_SOCKET_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration DEFAULT_START_WAIT = Duration.ofSeconds(5);
  
  protected static final LDConfig DEFAULT = new Builder().build();

  final DataSourceFactory dataSourceFactory;
  final DataStoreFactory dataStoreFactory;
  final boolean diagnosticOptOut;
  final EventProcessorFactory eventProcessorFactory;
  final HttpConfiguration httpConfig;
  final boolean offline;
  final Duration startWait;

  protected LDConfig(Builder builder) {
    this.dataStoreFactory = builder.dataStoreFactory;
    this.eventProcessorFactory = builder.eventProcessorFactory;
    this.dataSourceFactory = builder.dataSourceFactory;
    this.diagnosticOptOut = builder.diagnosticOptOut;
    this.offline = builder.offline;
    this.startWait = builder.startWait;

    Proxy proxy = builder.proxy();
    Authenticator proxyAuthenticator = builder.proxyAuthenticator();
    if (proxy != null) {
      if (proxyAuthenticator != null) {
        logger.info("Using proxy: " + proxy + " with authentication.");
      } else {
        logger.info("Using proxy: " + proxy + " without authentication.");
      }
    }
    
    this.httpConfig = new HttpConfiguration(builder.connectTimeout, proxy, proxyAuthenticator,
        builder.socketTimeout, builder.sslSocketFactory, builder.trustManager,
        builder.wrapperName, builder.wrapperVersion);
  }
  
  LDConfig(LDConfig config) {
    this.dataSourceFactory = config.dataSourceFactory;
    this.dataStoreFactory = config.dataStoreFactory;
    this.diagnosticOptOut = config.diagnosticOptOut;
    this.eventProcessorFactory = config.eventProcessorFactory;
    this.httpConfig = config.httpConfig;
    this.offline = config.offline;
    this.startWait = config.startWait;
  }

  /**
   * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> that helps construct
   * {@link com.launchdarkly.sdk.server.LDConfig} objects. Builder calls can be chained, enabling the
   * following pattern:
   * <pre>
   * LDConfig config = new LDConfig.Builder()
   *      .connectTimeoutMillis(3)
   *      .socketTimeoutMillis(3)
   *      .build()
   * </pre>
   */
  public static class Builder {
    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private DataSourceFactory dataSourceFactory = null;
    private DataStoreFactory dataStoreFactory = null;
    private boolean diagnosticOptOut = false;
    private EventProcessorFactory eventProcessorFactory = null;
    private boolean offline = false;
    private String proxyHost = "localhost";
    private int proxyPort = -1;
    private String proxyUsername = null;
    private String proxyPassword = null;
    private Duration socketTimeout = DEFAULT_SOCKET_TIMEOUT;
    private Duration startWait = DEFAULT_START_WAIT;
    private SSLSocketFactory sslSocketFactory = null;
    private X509TrustManager trustManager = null;
    private String wrapperName = null;
    private String wrapperVersion = null;

    /**
     * Creates a builder with all configuration parameters set to the default
     */
    public Builder() {
    }
    
    /**
     * Set the connection timeout for the configuration. This is the time allowed for the underlying HTTP client to connect
     * to the LaunchDarkly server. The default is 2 seconds.
     *
     * @param connectTimeout the connection timeout; null to use the default
     * @return the builder
     */
    public Builder connectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
      return this;
    }

    /**
     * Sets the implementation of the component that receives feature flag data from LaunchDarkly,
     * using a factory object. Depending on the implementation, the factory may be a builder that
     * allows you to set other configuration options as well.
     * <p>
     * The default is {@link Components#streamingDataSource()}. You may instead use
     * {@link Components#pollingDataSource()}, or a test fixture such as
     * {@link com.launchdarkly.sdk.server.integrations.FileData#dataSource()}. See those methods
     * for details on how to configure them.
     * 
     * @param factory the factory object
     * @return the builder
     * @since 4.12.0
     */
    public Builder dataSource(DataSourceFactory factory) {
      this.dataSourceFactory = factory;
      return this;
    }

    /**
     * Sets the implementation of the data store to be used for holding feature flags and
     * related data received from LaunchDarkly, using a factory object. The default is
     * {@link Components#inMemoryDataStore()}; for database integrations, use
     * {@link Components#persistentDataStore(com.launchdarkly.sdk.server.interfaces.PersistentDataStoreFactory)}.
     * 
     * @param factory the factory object
     * @return the builder
     * @since 4.12.0
     */
    public Builder dataStore(DataStoreFactory factory) {
      this.dataStoreFactory = factory;
      return this;
    }

    /**
     * Set to true to opt out of sending diagnostics data.
     * <p>
     * Unless {@code diagnosticOptOut} is set to true, the client will send some diagnostics data to the
     * LaunchDarkly servers in order to assist in the development of future SDK improvements. These diagnostics
     * consist of an initial payload containing some details of SDK in use, the SDK's configuration, and the platform
     * the SDK is being run on; as well as payloads sent periodically with information on irregular occurrences such
     * as dropped events.
     *
     * @see com.launchdarkly.sdk.server.integrations.EventProcessorBuilder#diagnosticRecordingInterval(Duration)
     *
     * @param diagnosticOptOut true if you want to opt out of sending any diagnostics data
     * @return the builder
     * @since 4.12.0
     */
    public Builder diagnosticOptOut(boolean diagnosticOptOut) {
      this.diagnosticOptOut = diagnosticOptOut;
      return this;
    }

    /**
     * Sets the implementation of {@link EventProcessor} to be used for processing analytics events.
     * <p>
     * The default is {@link Components#sendEvents()}, but you may choose to use a custom implementation
     * (for instance, a test fixture), or disable events with {@link Components#noEvents()}.
     * 
     * @param factory a builder/factory object for event configuration
     * @return the builder
     * @since 4.12.0
     */
    public Builder events(EventProcessorFactory factory) {
      this.eventProcessorFactory = factory;
      return this;
    }

    /**
     * Set whether this client is offline.
     * <p>
     * In offline mode, the SDK will not make network connections to LaunchDarkly for any purpose. Feature
     * flag data will only be available if it already exists in the data store, and analytics events will
     * not be sent.
     * <p>
     * This is equivalent to calling {@code dataSource(Components.externalUpdatesOnly())} and
     * {@code events(Components.noEvents())}. It overrides any other values you may have set for
     * {@link #dataSource(DataSourceFactory)} or {@link #events(EventProcessorFactory)}.
     * 
     * @param offline when set to true no calls to LaunchDarkly will be made
     * @return the builder
     */
    public Builder offline(boolean offline) {
      this.offline = offline;
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
     * Set the socket timeout for the configuration. This is the number of seconds between successive packets that the
     * client will tolerate before flagging an error. The default is 10 seconds.
     *
     * @param socketTimeout the socket timeout; null to use the default
     * @return the builder
     */
    public Builder socketTimeout(Duration socketTimeout) {
      this.socketTimeout = socketTimeout == null ? DEFAULT_SOCKET_TIMEOUT : socketTimeout;
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
     * Set how long the constructor will block awaiting a successful connection to LaunchDarkly.
     * Setting this to a zero or negative duration will not block and cause the constructor to return immediately.
     * Default value: 5000
     *
     * @param startWait maximum time to wait; null to use the default
     * @return the builder
     */
    public Builder startWait(Duration startWait) {
      this.startWait = startWait == null ? DEFAULT_START_WAIT : startWait;
      return this;
    }

    /**
     * For use by wrapper libraries to set an identifying name for the wrapper being used. This will be included in a
     * header during requests to the LaunchDarkly servers to allow recording metrics on the usage of
     * these wrapper libraries.
     *
     * @param wrapperName an identifying name for the wrapper library
     * @return the builder
     * @since 4.12.0
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
     * @since 4.12.0
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
        return (route, response) -> {
          if (response.request().header("Proxy-Authorization") != null) {
            return null; // Give up, we've already failed to authenticate with the proxy.
          } else {
            return response.request().newBuilder()
                .header("Proxy-Authorization", credential)
                .build();
          }
        };
      }
      return null;
    }

    /**
     * Builds the configured {@link com.launchdarkly.sdk.server.LDConfig} object.
     *
     * @return the {@link com.launchdarkly.sdk.server.LDConfig} configured by this builder
     */
    public LDConfig build() {
      return new LDConfig(this);
    }
  }
}
