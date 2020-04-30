package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;

import java.net.Proxy;
import java.time.Duration;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Encapsulates top-level HTTP configuration that applies to all SDK components.
 * <p>
 * Use {@link HttpConfigurationBuilder} to construct an instance.
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
   * An optional identifier used by wrapper libraries to indicate what wrapper is being used.
   * 
   * This allows LaunchDarkly to gather metrics on the usage of wrappers that are based on the Java SDK.
   * It is part of {@link HttpConfiguration} because it is included in HTTP headers.
   * 
   * @return a wrapper identifier string or null
   */
  String getWrapperIdentifier();
}
