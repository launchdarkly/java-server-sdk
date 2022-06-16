package com.launchdarkly.sdk.server.subsystems;

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
public interface HttpConfiguration {
  /**
   * The connection timeout. This is the time allowed for the underlying HTTP client to connect
   * to the LaunchDarkly server.
   * 
   * @return the connection timeout; must not be null
   */
  Duration getConnectTimeout();

  /**
   * The proxy configuration, if any.
   * 
   * @return a {@link Proxy} instance or null
   */
  Proxy getProxy();

  /**
   * The authentication method to use for a proxy, if any. Ignored if {@link #getProxy()} is null.
   * 
   * @return an {@link HttpAuthentication} implementation or null
   */
  HttpAuthentication getProxyAuthentication();

  /**
   * The socket timeout. This is the amount of time without receiving data on a connection that the
   * SDK will tolerate before signaling an error. This does <i>not</i> apply to the streaming connection
   * used by {@link com.launchdarkly.sdk.server.Components#streamingDataSource()}, which has its own
   * non-configurable read timeout based on the expected behavior of the LaunchDarkly streaming service.
   *  
   * @return the socket timeout; must not be null
   */
  Duration getSocketTimeout();
  
  /**
   * The configured socket factory for insecure connections.
   *
   * @return a SocketFactory or null
   */
  default SocketFactory getSocketFactory() {
    return null;
  }

  /**
   * The configured socket factory for secure connections.
   * 
   * @return a SSLSocketFactory or null
   */
  SSLSocketFactory getSslSocketFactory();

  /**
   * The configured trust manager for secure connections, if custom certificate verification is needed.
   * 
   * @return an X509TrustManager or null
   */
  X509TrustManager getTrustManager();

  /**
   * Returns the basic headers that should be added to all HTTP requests from SDK components to
   * LaunchDarkly services, based on the current SDK configuration.
   * 
   * @return a list of HTTP header names and values
   */
  Iterable<Map.Entry<String, String>> getDefaultHeaders();
}
