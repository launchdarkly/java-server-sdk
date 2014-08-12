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

import java.lang.reflect.Type;

@ThreadSafe
public class LaunchDarklyClient {
  private final Config config;
  private final CloseableHttpClient client;


  /**
   * Creates a new client to connect to LaunchDarkly with the default configuration. In most
   * cases, you should use this constructor to build a client instance.
   *
   * @param apiKey the API key for your account
   */
  public LaunchDarklyClient(String apiKey) {
    this(new Config(apiKey));
  }

  /**
   * Creates a new client to connect to LaunchDarkly with a custom configuration. This constructor
   * should be used to configure advanced client features, such as customizing the LaunchDarkly base URL.
   *
   * @param config
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

  /**
   * Returns the value of a feature flag for a given user.
   *
   *
   * @param key the unique key for the feature flag
   * @param user the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return whether or not the feature should be enabled, or {@code defaultValue} if the feature is disabled in the LaunchDarkly control panel
   */
  public boolean getFlag(String key, User user, boolean defaultValue) {
    Gson gson = new Gson();
    Base64 base64 = new Base64(true);
    try {
      String userJson = gson.toJson(user);

      String encodedUser = new String((byte[])base64.encode(userJson.getBytes("UTF-8")));

      URIBuilder builder = config.getBuilder().setPath("/api/features/" + key + "/" +  encodedUser);

      HttpGet request = new HttpGet(builder.build());
      request.addHeader("Authorization", "api_key " + config.apiKey);

      HttpResponse response = client.execute(request);

      Type boolType = new TypeToken<FeatureValue<Boolean>>() {}.getType();

      FeatureValue<Boolean> result = gson.fromJson(EntityUtils.toString(response.getEntity()), boolType);

      return result.get().booleanValue();

    } catch (Exception e) {
      e.printStackTrace();
      return defaultValue;
    }
  }

}