package com.launchdarkly.client;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpStatus;
import org.apache.http.annotation.ThreadSafe;
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

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * A client for the LaunchDarkly API. Client instances are thread-safe. Applications can safely instantiate
 * a single {@code LDClient} for the lifetime of their application.
 *
 */
@ThreadSafe
public class LDClient implements Closeable {
  private static final Logger logger = LoggerFactory.getLogger(LDClient.class);
  private final LDConfig config;
  private final CloseableHttpClient client;
  private final EventProcessor eventProcessor;

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

    eventProcessor = new EventProcessor(config);
  }

  /**
   * Tracks that a user performed an event.
   *
   * @param eventName the name of the event
   * @param user the user that performed the event
   * @param data a JSON object containing additional data associated with the event
   */
  public void sendEvent(String eventName, LDUser user, JsonObject data) {
    eventProcessor.sendEvent(new CustomEvent(eventName, user, data));
  }

  private void sendFlagRequestEvent(String featureKey, LDUser user, boolean value) {
    eventProcessor.sendEvent(new FeatureRequestEvent<Boolean>(featureKey, user, value));
    NewRelicReflector.annotateTransaction(featureKey, String.valueOf(value));
  }

  /**
   * Calculates the value of a feature flag for a given user.
   *
   *
   * @param featureKey the unique featureKey for the feature flag
   * @param user the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return whether or not the flag should be enabled, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  public boolean getFlag(String featureKey, LDUser user, boolean defaultValue) {
    Gson gson = new Gson();
    HttpCacheContext context = HttpCacheContext.create();
    HttpGet request = config.getRequest("/api/eval/features/" + featureKey);

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
          logger.error("Invalid API key");
        } else if (status == HttpStatus.SC_NOT_FOUND) {
          logger.error("Unknown feature key: " + featureKey);
        } else {
          logger.error("Unexpected status code: " + status);
        }
        sendFlagRequestEvent(featureKey, user, defaultValue);
        return defaultValue;
      }

      Type boolType = new TypeToken<FeatureRep<Boolean>>(){}.getType();

      FeatureRep<Boolean> result = gson.fromJson(EntityUtils.toString(response.getEntity()), boolType);

      Boolean val = result.evaluate(user);

      if (val == null) {
        sendFlagRequestEvent(featureKey, user, defaultValue);
        return defaultValue;
      } else {
        boolean value = val.booleanValue();
        sendFlagRequestEvent(featureKey, user, value);
        return value;
      }

    } catch (IOException e) {
      logger.error("Unhandled exception in LaunchDarkly client", e);
      sendFlagRequestEvent(featureKey, user, defaultValue);
      return defaultValue;
    } finally {
      try {
        if (response != null) response.close();
      } catch (IOException e) {
        logger.error("Unhandled exception in LaunchDarkly client", e);
      }
    }
  }

  /**
   * Closes the LaunchDarkly client event processing thread. This should only
   * be called on application shutdown.
   *
   * @throws IOException
   */
  @Override
  public void close() throws IOException {
    this.eventProcessor.close();
  }
}