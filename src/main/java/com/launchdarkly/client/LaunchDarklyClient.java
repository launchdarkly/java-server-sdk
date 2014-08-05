package com.launchdarkly.client;


import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class LaunchDarklyClient {
  private final Config config;
  private final CloseableHttpClient client;

  public LaunchDarklyClient(String apiKey) {
    this(new Config(apiKey));
  }

  public LaunchDarklyClient(Config config) {
    this.config = config;

    PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
    manager.setMaxTotal(100);

    CacheConfig cacheConfig = CacheConfig.custom()
        .setMaxCacheEntries(1000)
        .setMaxObjectSize(8192)
        .build();

    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(3000)
        .setSocketTimeout(3000)
        .build();
    client = CachingHttpClients.custom()
        .setCacheConfig(cacheConfig)
        .setDefaultRequestConfig(requestConfig)
        .build();
  }

  // TODO
  public boolean getFeatureFlag(String key, User user, boolean defaultValue) {
    

    return defaultValue;
  }



}