package com.launchdarkly.client;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * This class exposes advanced configuration options for the {@link LDClient}. Instances of this class must be constructed with a {@link com.launchdarkly.client.LDConfig.Builder}.
 *
 */
public final class LDConfig {
  private static final URI DEFAULT_BASE_URI = URI.create("https://app.launchdarkly.com");
  private static final URI DEFAULT_EVENTS_URI = URI.create("https://events.launchdarkly.com");
  private static final URI DEFAULT_STREAM_URI = URI.create("https://stream.launchdarkly.com");
  private static final int DEFAULT_CAPACITY = 10000;
  private static final int DEFAULT_CONNECT_TIMEOUT = 2000;
  private static final int DEFAULT_SOCKET_TIMEOUT = 10000;
  private static final int DEFAULT_FLUSH_INTERVAL = 5;
  private static final Logger logger = LoggerFactory.getLogger(LDConfig.class);

  protected static final LDConfig DEFAULT = new Builder().build();

  final URI baseURI;
  final URI eventsURI;
  final URI streamURI;
  final int capacity;
  final int connectTimeout;
  final int socketTimeout;
  final int flushInterval;
  final HttpHost proxyHost;
  final boolean stream;
  final boolean debugStreaming;
  final FeatureStore featureStore;
  final boolean useLdd;

  protected LDConfig(Builder builder) {
    this.baseURI = builder.baseURI;
    this.eventsURI = builder.eventsURI;
    this.capacity = builder.capacity;
    this.connectTimeout = builder.connectTimeout;
    this.socketTimeout = builder.socketTimeout;
    this.flushInterval = builder.flushInterval;
    this.proxyHost = builder.proxyHost();
    this.streamURI = builder.streamURI;
    this.stream = builder.stream;
    this.debugStreaming = builder.debugStreaming;
    this.featureStore = builder.featureStore;
    this.useLdd = builder.useLdd;
  }

  /**
   * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> that helps construct {@link com.launchdarkly.client.LDConfig} objects. Builder
   * calls can be chained, enabling the following pattern:
   * 
   * <pre>
   * LDConfig config = new LDConfig.Builder()
   *      .connectTimeout(3)
   *      .socketTimeout(3)
   *      .build()
   * </pre>
   *
   */
  public static class Builder{
    private URI baseURI = DEFAULT_BASE_URI;
    private URI eventsURI = DEFAULT_EVENTS_URI;
    private URI streamURI = DEFAULT_STREAM_URI;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
    private int capacity = DEFAULT_CAPACITY;
    private int flushInterval = DEFAULT_FLUSH_INTERVAL;
    private String proxyHost;
    private int proxyPort = -1;
    private String proxyScheme;
    private boolean stream = true;
    private boolean debugStreaming = false;
    private boolean useLdd = false;
    private FeatureStore featureStore = new InMemoryFeatureStore();

    /**
     * Creates a builder with all configuration parameters set to the default
     */
    public Builder() {
    }

    /**
     * Set the base URL of the LaunchDarkly server for this configuration
     * @param baseURI the base URL of the LaunchDarkly server for this configuration
     * @return the builder
     */
    public Builder baseURI(URI baseURI) {
      this.baseURI = baseURI;
      return this;
    }

    /**
     * Set the events URL of the LaunchDarkly server for this configuration
     * @param baseURI the events URL of the LaunchDarkly server for this configuration
     * @return the builder
     */
    public Builder eventsURI(URI eventsURI) {
      this.eventsURI = eventsURI;
      return this;
    }

    /**
     * Set the base URL of the LaunchDarkly streaming server for this configuration
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
     * Set whether we should debug streaming mode. If set, the client will fetch features via polling and compare the
     * retrieved feature with the value in the feature store. There is a performance cost to this, so it is not
     * recommended for production use.
     * @param debugStreaming whether streaming mode should be debugged
     * @return the builder
     */
    public Builder debugStreaming(boolean debugStreaming) {
      this.debugStreaming = debugStreaming;
      return this;
    }

    /**
     * Set whether streaming mode should be enabled. By default, streaming is enabled.
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
     *
     * <p>Both this method and {@link #connectTimeoutMillis(int) connectTimeoutMillis} affect the same property internally.</p>
     *
     * @param connectTimeout the connection timeout in seconds
     * @return the builder
     */
    public Builder connectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout * 1000;
      return this;
    }

    /**
     * Set the socket timeout in seconds for the configuration. This is the number of seconds between successive packets that the
     * client will tolerate before flagging an error. The default is 10 seconds.
     *
     * <p>Both this method and {@link #socketTimeoutMillis(int) socketTimeoutMillis} affect the same property internally.</p>
     *
     * @param socketTimeout the socket timeout in seconds
     * @return the builder
     */
    public Builder socketTimeout(int socketTimeout) {
      this.socketTimeout = socketTimeout * 1000;
      return this;
    }

    /**
     * Set the connection timeout in milliseconds for the configuration. This is the time allowed for the underlying HTTP client to connect
     * to the LaunchDarkly server. The default is 2000 ms.
     *
     * <p>Both this method and {@link #connectTimeout(int) connectTimeout} affect the same property internally.</p>
     *
     * @param connectTimeoutMillis the connection timeout in milliseconds
     * @return the builder
     */
    public Builder connectTimeoutMillis(int connectTimeoutMillis) {
      this.connectTimeout = connectTimeoutMillis;
      return this;
    }

