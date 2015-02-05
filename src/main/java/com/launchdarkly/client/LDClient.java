package com.launchdarkly.client;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
public class LDClient implements Closeable {
  private static final Logger logger = LoggerFactory.getLogger(LDClient.class);
  private final LDConfig config;
  private final CloseableHttpClient client;
  private final EventProcessor eventProcessor;
  private final String apiKey;
  protected static final String CLIENT_VERSION = getClientVersion();
  private volatile boolean offline = false;


  /**
   * Creates a new client instance that connects to LaunchDarkly with the default configuration. In most
   * cases, you should use this constructor.
   *
   * @param apiKey the API key for your account
   */
  public LDClient(String apiKey) {
    this(apiKey, LDConfig.DEFAULT);
  }

  /**
   * Creates a new client to connect to LaunchDarkly with a custom configuration. This constructor
   * can be used to configure advanced client features, such as customizing the LaunchDarkly base URL.
   *
   * @param apiKey the API key for your account
   * @param config a client configuration object
   */
  public LDClient(String apiKey, LDConfig config) {
    this.apiKey = apiKey;
    this.config = config;
    this.client = createClient();
    this.eventProcessor = createEventProcessor(apiKey, config);
  }

  protected EventProcessor createEventProcessor(String apiKey, LDConfig config) {
    return new EventProcessor(apiKey, config);
  }

  protected CloseableHttpClient createClient() {
    CloseableHttpClient client;
    PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
    manager.setMaxTotal(100);
    manager.setDefaultMaxPerRoute(20);

    CacheConfig cacheConfig = CacheConfig.custom()
        .setMaxCacheEntries(1000)
        .setMaxObjectSize(8192)
        .setSharedCache(false)
        .build();

    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(config.connectTimeout * 1000)
        .setSocketTimeout(config.socketTimeout * 1000)
        .build();
    client = CachingHttpClients.custom()
        .setCacheConfig(cacheConfig)
        .setConnectionManager(manager)
        .setDefaultRequestConfig(requestConfig)
        .build();
    return client;
  }

  /**
   * Tracks that a user performed an event.
   *
   * @param eventName the name of the event
   * @param user the user that performed the event
   * @param data a JSON object containing additional data associated with the event
   */
  public void track(String eventName, LDUser user, JsonElement data) {
    boolean processed = eventProcessor.sendEvent(new CustomEvent(eventName, user, data));
    if (!processed) {
      logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
    }
  }

  /**
   * Tracks that a user performed an event.
   *
   * @param eventName the name of the event
   * @param user the user that performed the event
   */
  public void track(String eventName, LDUser user) {
    if (this.offline) {
      return;
    }
    track(eventName, user, null);
  }

  /**
   * Register the user
   * @param user the user to register
   */
  public void identify(LDUser user) {
    if (this.offline) {
      return;
    }
    boolean processed = eventProcessor.sendEvent(new IdentifyEvent(user));
    if (!processed) {
      logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
    }
  }

  private void sendFlagRequestEvent(String featureKey, LDUser user, boolean value) {
    boolean processed = eventProcessor.sendEvent(new FeatureRequestEvent<Boolean>(featureKey, user, value));
    if (!processed) {
      logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
    }
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
    if (this.offline) {
      return defaultValue;
    }

    Gson gson = new Gson();
    HttpCacheContext context = HttpCacheContext.create();
    HttpGet request = config.getRequest(apiKey, "/api/eval/features/" + featureKey);

    CloseableHttpResponse response = null;
    try {
      response = client.execute(request, context);

      CacheResponseStatus responseStatus = context.getCacheResponseStatus();

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

    } catch (Exception e) {
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
   * Closes the LaunchDarkly client event processing thread and flushes all pending events. This should only
   * be called on application shutdown.
   *
   * @throws IOException
   */
  @Override
  public void close() throws IOException {
    this.eventProcessor.close();
  }

  /**
   * Flushes all pending events
   */
  public void flush() {
    this.eventProcessor.flush();
  }

  /**
   * Puts the LaunchDarkly client in offline mode.
   * In offline mode, all calls to {@link #getFlag(String, LDUser, boolean)} will return the default value, and
   * {@link #track(String, LDUser, com.google.gson.JsonObject)} will be a no-op.
   *
   */
  public void setOffline() {
    this.offline = true;
  }

  /**
   * Puts the LaunchDarkly client in online mode.
   *
   */
  public void setOnline() {
    this.offline = false;
  }

  /**
   *
   * @return whether the client is in offline mode
   */
  public boolean isOffline() {
    return this.offline;
  }

  private static String getClientVersion() {
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