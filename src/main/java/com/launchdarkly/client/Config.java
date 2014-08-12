package com.launchdarkly.client;

import org.apache.http.client.utils.URIBuilder;

import java.net.URI;

/**
 * This class exposes advanced configuration options for the {@link LaunchDarklyClient}.
 *
 */
public final class Config {
  static final URI DEFAULT_BASE_URI = URI.create("https://beta.launchdarkly.com");

  final URI baseURI;
  final String apiKey;

  /**
   * Create a configuration using the default base URL and the specified API key
   *
   * @param apiKey the API key
   */
  public Config(String apiKey) {
    this(apiKey, DEFAULT_BASE_URI);
  }

  /**
   * Create a configuration using the specified base URL and API key
   * @param apiKey the API key
   * @param baseURI the base URL for the LaunchDarkly API. Any path specified in the URI will be ignored.
   */
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