    /**
     * Set the socket timeout in milliseconds for the configuration. This is the number of milliseconds between successive packets that the
     * client will tolerate before flagging an error. The default is 10,000 milliseconds.
     *
     * <p>Both this method and {@link #socketTimeout(int) socketTimeout} affect the same property internally.</p>
     *
     * @param socketTimeoutMillis the socket timeout in milliseconds
     * @return the builder
     */
    public Builder socketTimeoutMillis(int socketTimeoutMillis) {
      this.socketTimeout = socketTimeoutMillis;
      return this;
    }

    /**
     * Set the number of seconds between flushes of the event buffer. Decreasing the flush interval means
     * that the event buffer is less likely to reach capacity.
     *
     * @param flushInterval the flush interval in seconds
     * @return the builder
     */
    public Builder flushInterval(int flushInterval) {
      this.flushInterval = flushInterval;
      return this;
    }

    /**
     * Set the capacity of the events buffer. The client buffers up to this many events in memory before flushing. If the capacity is exceeded before the buffer is flushed, events will be discarded.
     * Increasing the capacity means that events are less likely to be discarded, at the cost of consuming more memory.
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
     * {@link #proxyPort(int)} or  {@link #proxyScheme(String)} are specified, this will default to <code>localhost</code>.
     * <p>
     * If none of {@link #proxyHost(String)}, {@link #proxyPort(int)} or {@link #proxyScheme(String)} are specified,
     * a proxy will not be used, and {@link LDClient} will connect to LaunchDarkly directly.
     * </p>
     * @param host
     * @return the builder
     */
    public Builder proxyHost(String host) {
      this.proxyHost = host;
      return this;
    }

    /**
     * Set the port to use for an HTTP proxy for making connections to LaunchDarkly.  If not set (but {@link #proxyHost(String)}
     * or {@link #proxyScheme(String)} are specified, the default port for the scheme will be used.
     *
     * <p>
     * If none of {@link #proxyHost(String)}, {@link #proxyPort(int)} or {@link #proxyScheme(String)} are specified,
     * a proxy will not be used, and {@link LDClient} will connect to LaunchDarkly directly.
     * </p>
     * @param port
     * @return the builder
     */
    public Builder proxyPort(int port) {
      this.proxyPort = port;
      return this;
    }

    /**
     * Set the scheme to use for an HTTP proxy for making connections to LaunchDarkly.  If not set (but {@link #proxyHost(String)}
     * or {@link #proxyPort(int)} are specified, the default <code>https</code> scheme will be used.
     *
     * <p>
     * If none of {@link #proxyHost(String)}, {@link #proxyPort(int)} or {@link #proxyScheme(String)} are specified,
     * a proxy will not be used, and {@link LDClient} will connect to LaunchDarkly directly.
     * </p>
     * @param scheme
     * @return the builder
     */
    public Builder proxyScheme(String scheme) {
      this.proxyScheme = scheme;
      return this;
    }

    /**
     * Set whether this client should subscribe to the streaming API, or whether the LaunchDarkly daemon is in use
     * instead
     * @param useLdd
     * @return the builder
     */
    public Builder useLdd(boolean useLdd) {
      this.useLdd = useLdd;
      return this;
    }

    HttpHost proxyHost() {
      if (this.proxyHost == null && this.proxyPort == -1 && this.proxyScheme == null) {
        return null;
      } else {
        String hostname = this.proxyHost == null ? "localhost" : this.proxyHost;
        String scheme = this.proxyScheme == null ? "https" : this.proxyScheme;
        return new HttpHost(hostname, this.proxyPort, scheme);
      }
    }

    /**
     * Build the configured {@link com.launchdarkly.client.LDConfig} object
     * @return the {@link com.launchdarkly.client.LDConfig} configured by this builder
     */
    public LDConfig build() {
      return new LDConfig(this);
    }

  }

  private URIBuilder getBuilder() {
    return new URIBuilder()
        .setScheme(baseURI.getScheme())
        .setHost(baseURI.getHost())
        .setPort(baseURI.getPort());
  }

  private URIBuilder getEventsBuilder() {
    return new URIBuilder()
            .setScheme(eventsURI.getScheme())
            .setHost(eventsURI.getHost())
            .setPort(eventsURI.getPort());
  }

  HttpGet getRequest(String apiKey, String path) {
    URIBuilder builder = this.getBuilder().setPath(path);

    try {
      HttpGet request = new HttpGet(builder.build());
      request.addHeader("Authorization", "api_key " + apiKey);
      request.addHeader("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);

      return request;
    }
    catch (Exception e) {
      logger.error("Unhandled exception in LaunchDarkly client", e);
      return null;
    }
  }

  HttpPost postRequest(String apiKey, String path) {
    URIBuilder builder = this.getBuilder().setPath(path);

    try {
      HttpPost request = new HttpPost(builder.build());
      request.addHeader("Authorization", "api_key " + apiKey);
      request.addHeader("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);

      return request;
    }
    catch (Exception e) {
      logger.error("Unhandled exception in LaunchDarkly client", e);
      return null;
    }
  }

  HttpPost postEventsRequest(String apiKey, String path) {
    URIBuilder builder = this.getEventsBuilder().setPath(eventsURI.getPath() + path);

    try {
      HttpPost request = new HttpPost(builder.build());
      request.addHeader("Authorization", "api_key " + apiKey);
      request.addHeader("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);

      return request;
    }
    catch (Exception e) {
      logger.error("Unhandled exception in LaunchDarkly client", e);
      return null;
    }
  }
}