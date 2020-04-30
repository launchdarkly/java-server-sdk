package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import java.net.Proxy;
import java.time.Duration;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

final class HttpConfigurationImpl implements HttpConfiguration {
  final Duration connectTimeout;
  final Proxy proxy;
  final HttpAuthentication proxyAuth;
  final Duration socketTimeout;
  final SSLSocketFactory sslSocketFactory;
  final X509TrustManager trustManager;
  final String wrapper;
  
  HttpConfigurationImpl(Duration connectTimeout, Proxy proxy, HttpAuthentication proxyAuth,
      Duration socketTimeout, SSLSocketFactory sslSocketFactory, X509TrustManager trustManager,
      String wrapper) {
    this.connectTimeout = connectTimeout;
    this.proxy = proxy;
    this.proxyAuth = proxyAuth;
    this.socketTimeout = socketTimeout;
    this.sslSocketFactory = sslSocketFactory;
    this.trustManager = trustManager;
    this.wrapper = wrapper;
  }

  @Override
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  @Override
  public Proxy getProxy() {
    return proxy;
  }

  @Override
  public HttpAuthentication getProxyAuthentication() {
    return proxyAuth;
  }

  @Override
  public Duration getSocketTimeout() {
    return socketTimeout;
  }

  @Override
  public SSLSocketFactory getSslSocketFactory() {
    return sslSocketFactory;
  }

  @Override
  public X509TrustManager getTrustManager() {
    return trustManager;
  }

  @Override
  public String getWrapperIdentifier() {
    return wrapper;
  }
}
