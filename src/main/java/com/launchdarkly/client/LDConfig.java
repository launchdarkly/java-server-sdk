package com.launchdarkly.client;

import com.google.common.io.Files;
import com.google.gson.Gson;
import okhttp3.Authenticator;
import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * This class exposes advanced configuration options for the {@link LDClient}. Instances of this class must be constructed with a {@link com.launchdarkly.client.LDConfig.Builder}.
 */
public final class LDConfig {
  private static final Logger logger = LoggerFactory.getLogger(LDConfig.class);
  static final Gson gson = new Gson();

  private static final URI DEFAULT_BASE_URI = URI.create("https://app.launchdarkly.com");
  private static final URI DEFAULT_EVENTS_URI = URI.create("https://events.launchdarkly.com");
  private static final URI DEFAULT_STREAM_URI = URI.create("https://stream.launchdarkly.com");
  private static final int DEFAULT_CAPACITY = 10000;
  private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
  private static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 10000;
  private static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 5;
  private static final long DEFAULT_POLLING_INTERVAL_MILLIS = 1000L;
  private static final long DEFAULT_START_WAIT_MILLIS = 5000L;
  private static final int DEFAULT_SAMPLING_INTERVAL = 0;

  private static final long DEFAULT_RECONNECT_TIME_MILLIS = 1000;
  private static final long MAX_HTTP_CACHE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

  protected static final LDConfig DEFAULT = new Builder().build();

  final URI baseURI;
  final URI eventsURI;
  final URI streamURI;
  final int capacity;
  final int connectTimeoutMillis;
  final int socketTimeoutMillis;
  final int flushInterval;
  final Proxy proxy;
  final Authenticator proxyAuthenticator;
  final OkHttpClient httpClient;
  final boolean stream;
  final FeatureStore featureStore;
  final boolean useLdd;
  final boolean offline;
  final long pollingIntervalMillis;
  final long startWaitMillis;
  final int samplingInterval;
  final long reconnectTimeMs;

  protected LDConfig(Builder builder) {
    this.baseURI = builder.baseURI;
    this.eventsURI = builder.eventsURI;
    this.capacity = builder.capacity;
    this.connectTimeoutMillis = builder.connectTimeoutMillis;
    this.socketTimeoutMillis = builder.socketTimeoutMillis;
    this.flushInterval = builder.flushIntervalSeconds;
    this.proxy = builder.proxy();
    this.proxyAuthenticator = builder.proxyAuthenticator();
    this.streamURI = builder.streamURI;
    this.stream = builder.stream;
    this.featureStore = builder.featureStore;
    this.useLdd = builder.useLdd;
    this.offline = builder.offline;
    if (builder.pollingIntervalMillis < DEFAULT_POLLING_INTERVAL_MILLIS) {
      this.pollingIntervalMillis = DEFAULT_POLLING_INTERVAL_MILLIS;
    } else {
      this.pollingIntervalMillis = builder.pollingIntervalMillis;
    }
    this.startWaitMillis = builder.startWaitMillis;
    this.samplingInterval = builder.samplingInterval;
    this.reconnectTimeMs = builder.reconnectTimeMillis;

    File cacheDir = Files.createTempDir();
    Cache cache = new Cache(cacheDir, MAX_HTTP_CACHE_SIZE_BYTES);

    OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
        .cache(cache)
        .connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
        .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true);

    if (proxy != null) {
      httpClientBuilder.proxy(proxy);
      if (proxyAuthenticator != null) {
        httpClientBuilder.proxyAuthenticator(proxyAuthenticator);
        logger.info("Using proxy: " + proxy + " with authentication.");
      } else {
        logger.info("Using proxy: " + proxy + " without authentication.");
      }
    }

