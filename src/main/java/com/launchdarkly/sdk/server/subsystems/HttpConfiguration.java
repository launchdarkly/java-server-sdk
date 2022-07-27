package com.launchdarkly.sdk.server.subsystems;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;

import java.net.Proxy;
import java.time.Duration;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Encapsulates top-level HTTP configuration that applies to all SDK components.
 * <p>
 * Use {@link HttpConfigurationBuilder} to construct an instance.
 * <p>
 * The SDK's built-in components use OkHttp as the HTTP client implementation, but since OkHttp types
 * are not surfaced in the public API and custom components might use some other implementation, this
 * class only provides the properties that would be used to create an HTTP client; it does not create
 * the client itself. SDK implementation code uses its own helper methods to do so.
 * 
 * @since 4.13.0
 */
public final class HttpConfiguration {
  private final Duration connectTimeout;
  private final ImmutableMap<String, String> defaultHeaders;
  private final Proxy proxy;
  private final HttpAuthentication proxyAuth;
  private final Duration socketTimeout;
  private final SocketFactory socketFactory;
  private final SSLSocketFactory sslSocketFactory;
  private final X509TrustManager trustManager;

  /**
   * Creates an instance.
   * 
   * @param connectTimeout see {@link #getConnectTimeout()}
   * @param defaultHeaders see {@link #getDefaultHeaders()}
   * @param proxy see {@link #getProxy()}
   * @param proxyAuth see {@link #getProxyAuthentication()}
   * @param socketTimeout see {@link #getSocketTimeout()}
   * @param socketFactory see {@link #getSocketFactory()}
   * @param sslSocketFactory see {@link #getSslSocketFactory()}
   * @param trustManager see {@link #getTrustManager()}
   */
  public HttpConfiguration(
      Duration connectTimeout,
      ImmutableMap<String, String> defaultHeaders,
      Proxy proxy,
      HttpAuthentication proxyAuth,
      Duration socketTimeout,
      SocketFactory socketFactory,
      SSLSocketFactory sslSocketFactory,
      X509TrustManager trustManager) {
    this.connectTimeout = connectTimeout == null ? HttpConfigurationBuilder.DEFAULT_CONNECT_TIMEOUT : connectTimeout;
    this.defaultHeaders = defaultHeaders == null ? ImmutableMap.of() : defaultHeaders;
    this.proxy = proxy;
    this.proxyAuth = proxyAuth;
    this.socketTimeout = socketTimeout == null ? HttpConfigurationBuilder.DEFAULT_SOCKET_TIMEOUT : socketTimeout;
    this.socketFactory = socketFactory;
    this.sslSocketFactory = sslSocketFactory;
    this.trustManager = trustManager;
  }

  /**
   * The connection timeout. This is the time allowed for the underlying HTTP client to connect
   * to the LaunchDarkly server.
   * 
   * @return the connection timeout; must not be null
   */
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * The proxy configuration, if any.
   * 
   * @return a {@link Proxy} instance or null
   */
  public Proxy getProxy() {
    return proxy;
  }

  /**
   * The authentication method to use for a proxy, if any. Ignored if {@link #getProxy()} is null.
   * 
   * @return an {@link HttpAuthentication} implementation or null
   */
  public HttpAuthentication getProxyAuthentication() {
    return proxyAuth;
  }

  /**
   * The socket timeout. This is the amount of time without receiving data on a connection that the
   * SDK will tolerate before signaling an error. This does <i>not</i> apply to the streaming connection
   * used by {@link com.launchdarkly.sdk.server.Components#streamingDataSource()}, which has its own
   * non-configurable read timeout based on the expected behavior of the LaunchDarkly streaming service.
   *  
   * @return the socket timeout; must not be null
   */
  public Duration getSocketTimeout() {
    return socketTimeout;
  }
  
  /**
   * The configured socket factory for insecure connections.
   *
   * @return a SocketFactory or null
   */
  public SocketFactory getSocketFactory() {
    return socketFactory;
  }

  /**
   * The configured socket factory for secure connections.
   * 
   * @return a SSLSocketFactory or null
   */
  public SSLSocketFactory getSslSocketFactory() {
    return sslSocketFactory;
  }

  /**
   * The configured trust manager for secure connections, if custom certificate verification is needed.
   * 
   * @return an X509TrustManager or null
   */
  public X509TrustManager getTrustManager() {
    return trustManager;
  }

  /**
   * Returns the basic headers that should be added to all HTTP requests from SDK components to
   * LaunchDarkly services, based on the current SDK configuration.
   * 
   * @return a list of HTTP header names and values
   */
  public Iterable<Map.Entry<String, String>> getDefaultHeaders() {
    return defaultHeaders.entrySet();
  }
}
