package com.launchdarkly.client;

import java.net.Proxy;
import java.time.Duration;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Authenticator;

// Used internally to encapsulate top-level HTTP configuration that applies to all components.
final class HttpConfiguration {
  final Duration connectTimeout;
  final Proxy proxy;
  final Authenticator proxyAuthenticator;
  final Duration socketTimeout;
  final SSLSocketFactory sslSocketFactory;
  final X509TrustManager trustManager;
  final String wrapperName;
  final String wrapperVersion;
  
  HttpConfiguration(Duration connectTimeout, Proxy proxy, Authenticator proxyAuthenticator,
      Duration socketTimeout, SSLSocketFactory sslSocketFactory, X509TrustManager trustManager,
      String wrapperName, String wrapperVersion) {
    super();
    this.connectTimeout = connectTimeout;
    this.proxy = proxy;
    this.proxyAuthenticator = proxyAuthenticator;
    this.socketTimeout = socketTimeout;
    this.sslSocketFactory = sslSocketFactory;
    this.trustManager = trustManager;
    this.wrapperName = wrapperName;
    this.wrapperVersion = wrapperVersion;
  }
}
