package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Contains methods for configuring the SDK's networking behavior.
 * <p>
 * If you want to set non-default values for any of these properties, create a builder with
 * {@link Components#httpConfiguration()}, change its properties with the methods of this class,
 * and pass it to {@link com.launchdarkly.sdk.server.LDConfig.Builder#http(ComponentConfigurer)}:
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
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#httpConfiguration()}.
 * 
 * @since 4.13.0
 */
public abstract class HttpConfigurationBuilder implements ComponentConfigurer<HttpConfiguration> {
  /**
   * The default value for {@link #connectTimeout(Duration)}: two seconds.
   */
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
  
  /**
   * The default value for {@link #socketTimeout(Duration)}: 10 seconds.
   */
  public static final Duration DEFAULT_SOCKET_TIMEOUT = Duration.ofSeconds(10);

  protected Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
  protected HttpAuthentication proxyAuth;
  protected String proxyHost;
  protected Map<String, String> customHeaders = new HashMap<>();
  protected int proxyPort;
  protected Duration socketTimeout = DEFAULT_SOCKET_TIMEOUT;
  protected SocketFactory socketFactory;
  protected SSLSocketFactory sslSocketFactory;
  protected X509TrustManager trustManager;
  protected String wrapperName;
  protected String wrapperVersion;
  
  /**
   * Sets the connection timeout. This is the time allowed for the SDK to make a socket connection to
   * any of the LaunchDarkly services.
   * <p>
   * The default is {@link #DEFAULT_CONNECT_TIMEOUT}.
   * 
   * @param connectTimeout the connection timeout; null to use the default
   * @return the builder
   */
  public HttpConfigurationBuilder connectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
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
   * used by {@link com.launchdarkly.sdk.server.Components#streamingDataSource()}, which has its own
   * non-configurable read timeout based on the expected behavior of the LaunchDarkly streaming service.
   * <p>
   * The default is {@link #DEFAULT_SOCKET_TIMEOUT}.
   * 
   * @param socketTimeout the socket timeout; null to use the default
   * @return the builder
   */
  public HttpConfigurationBuilder socketTimeout(Duration socketTimeout) {
    this.socketTimeout = socketTimeout == null ? DEFAULT_SOCKET_TIMEOUT : socketTimeout;
    return this;
  }
  
  /**
   * Specifies a custom socket configuration for HTTP connections to LaunchDarkly.
   * <p>
   * This uses the standard Java interfaces for configuring socket connections.
   *
   * @param socketFactory the socket factory
   * @return the builder
   */
  public HttpConfigurationBuilder socketFactory(SocketFactory socketFactory) {
    this.socketFactory = socketFactory;
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
     * Specifies a custom HTTP header that should be added to all SDK requests.
     * <p>
     * This may be helpful if you are using a gateway or proxy server that requires a specific header in requests. You
     * may add any number of headers.
     *
     * @param headerName standard HTTP header
     * @param headerValue standard HTTP value
     * @return the builder
     */
    public HttpConfigurationBuilder addCustomHeader(String headerName, String headerValue) {
      this.customHeaders.put(headerName, headerValue);
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
