package com.launchdarkly.client.integrations;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.interfaces.HttpAuthentication;
import com.launchdarkly.client.interfaces.HttpConfigurationFactory;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Contains methods for configuring the SDK's networking behavior.
 * <p>
 * If you want to set non-default values for any of these properties, create a builder with
 * {@link Components#httpConfiguration()}, change its properties with the methods of this class,
 * and pass it to {@link com.launchdarkly.client.LDConfig.Builder#http(HttpConfigurationFactory)}:
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .http(
 *           Components.httpConfiguration()
 *             .connectTimeoutMillis(3000)
 *             .proxyHostAndPort("my-proxy", 8080)
 *          )
 *         .build();
 * </code></pre>
 * <p>
 * These properties will override any equivalent deprecated properties that were set with {@code LDConfig.Builder},
 * such as {@link com.launchdarkly.client.LDConfig.Builder#connectTimeoutMillis(int)}.
 * <p>
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#httpConfiguration()}.
 * 
 * @since 4.13.0
 */
public abstract class HttpConfigurationBuilder implements HttpConfigurationFactory {
  /**
   * The default value for {@link #connectTimeoutMillis(int)}.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
  
  /**
   * The default value for {@link #socketTimeoutMillis(int)}.
   */
  public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 10000;

  protected int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
  protected HttpAuthentication proxyAuth;
  protected String proxyHost;
  protected int proxyPort;
  protected int socketTimeoutMillis = DEFAULT_SOCKET_TIMEOUT_MILLIS;
  protected SSLSocketFactory sslSocketFactory;
  protected X509TrustManager trustManager;
  protected String wrapperName;
  protected String wrapperVersion;
  
  /**
   * Sets the connection timeout. This is the time allowed for the SDK to make a socket connection to
   * any of the LaunchDarkly services.
   * <p>
   * The default is {@link #DEFAULT_CONNECT_TIMEOUT_MILLIS}.
   * 
   * @param connectTimeoutMillis the connection timeout, in milliseconds
   * @return the builder
   */
  public HttpConfigurationBuilder connectTimeoutMillis(int connectTimeoutMillis) {
    this.connectTimeoutMillis = connectTimeoutMillis;
    return this;
  }

  /**
   * Sets an HTTP proxy for making connections to LaunchDarkly.
   *
   * @param host the proxy hostname
   * @param port the proxy port
   * @return the builder
   */
  public HttpConfigurationBuilder proxyHostAndPort(String host, int port) {
    this.proxyHost = host;
    this.proxyPort = port;
    return this;
  }

  /**
   * Sets an authentication strategy for use with an HTTP proxy. This has no effect unless a proxy
   * was specified with {@link #proxyHostAndPort(String, int)}.
   *
   * @param strategy the authentication strategy
   * @return the builder
   */
  public HttpConfigurationBuilder proxyAuth(HttpAuthentication strategy) {
    this.proxyAuth = strategy;
    return this;
  }

  /**
   * Sets the socket timeout. This is the amount of time without receiving data on a connection that the
   * SDK will tolerate before signaling an error. This does <i>not</i> apply to the streaming connection
   * used by {@link com.launchdarkly.client.Components#streamingDataSource()}, which has its own
   * non-configurable read timeout based on the expected behavior of the LaunchDarkly streaming service.
   * <p>
   * The default is {@link #DEFAULT_SOCKET_TIMEOUT_MILLIS}.
   * 
   * @param socketTimeoutMillis the socket timeout, in milliseconds
   * @return the builder
   */
  public HttpConfigurationBuilder socketTimeoutMillis(int socketTimeoutMillis) {
    this.socketTimeoutMillis = socketTimeoutMillis;
    return this;
  }
  
  /**
   * Specifies a custom security configuration for HTTPS connections to LaunchDarkly.
   * <p>
   * This uses the standard Java interfaces for configuring secure socket connections and certificate
   * verification.
   *
   * @param sslSocketFactory the SSL socket factory
   * @param trustManager the trust manager
   * @return the builder
   */
  public HttpConfigurationBuilder sslSocketFactory(SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
    this.sslSocketFactory = sslSocketFactory;
    this.trustManager = trustManager;
    return this;
  }

  /**
   * For use by wrapper libraries to set an identifying name for the wrapper being used. This will be included in a
   * header during requests to the LaunchDarkly servers to allow recording metrics on the usage of
   * these wrapper libraries.
   * 
   * @param wrapperName an identifying name for the wrapper library
   * @param wrapperVersion version string for the wrapper library
   * @return the builder
   */
  public HttpConfigurationBuilder wrapper(String wrapperName, String wrapperVersion) {
    this.wrapperName = wrapperName;
    this.wrapperVersion = wrapperVersion;
    return this;
  }
}
