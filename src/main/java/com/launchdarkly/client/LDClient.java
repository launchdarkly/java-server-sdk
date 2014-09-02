package com.launchdarkly.client;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.cache.CacheResponseStatus;
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * A client for the LaunchDarkly API. Client instances are thread-safe. Applications can safely instantiate
 * a single {@code LDClient} for the lifetime of their application.
 *
 */
@ThreadSafe
public class LDClient {
  private final Logger logger = LoggerFactory.getLogger(LDClient.class);
  private final LDConfig config;
  private final CloseableHttpClient client;

  /**
   * Creates a new client instance that connects to LaunchDarkly with the default configuration. In most
   * cases, you should use this constructor.
   *
   * @param apiKey the API key for your account
   */
  public LDClient(String apiKey) {
    this(new LDConfig(apiKey));
  }

  /**
   * Creates a new client to connect to LaunchDarkly with a custom configuration. This constructor
   * can be used to configure advanced client features, such as customizing the LaunchDarkly base URL.
   *
   * @param config a client configuration object
   */
  public LDClient(LDConfig config) {
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
      request.addHeader("User-Agent", "JavaClient/" + getClientVersion());

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
  public boolean getFlag(String key, LDUser user, boolean defaultValue) {
    Gson gson = new Gson();
    HttpCacheContext context = HttpCacheContext.create();
    HttpGet request = getRequest("/api/eval/features/" + key);

    CloseableHttpResponse response = null;
    try {
      response = client.execute(request, context);

      CacheResponseStatus responseStatus = context.getCacheResponseStatus();

      if (logger.isDebugEnabled()) {
        switch (responseStatus) {
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

      int status = response.getStatusLine().getStatusCode();

      if (status != HttpStatus.SC_OK) {
        if (status == HttpStatus.SC_UNAUTHORIZED) {
          logger.info("Invalid API key");
        } else if (status == HttpStatus.SC_NOT_FOUND) {
          logger.error("Unknown feature key: " + key);
        } else {
          logger.error("Unexpected status code: " + status);
        }
        return defaultValue;
      }

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
    } finally {
      try {
        if (response != null) response.close();
      } catch (IOException e) {
      }
    }
  }

  public static String getClientVersion() {
    Class clazz = LDClient.class;
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
      return "Unknown";
    }
  }

}