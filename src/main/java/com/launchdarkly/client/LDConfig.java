package com.launchdarkly.client;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * This class exposes advanced configuration options for the {@link LDClient}.
 *
 */
public final class LDConfig {
  private static final URI DEFAULT_BASE_URI = URI.create("https://app.launchdarkly.com");
  private static final int DEFAULT_CAPACITY = 10000;
  private static final int DEFAULT_CONNECT_TIMEOUT = 3;
  private static final int DEFAULT_SOCKET_TIMEOUT = 3;
  private static final Logger logger = LoggerFactory.getLogger(LDConfig.class);

  protected static final LDConfig DEFAULT = new Builder().build();

  final URI baseURI;
  final int capacity;
  final int connectTimeout;
  final int socketTimeout;

  protected LDConfig(Builder builder) {
    this.baseURI = builder.baseURI;
    this.capacity = builder.capacity;
    this.connectTimeout = builder.connectTimeout;
    this.socketTimeout = builder.socketTimeout;
  }


  private URIBuilder getBuilder() {
    return new URIBuilder()
        .setScheme(baseURI.getScheme())
        .setHost(baseURI.getHost())
        .setPort(baseURI.getPort());
  }

  HttpGet getRequest(String apiKey, String path) {
    URIBuilder builder = this.getBuilder().setPath(path);

    try {
      HttpGet request = new HttpGet(builder.build());
      request.addHeader("Authorization", "api_key " + apiKey);
      request.addHeader("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);

      return request;
    }
    catch (Exception e) {
      logger.error("Unhandled exception in LaunchDarkly client", e);
      return null;
    }
  }

  HttpPost postRequest(String apiKey, String path) {
    URIBuilder builder = this.getBuilder().setPath(path);

    try {
      HttpPost request = new HttpPost(builder.build());
      request.addHeader("Authorization", "api_key " + apiKey);
      request.addHeader("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);

      return request;
    }
    catch (Exception e) {
      logger.error("Unhandled exception in LaunchDarkly client", e);
      return null;
    }
  }


  public static class Builder{
    private URI baseURI = DEFAULT_BASE_URI;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
    private int capacity = DEFAULT_CAPACITY;

    public Builder() {
    }

    public Builder baseURI(URI baseURI) {
      this.baseURI = baseURI;
      return this;
    }

    public Builder connectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    public Builder socketTimeout(int socketTimeout) {
      this.socketTimeout = socketTimeout;
      return this;
    }

    public Builder capacity(int capacity) {
      this.capacity = capacity;
      return this;
    }

    public LDConfig build() {
      return new LDConfig(this);
    }

  }

}