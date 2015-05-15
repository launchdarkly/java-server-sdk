package com.launchdarkly.client;

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
  private static final int DEFAULT_CAPACITY = 10000;
  private static final int DEFAULT_CONNECT_TIMEOUT = 2;
  private static final int DEFAULT_SOCKET_TIMEOUT = 10;
  private static final int DEFAULT_FLUSH_INTERVAL = 5;
  private static final Logger logger = LoggerFactory.getLogger(LDConfig.class);

  protected static final LDConfig DEFAULT = new Builder().build();

  final URI baseURI;
  final int capacity;
  final int connectTimeout;
  final int socketTimeout;
  final int flushInterval;

  protected LDConfig(Builder builder) {
    this.baseURI = builder.baseURI;
    this.capacity = builder.capacity;
    this.connectTimeout = builder.connectTimeout;
    this.socketTimeout = builder.socketTimeout;
    this.flushInterval = builder.flushInterval;
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
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
    private int capacity = DEFAULT_CAPACITY;
    private int flushInterval = DEFAULT_FLUSH_INTERVAL;

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
}