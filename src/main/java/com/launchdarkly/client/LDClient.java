package com.launchdarkly.client;


import com.google.gson.JsonElement;
import org.apache.http.annotation.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 *
 * A client for the LaunchDarkly API. Client instances are thread-safe. Applications should instantiate
 * a single {@code LDClient} for the lifetime of their application.
 *
 */
@ThreadSafe
public class LDClient implements Closeable {
  private static final Logger logger = LoggerFactory.getLogger(LDClient.class);
  private final LDConfig config;
  private final FeatureRequestor requestor;
  private final EventProcessor eventProcessor;
  private final StreamProcessor streamProcessor;
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
    this.config = config;
    this.requestor = createFeatureRequestor(apiKey, config);
    this.eventProcessor = createEventProcessor(apiKey, config);

    if (config.stream) {
      logger.debug("Enabling streaming API");
      this.streamProcessor = createStreamProcessor(apiKey, config, requestor);
      this.streamProcessor.subscribe();
    } else {
      logger.debug("Streaming API disabled");
      this.streamProcessor = null;
    }
  }

  protected FeatureRequestor createFeatureRequestor(String apiKey, LDConfig config) {
    return new FeatureRequestor(apiKey, config);
  }

  protected EventProcessor createEventProcessor(String apiKey, LDConfig config) {
    return new EventProcessor(apiKey, config);
  }

  protected StreamProcessor createStreamProcessor(String apiKey, LDConfig config, FeatureRequestor requestor) {
    return new StreamProcessor(apiKey, config, requestor);
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

  private void sendFlagRequestEvent(String featureKey, LDUser user, boolean value, boolean defaultValue) {
    boolean processed = eventProcessor.sendEvent(new FeatureRequestEvent<Boolean>(featureKey, user, value, defaultValue));
    if (!processed) {
      logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
    }
    NewRelicReflector.annotateTransaction(featureKey, String.valueOf(value));
  }

  /**
   * Calculates the value of a feature flag for a given user.
   *
   * @param featureKey the unique featureKey for the feature flag
   * @param user the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return whether or not the flag should be enabled, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   * @deprecated As of version 0.7.0, renamed to {@link #toggle(String, LDUser, boolean)}
   */
  public boolean getFlag(String featureKey, LDUser user, boolean defaultValue) {
    return toggle(featureKey, user, defaultValue);
  }

  /**
   * Calculates the value of a feature flag for a given user.
   *
   * @param featureKey the unique featureKey for the feature flag
   * @param user the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return whether or not the flag should be enabled, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  public boolean toggle(String featureKey, LDUser user, boolean defaultValue) {
    if (this.offline) {
      return defaultValue;
    }
    try {
      FeatureRep<Boolean> result;
      if (this.config.stream && this.streamProcessor != null && this.streamProcessor.initialized()) {
        logger.debug("Using feature flag stored from streaming API");
        result = (FeatureRep<Boolean>) this.streamProcessor.getFeature(featureKey);
        if (config.debugStreaming) {
          FeatureRep<Boolean> pollingResult = requestor.makeRequest(featureKey, true);
          if (!result.equals(pollingResult)) {
            logger.warn("Mismatch between streaming and polling feature! Streaming: {} Polling: {}", result, pollingResult);
          }
        }
      } else {
        // If streaming is enabled, always get the latest version of the feature while polling
        result = requestor.makeRequest(featureKey, this.config.stream);
      }
      if (result == null) {
        logger.warn("Unknown feature flag " + featureKey + "; returning default value");
        sendFlagRequestEvent(featureKey, user, defaultValue, defaultValue);
        return defaultValue;
      }

      Boolean val = result.evaluate(user);
      if (val == null) {
        sendFlagRequestEvent(featureKey, user, defaultValue, defaultValue);
        return defaultValue;
      } else {
        boolean value = val.booleanValue();
        sendFlagRequestEvent(featureKey, user, value, defaultValue);
        return value;
      }
    } catch (Exception e) {
      logger.error("Encountered exception in LaunchDarkly client", e);
      sendFlagRequestEvent(featureKey, user, defaultValue, defaultValue);
      return defaultValue;
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
    if (this.streamProcessor != null) {
      this.streamProcessor.close();
    }
  }

  /**
   * Flushes all pending events
   */
  public void flush() {
    this.eventProcessor.flush();
  }

  /**
   * Puts the LaunchDarkly client in offline mode.
   * In offline mode, all calls to {@link #toggle(String, LDUser, boolean)} will return the default value, and
   * {@link #track(String, LDUser, com.google.gson.JsonElement)} will be a no-op.
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