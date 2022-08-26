package com.launchdarkly.sdk.server;

import java.net.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Authenticator;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.OkHttpClient;

/**
 * Internal container for HTTP parameters used by SDK components. Includes logic for creating an
 * OkHttp client.
 * <p>
 * This is separate from any public HTTP configuration/builder classes that are part of the SDK API.
 * Those are transformed into this when the SDK is constructing components. The public API does not
 * reference any OkHttp classes, but this internal class does.  
 */
final class HttpProperties {
  private final long connectTimeoutMillis;
  private final Map<String, String> defaultHeaders;
  private final Proxy proxy;
  private final Authenticator proxyAuth;
  private final SocketFactory socketFactory;
  private final long socketTimeoutMillis;
  private final SSLSocketFactory sslSocketFactory;
  private final X509TrustManager trustManager;

  public HttpProperties(long connectTimeoutMillis, Map<String, String> defaultHeaders, Proxy proxy,
      Authenticator proxyAuth, SocketFactory socketFactory, long socketTimeoutMillis, SSLSocketFactory sslSocketFactory,
      X509TrustManager trustManager) {
    super();
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.defaultHeaders = defaultHeaders == null ? Collections.emptyMap() : new HashMap<>(defaultHeaders);
    this.proxy = proxy;
    this.proxyAuth = proxyAuth;
    this.socketFactory = socketFactory;
    this.socketTimeoutMillis = socketTimeoutMillis;
    this.sslSocketFactory = sslSocketFactory;
    this.trustManager = trustManager;
  }
  
  public Iterable<Map.Entry<String, String>> getDefaultHeaders() {
    return defaultHeaders.entrySet();
  }

  public void applyToHttpClientBuilder(OkHttpClient.Builder builder) {
    builder.connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS));
    if (connectTimeoutMillis > 0) {
      builder.connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS);
    }
    if (socketTimeoutMillis > 0) {
      builder.readTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS);
    }
    builder.retryOnConnectionFailure(false); // we will implement our own retry logic
      
    if (socketFactory != null) {
      builder.socketFactory(socketFactory);
    }
  
    if (sslSocketFactory != null) {
      builder.sslSocketFactory(sslSocketFactory, trustManager);
    }
  
    if (proxy != null) {
      builder.proxy(proxy);
      if (proxyAuth != null) {
        builder.proxyAuthenticator(proxyAuth);
      }
    }
  }
  
  public OkHttpClient.Builder toHttpClientBuilder() {
    OkHttpClient.Builder builder = new OkHttpClient.Builder();
    applyToHttpClientBuilder(builder);
    return builder;
  }
  
  public Headers.Builder toHeadersBuilder() {
    Headers.Builder builder = new Headers.Builder();
    for (Map.Entry<String, String> kv: getDefaultHeaders()) {
      builder.add(kv.getKey(), kv.getValue());
    }
    return builder;
  }

  public static void shutdownHttpClient(OkHttpClient client) {
    if (client.dispatcher() != null) {
      client.dispatcher().cancelAll();
      if (client.dispatcher().executorService() != null) {
        client.dispatcher().executorService().shutdown();
      }
    }
    if (client.connectionPool() != null) {
      client.connectionPool().evictAll();
    }
    if (client.cache() != null) {
      try {
        client.cache().close();
      } catch (Exception e) {}
    }
  }  
}
