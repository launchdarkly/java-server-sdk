package com.launchdarkly.client;

import java.net.URI;

public final class Config {
  public static final URI DEFAULT_BASE_URI = URI.create("https://api.launchdarkly.com");

  final URI baseURI;
  final String apiKey;

  public Config(String apiKey) {
    this(apiKey, DEFAULT_BASE_URI);
  }

  public Config(String apiKey, URI baseURI) {
    this.apiKey = apiKey;
    this.baseURI = baseURI;
  }
}