    httpClient = httpClientBuilder
        .build();
  }

  Request.Builder getRequestBuilder(String sdkKey) {
    return new Request.Builder()
        .addHeader("Authorization", sdkKey)
        .addHeader("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);
  }

  /**
   * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> that helps construct {@link com.launchdarkly.client.LDConfig} objects. Builder
   * calls can be chained, enabling the following pattern:
   * <p>
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
    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    private int socketTimeoutMillis = DEFAULT_SOCKET_TIMEOUT_MILLIS;
    private int capacity = DEFAULT_CAPACITY;
    private int flushIntervalSeconds = DEFAULT_FLUSH_INTERVAL_SECONDS;
    private String proxyHost = "localhost";
    private int proxyPort = -1;
    private String proxyUsername = null;
    private String proxyPassword = null;
    private boolean stream = true;
    private boolean useLdd = false;
    private boolean offline = false;
    private long pollingIntervalMillis = DEFAULT_POLLING_INTERVAL_MILLIS;
    private FeatureStore featureStore = new InMemoryFeatureStore();
    private long startWaitMillis = DEFAULT_START_WAIT_MILLIS;
    private int samplingInterval = DEFAULT_SAMPLING_INTERVAL;
    private long reconnectTimeMillis = DEFAULT_RECONNECT_TIME_MILLIS;

    /**
     * Creates a builder with all configuration parameters set to the default
     */
    public Builder() {
    }

    /**
     * Set the base URL of the LaunchDarkly server for this configuration
     *
     * @param baseURI the base URL of the LaunchDarkly server for this configuration
     * @return the builder
     */
    public Builder baseURI(URI baseURI) {
      this.baseURI = baseURI;
      return this;
    }

    /**
     * Set the events URL of the LaunchDarkly server for this configuration
     *
     * @param eventsURI the events URL of the LaunchDarkly server for this configuration
     * @return the builder
     */
    public Builder eventsURI(URI eventsURI) {
      this.eventsURI = eventsURI;
      return this;
    }

    /**
     * Set the base URL of the LaunchDarkly streaming server for this configuration
     *
     * @param streamURI the base URL of the LaunchDarkly streaming server
     * @return the builder
     */
    public Builder streamURI(URI streamURI) {
      this.streamURI = streamURI;
      return this;
    }

    public Builder featureStore(FeatureStore store) {
      this.featureStore = store;
      return this;
    }

    /**
     * Set whether streaming mode should be enabled. By default, streaming is enabled.
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
     * <p>
     * <p>Both this method and {@link #connectTimeoutMillis(int) connectTimeoutMillis} affect the same property internally.</p>
     *
     * @param connectTimeout the connection timeout in seconds
     * @return the builder
     */
    public Builder connectTimeout(int connectTimeout) {
      this.connectTimeoutMillis = connectTimeout * 1000;
      return this;
    }

    /**
     * Set the socket timeout in seconds for the configuration. This is the number of seconds between successive packets that the
     * client will tolerate before flagging an error. The default is 10 seconds.
     * <p>
     * <p>Both this method and {@link #socketTimeoutMillis(int) socketTimeoutMillis} affect the same property internally.</p>
     *
     * @param socketTimeout the socket timeout in seconds
     * @return the builder
     */
    public Builder socketTimeout(int socketTimeout) {
      this.socketTimeoutMillis = socketTimeout * 1000;
      return this;
    }

    /**
     * Set the connection timeout in milliseconds for the configuration. This is the time allowed for the underlying HTTP client to connect
     * to the LaunchDarkly server. The default is 2000 ms.
     * <p>
     * <p>Both this method and {@link #connectTimeout(int) connectTimeoutMillis} affect the same property internally.</p>
     *
     * @param connectTimeoutMillis the connection timeout in milliseconds
     * @return the builder
     */
    public Builder connectTimeoutMillis(int connectTimeoutMillis) {
      this.connectTimeoutMillis = connectTimeoutMillis;
      return this;
    }

    /**
     * Set the socket timeout in milliseconds for the configuration. This is the number of milliseconds between successive packets that the
     * client will tolerate before flagging an error. The default is 10,000 milliseconds.
     * <p>
     * <p>Both this method and {@link #socketTimeout(int) socketTimeoutMillis} affect the same property internally.</p>
     *
     * @param socketTimeoutMillis the socket timeout in milliseconds
     * @return the builder
     */
    public Builder socketTimeoutMillis(int socketTimeoutMillis) {
      this.socketTimeoutMillis = socketTimeoutMillis;
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
     * @param host
     * @return the builder
     */
    public Builder proxyHost(String host) {
      this.proxyHost = host;
      return this;
    }

    /**
     * Set the port to use for an HTTP proxy for making connections to LaunchDarkly. This is required for proxied HTTP connections.
     *
     * @param port
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
     * @param username
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
     * @param password
     * @return the builder
     */
    public Builder proxyPassword(String password) {
      this.proxyPassword = password;
      return this;
    }

    /**
     * Deprecated. Only HTTP proxies are currently supported.
     *
     * @param unused
     * @return the builder
     */
    @Deprecated
    public Builder proxyScheme(String unused) {
      return this;
    }

    /**
     * Set whether this client should subscribe to the streaming API, or whether the LaunchDarkly daemon is in use
     * instead
     *
     * @param useLdd
     * @return the builder
     */
    public Builder useLdd(boolean useLdd) {
      this.useLdd = useLdd;
      return this;
    }

    /**
     * Set whether this client is offline.
     *
     * @param offline when set to true no calls to LaunchDarkly will be made.
     * @return the builder
     */
    public Builder offline(boolean offline) {
      this.offline = offline;
      return this;
    }

    /**
     * Set the polling interval (when streaming is disabled). Values less than the default of 1000
     * will be set to 1000.
     *
     * @param pollingIntervalMillis rule update polling interval in milliseconds.
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
     * <p>
     * <p>Example: if you want 5% sampling rate, set <code>samplingInterval</code> to 20.
     *
     * @param samplingInterval the sampling interval.
     * @return the builder
     */
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
     * Build the configured {@link com.launchdarkly.client.LDConfig} object
     *
     * @return the {@link com.launchdarkly.client.LDConfig} configured by this builder
     */
    public LDConfig build() {
      return new LDConfig(this);
    }
  }
}