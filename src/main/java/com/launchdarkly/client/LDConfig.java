package com.launchdarkly.client;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * This class exposes advanced configuration options for the {@link LDClient}.
 *
 */
public final class LDConfig {
  private static final String CLIENT_VERSION = getClientVersion();
  private static final URI DEFAULT_BASE_URI = URI.create("https://app.launchdarkly.com");
  private static final int DEFAULT_CAPACITY = 10000;
  private static final Logger logger = LoggerFactory.getLogger(LDConfig.class);


  final URI baseURI;
  final String apiKey;
  final int capacity;

  /**
   * Create a configuration using the default base URL and the specified API key
   *
   * @param apiKey the API key
   */
  public LDConfig(String apiKey) {
    this(apiKey, DEFAULT_BASE_URI, DEFAULT_CAPACITY);
  }

  /**
   * Create a configuration using the specified base URL and API key
   * @param apiKey the API key
   * @param baseURI the base URL for the LaunchDarkly API. Any path specified in the URI will be ignored.
   * @param capacity the maximum number of events that will be buffered before discarding. Events are batched and sent every 30 seconds,
   *                 so this should be larger than the number of events the app might create in that time.
   */
  public LDConfig(String apiKey, URI baseURI, int capacity) {
    this.apiKey = apiKey;
    this.baseURI = baseURI;
    this.capacity = capacity;
  }

  private URIBuilder getBuilder() {
    return new URIBuilder()
        .setScheme(baseURI.getScheme())
        .setHost(baseURI.getHost())
        .setPort(baseURI.getPort());
  }

  HttpGet getRequest(String path) {
    URIBuilder builder = this.getBuilder().setPath(path);

    try {
      HttpGet request = new HttpGet(builder.build());
      request.addHeader("Authorization", "api_key " + this.apiKey);
      request.addHeader("User-Agent", "JavaClient/" + CLIENT_VERSION);

      return request;
    }
    catch (Exception e) {
      logger.error("Unhandled exception in LaunchDarkly client", e);
      return null;
    }
  }

  HttpPost postRequest(String path) {
    URIBuilder builder = this.getBuilder().setPath(path);

    try {
      HttpPost request = new HttpPost(builder.build());
      request.addHeader("Authorization", "api_key " + this.apiKey);
      request.addHeader("User-Agent", "JavaClient/" + CLIENT_VERSION);

      return request;
    }
    catch (Exception e) {
      logger.error("Unhandled exception in LaunchDarkly client", e);
      return null;
    }
  }

  static String getClientVersion() {
    Class clazz = LDConfig.class;
    String className = clazz.getSimpleName() + ".class";
    String classPath = clazz.getResource(className).toString();
    if (!classPath.startsWith("jar")) {
      // Class not from JAR
      return "Unknown";
    }
    String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
        "/META-INF/MANIFEST.MF";
    Manifest manifest = null;
    try {
      manifest = new Manifest(new URL(manifestPath).openStream());
      Attributes attr = manifest.getMainAttributes();
      String value = attr.getValue("Implementation-Version");
      return value;
    } catch (IOException e) {
      logger.warn("Unable to determine LaunchDarkly client library version", e);
      return "Unknown";
    }
  }

}