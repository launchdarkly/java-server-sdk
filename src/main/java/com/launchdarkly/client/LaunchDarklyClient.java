package com.launchdarkly.client;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;

/**
 *
 * A client for the LaunchDarkly API. Client instances are thread-safe. Applications can safely instantiate
 * a single {@code LaunchDarklyClient} for the lifetime of their application.
 *
 */
@ThreadSafe
public class LaunchDarklyClient {
  private final Config config;
  private final CloseableHttpClient client;

  /**
   * Creates a new client instance that connects to LaunchDarkly with the default configuration. In most
   * cases, you should use this constructor.
   *
   * @param apiKey the API key for your account
   */
  public LaunchDarklyClient(String apiKey) {
    this(new Config(apiKey));
  }

  /**
   * Creates a new client to connect to LaunchDarkly with a custom configuration. This constructor
   * can be used to configure advanced client features, such as customizing the LaunchDarkly base URL.
   *
   * @param config a client configuration object
   */
  public LaunchDarklyClient(Config config) {
    this.config = config;

    PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
    manager.setMaxTotal(100);

    CacheConfig cacheConfig = CacheConfig.custom()
        .setMaxCacheEntries(1000)
        .setMaxObjectSize(8192)
        .setSharedCache(false)
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

  private HttpGet getRequest(String path) {
    URIBuilder builder = config.getBuilder().setPath(path);

    try {
      HttpGet request = new HttpGet(builder.build());
      request.addHeader("Authorization", "api_key " + config.apiKey);

      return request;
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Calculates the value of a feature flag for a given user.
   *
   *
   * @param key the unique key for the feature flag
   * @param user the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return whether or not the flag should be enabled, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  public boolean getFlag(String key, User user, boolean defaultValue) {
    Gson gson = new Gson();
    HttpGet request = getRequest("/api/features/" + key);

    try {
      HttpResponse response = client.execute(request);

      Type boolType = new TypeToken<FeatureRep<Boolean>>(){}.getType();

      FeatureRep<Boolean> result = gson.fromJson(EntityUtils.toString(response.getEntity()), boolType);

      if (!result.on) {
        return defaultValue;
      }

      Boolean val = result.evaluate(user);

      if (val == null) {
        return defaultValue;
      } else {
        return val.booleanValue();
      }

    } catch (IOException e) {
      e.printStackTrace();
      return defaultValue;
    }
  }

}