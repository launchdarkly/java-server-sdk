package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.HttpAuthentication;
import com.launchdarkly.client.interfaces.HttpConfiguration;

import java.net.Proxy;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

final class HttpConfigurationImpl implements HttpConfiguration {
  final int connectTimeoutMillis;
  final Proxy proxy;
  final HttpAuthentication proxyAuth;
  final int socketTimeoutMillis;
  final SSLSocketFactory sslSocketFactory;
  final X509TrustManager trustManager;
  final String wrapper;
  
  HttpConfigurationImpl(int connectTimeoutMillis, Proxy proxy, HttpAuthentication proxyAuth,
      int socketTimeoutMillis, SSLSocketFactory sslSocketFactory, X509TrustManager trustManager,
      String wrapper) {
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.proxy = proxy;
    this.proxyAuth = proxyAuth;
    this.socketTimeoutMillis = socketTimeoutMillis;
    this.sslSocketFactory = sslSocketFactory;
    this.trustManager = trustManager;
    this.wrapper = wrapper;
  }

  @Override
  public int getConnectTimeoutMillis() {
    return connectTimeoutMillis;
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
  public int getSocketTimeoutMillis() {
    return socketTimeoutMillis;
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
