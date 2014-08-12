package com.launchdarkly.client;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;

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

  public boolean getFeatureFlag(String key, User user, boolean defaultValue) {
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