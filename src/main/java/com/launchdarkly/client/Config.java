package com.launchdarkly.client;

import org.apache.http.client.utils.URIBuilder;

import java.net.URI;

public final class Config {
  public static final URI DEFAULT_BASE_URI = URI.create("https://beta.launchdarkly.com");

  final URI baseURI;
  final String apiKey;

  public Config(String apiKey) {
    this(apiKey, DEFAULT_BASE_URI);
  }

  public Config(String apiKey, URI baseURI) {
    this.apiKey = apiKey;
    this.baseURI = baseURI;
  }

  URIBuilder getBuilder() {
    return new URIBuilder()
        .setScheme(baseURI.getScheme())
        .setHost(baseURI.getHost())
        .setPort(baseURI.getPort());
  }
}