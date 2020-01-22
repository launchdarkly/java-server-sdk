package com.launchdarkly.client;

import java.net.Proxy;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Authenticator;

// Used internally to encapsulate top-level HTTP configuration that applies to all components.
final class HttpConfiguration {
  final int connectTimeout;
  final TimeUnit connectTimeoutUnit;
  final Proxy proxy;
  final Authenticator proxyAuthenticator;
  final int socketTimeout;
  final TimeUnit socketTimeoutUnit;
  final SSLSocketFactory sslSocketFactory;
  final X509TrustManager trustManager;
  
  HttpConfiguration(int connectTimeout, TimeUnit connectTimeoutUnit, Proxy proxy, Authenticator proxyAuthenticator,
      int socketTimeout, TimeUnit socketTimeoutUnit, SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
    super();
    this.connectTimeout = connectTimeout;
    this.connectTimeoutUnit = connectTimeoutUnit;
    this.proxy = proxy;
    this.proxyAuthenticator = proxyAuthenticator;
    this.socketTimeout = socketTimeout;
    this.socketTimeoutUnit = socketTimeoutUnit;
    this.sslSocketFactory = sslSocketFactory;
    this.trustManager = trustManager;
  }
}
