package com.launchdarkly.sdk.server;

import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.OkHttpClient;

class Util {
  static Headers.Builder getHeadersBuilderFor(String sdkKey, HttpConfiguration config) {
    Headers.Builder builder = new Headers.Builder()
        .add("Authorization", sdkKey)
        .add("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);

    if (config.wrapperName != null) {
      String wrapperVersion = "";
      if (config.wrapperVersion != null) {
        wrapperVersion = "/" + config.wrapperVersion;
      }
      builder.add("X-LaunchDarkly-Wrapper", config.wrapperName + wrapperVersion);
    }

    return builder;
  }
  
  static void configureHttpClientBuilder(HttpConfiguration config, OkHttpClient.Builder builder) {
    builder.connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
      .connectTimeout(config.connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
      .readTimeout(config.socketTimeout.toMillis(), TimeUnit.MILLISECONDS)
      .writeTimeout(config.socketTimeout.toMillis(), TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(false); // we will implement our own retry logic

    if (config.sslSocketFactory != null) {
      builder.sslSocketFactory(config.sslSocketFactory, config.trustManager);
    }

    if (config.proxy != null) {
      builder.proxy(config.proxy);
      if (config.proxyAuthenticator != null) {
        builder.proxyAuthenticator(config.proxyAuthenticator);
      }
    }
  }
  
  static void shutdownHttpClient(OkHttpClient client) {
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
  
  /**
   * Tests whether an HTTP error status represents a condition that might resolve on its own if we retry.
   * @param statusCode the HTTP status
   * @return true if retrying makes sense; false if it should be considered a permanent failure
   */
  static boolean isHttpErrorRecoverable(int statusCode) {
    if (statusCode >= 400 && statusCode < 500) {
      switch (statusCode) {
      case 400: // bad request
      case 408: // request timeout
      case 429: // too many requests
        return true;
      default:
        return false; // all other 4xx errors are unrecoverable
      }
    }
    return true;
  }
  
  /**
   * Builds an appropriate log message for an HTTP error status.
   * @param statusCode the HTTP status
   * @param context description of what we were trying to do
   * @param recoverableMessage description of our behavior if the error is recoverable; typically "will retry"
   * @return a message string
   */
  static String httpErrorMessage(int statusCode, String context, String recoverableMessage) {
    StringBuilder sb = new StringBuilder();
    sb.append("Received HTTP error ").append(statusCode);
    switch (statusCode) {
    case 401:
    case 403:
      sb.append(" (invalid SDK key)");
    }
    sb.append(" for ").append(context).append(" - ");
    sb.append(isHttpErrorRecoverable(statusCode) ? recoverableMessage : "giving up permanently");
    return sb.toString();
  }
}
