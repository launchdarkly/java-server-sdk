package com.launchdarkly.client;

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

import java.io.IOException;
import java.util.Map;

class FeatureRequestor {

  public static final String GET_LATEST_FLAGS_PATH = "/sdk/latest-flags";
  private final String sdkKey;
  private final LDConfig config;
  private final CloseableHttpClient client;
  private static final Logger logger = LoggerFactory.getLogger(FeatureRequestor.class);

  FeatureRequestor(String sdkKey, LDConfig config) {
    this.sdkKey = sdkKey;
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

  Map<String, FeatureFlag> getAllFlags() throws IOException {
    HttpCacheContext context = HttpCacheContext.create();

    HttpGet request = config.getRequest(sdkKey, GET_LATEST_FLAGS_PATH);

    CloseableHttpResponse response = null;
    try {
      logger.debug("Making request: " + request);
      response = client.execute(request, context);

      logCacheResponse(context.getCacheResponseStatus());
      if (!Util.handleResponse(logger, request, response)) {
        throw new IOException("Failed to fetch flags");
      }

      String json = EntityUtils.toString(response.getEntity());
      logger.debug("Got response: " + response.toString());
      return FeatureFlag.fromJsonMap(json);
    }
    finally {
      try {
        if (response != null) response.close();
      } catch (IOException ignored) {
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

  FeatureFlag getFlag(String featureKey) throws IOException {
    HttpCacheContext context = HttpCacheContext.create();
    HttpGet request = config.getRequest(sdkKey, GET_LATEST_FLAGS_PATH + "/" + featureKey);
    CloseableHttpResponse response = null;
    try {
      response = client.execute(request, context);

      logCacheResponse(context.getCacheResponseStatus());

      if (!Util.handleResponse(logger, request, response)) {
        throw new IOException("Failed to fetch flag");
      }
      return FeatureFlag.fromJson(EntityUtils.toString(response.getEntity()));
    }
    finally {
      try {
        if (response != null) response.close();
      } catch (IOException ignored) {
      }
    }
  }
}
