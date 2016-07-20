package com.launchdarkly.client;

import org.apache.http.HttpStatus;
import org.apache.http.client.cache.CacheResponseStatus;
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

class FeatureRequestor {

  public static final String GET_LATEST_FLAGS_PATH = "/sdk/latest-flags";
  private final String apiKey;
  private final LDConfig config;
  private final CloseableHttpClient client;
  private static final Logger logger = LoggerFactory.getLogger(FeatureRequestor.class);

  FeatureRequestor(String apiKey, LDConfig config) {
    this.apiKey = apiKey;
    this.config = config;
    this.client = createClient();
  }

  protected CloseableHttpClient createClient() {
    CloseableHttpClient client;
    PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
    manager.setMaxTotal(100);
    manager.setDefaultMaxPerRoute(20);

    CacheConfig cacheConfig = CacheConfig.custom()
        .setMaxCacheEntries(1000)
        .setMaxObjectSize(131072)
        .setSharedCache(false)
        .build();

    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(config.connectTimeout)
        .setSocketTimeout(config.socketTimeout)
        .setProxy(config.proxyHost)
        .build();
    client = CachingHttpClients.custom()
        .setCacheConfig(cacheConfig)
        .setConnectionManager(manager)
        .setDefaultRequestConfig(requestConfig)
        .build();
    return client;
  }

  Map<String, FeatureFlag> makeAllRequest() throws IOException {
    HttpCacheContext context = HttpCacheContext.create();

    HttpGet request = config.getRequest(apiKey, GET_LATEST_FLAGS_PATH);

    CloseableHttpResponse response = null;
    try {
      logger.debug("Making request: " + request);
      response = client.execute(request, context);

      logCacheResponse(context.getCacheResponseStatus());

      handleResponseStatus(response.getStatusLine().getStatusCode(), null);

      String json = EntityUtils.toString(response.getEntity());
      logger.debug("Got response: " + response.toString());
      return FeatureFlag.fromJsonMap(json);
    }
    finally {
      try {
        if (response != null) response.close();
      } catch (IOException e) {
      }
    }
  }

  void logCacheResponse(CacheResponseStatus status) {
    switch (status) {
      case CACHE_HIT:
        logger.debug("A response was generated from the cache with " +
            "no requests sent upstream");
        break;
      case CACHE_MODULE_RESPONSE:
        logger.debug("The response was generated directly by the " +
            "caching module");
        break;
      case CACHE_MISS:
        logger.debug("The response came from an upstream server");
        break;
      case VALIDATED:
        logger.debug("The response was generated from the cache " +
            "after validating the entry with the origin server");
        break;
    }
  }

  void handleResponseStatus(int status, String featureKey) throws IOException {

    if (status != HttpStatus.SC_OK) {
      if (status == HttpStatus.SC_UNAUTHORIZED) {
        logger.error("Invalid API key");
      } else if (status == HttpStatus.SC_NOT_FOUND) {
        if (featureKey != null) {
          logger.error("Unknown feature key: " + featureKey);
        }
        else {
          logger.error("Resource not found");
        }
      } else {
        logger.error("Unexpected status code: " + status);
      }
      throw new IOException("Failed to fetch flags");
    }

  }

  FeatureFlag makeRequest(String featureKey, boolean latest) throws IOException {
    HttpCacheContext context = HttpCacheContext.create();

    String resource = latest ? "/api/eval/latest-features/" : "/api/eval/features/";

    HttpGet request = config.getRequest(apiKey,resource + featureKey);

    CloseableHttpResponse response = null;
    try {
      response = client.execute(request, context);

      logCacheResponse(context.getCacheResponseStatus());

      handleResponseStatus(response.getStatusLine().getStatusCode(), featureKey);

      return FeatureFlag.fromJson(EntityUtils.toString(response.getEntity()));
    }
    finally {
      try {
        if (response != null) response.close();
      } catch (IOException e) {
      }
    }
  }
}
