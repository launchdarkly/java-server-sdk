package com.launchdarkly.client;


import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * A client for the LaunchDarkly API. Client instances are thread-safe. Applications should instantiate
 * a single {@code LDClient} for the lifetime of their application.
 */
public class LDClient implements LDClientInterface {
  private static final Logger logger = LoggerFactory.getLogger(LDClient.class);
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  static final String CLIENT_VERSION = getClientVersion();

  private final LDConfig config;
  private final String sdkKey;
  private final FeatureRequestor requestor;
  private final EventProcessor eventProcessor;
  private UpdateProcessor updateProcessor;

  /**
   * Creates a new client instance that connects to LaunchDarkly with the default configuration. In most
   * cases, you should use this constructor.
   *
   * @param sdkKey the SDK key for your LaunchDarkly environment
   */
  public LDClient(String sdkKey) {
    this(sdkKey, LDConfig.DEFAULT);
  }

  /**
   * Creates a new client to connect to LaunchDarkly with a custom configuration. This constructor
   * can be used to configure advanced client features, such as customizing the LaunchDarkly base URL.
   *
   * @param sdkKey the SDK key for your LaunchDarkly environment
   * @param config a client configuration object
   */
  public LDClient(String sdkKey, LDConfig config) {
    this.config = config;
    this.sdkKey = sdkKey;
    this.requestor = createFeatureRequestor(sdkKey, config);
    this.eventProcessor = createEventProcessor(sdkKey, config);

    if (config.offline) {
      logger.info("Starting LaunchDarkly client in offline mode");
      return;
    }

    if (config.useLdd) {
      logger.info("Starting LaunchDarkly in LDD mode. Skipping direct feature retrieval.");
      return;
    }

    if (config.stream) {
      logger.info("Enabling streaming API");
      this.updateProcessor = createStreamProcessor(sdkKey, config, requestor);
    } else {
      logger.info("Disabling streaming API");
      this.updateProcessor = createPollingProcessor(config);
    }

    Future<Void> startFuture = updateProcessor.start();

    if (config.startWaitMillis > 0L) {
      logger.info("Waiting up to " + config.startWaitMillis + " milliseconds for LaunchDarkly client to start...");
      try {
        startFuture.get(config.startWaitMillis, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        logger.error("Timeout encountered waiting for LaunchDarkly client initialization");
      } catch (Exception e) {
        logger.error("Exception encountered waiting for LaunchDarkly client initialization", e);
      }
    }
  }

  @Override
  public boolean initialized() {
    return isOffline() || config.useLdd || updateProcessor.initialized();
  }

  @VisibleForTesting
  protected FeatureRequestor createFeatureRequestor(String sdkKey, LDConfig config) {
    return new FeatureRequestor(sdkKey, config);
  }

  @VisibleForTesting
  protected EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
    return new EventProcessor(sdkKey, config);
  }

  @VisibleForTesting
  protected StreamProcessor createStreamProcessor(String sdkKey, LDConfig config, FeatureRequestor requestor) {
    return new StreamProcessor(sdkKey, config, requestor);
  }

  @VisibleForTesting
  protected PollingProcessor createPollingProcessor(LDConfig config) {
    return new PollingProcessor(config, requestor);
  }


  /**
   * Tracks that a user performed an event.
   *
   * @param eventName the name of the event
   * @param user      the user that performed the event
   * @param data      a JSON object containing additional data associated with the event
   */
  @Override
  public void track(String eventName, LDUser user, JsonElement data) {
    if (isOffline()) {
      return;
    }
    if (user == null || user.getKey() == null) {
      logger.warn("Track called with null user or null user key!");
    }
    boolean processed = eventProcessor.sendEvent(new CustomEvent(eventName, user, data));
    if (!processed) {
      logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
    }
  }

  /**
   * Tracks that a user performed an event.
   *
   * @param eventName the name of the event
   * @param user      the user that performed the event
   */
  @Override
  public void track(String eventName, LDUser user) {
    if (isOffline()) {
      return;
    }
    track(eventName, user, null);
  }

  /**
   * Register the user
   *
   * @param user the user to register
   */
  @Override
  public void identify(LDUser user) {
    if (isOffline()) {
      return;
    }
    if (user == null || user.getKey() == null) {
      logger.warn("Identify called with null user or null user key!");
    }
    boolean processed = eventProcessor.sendEvent(new IdentifyEvent(user));
    if (!processed) {
      logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
    }
  }

  private void sendFlagRequestEvent(String featureKey, LDUser user, JsonElement value, JsonElement defaultValue, Integer version) {
    if (isOffline()) {
      return;
    }
    boolean processed = eventProcessor.sendEvent(new FeatureRequestEvent(featureKey, user, value, defaultValue, version, null));
    if (!processed) {
      logger.warn("Exceeded event queue capacity. Increase capacity to avoid dropping events.");
    }
    NewRelicReflector.annotateTransaction(featureKey, String.valueOf(value));
  }

  /**
   * Returns a map from feature flag keys to {@code JsonElement} feature flag values for a given user.
   * If the result of a flag's evaluation would have returned the default variation, it will have a null entry
   * in the map. If the client is offline, has not been initialized, or a null user or user with null/empty user key a {@code null} map will be returned.
   * This method will not send analytics events back to LaunchDarkly.
   * <p>
   * The most common use case for this method is to bootstrap a set of client-side feature flags from a back-end service.
   *
   * @param user the end user requesting the feature flags
   * @return a map from feature flag keys to {@code JsonElement} for the specified user
   */
  @Override
  public Map<String, JsonElement> allFlags(LDUser user) {
    if (isOffline()) {
      logger.debug("allFlags() was called when client is in offline mode.");
    }

    if (!initialized()) {
      logger.warn("allFlags() was called before Client has been initialized! Returning null.");
      return null;
    }

    if (user == null || user.getKey() == null) {
      logger.warn("allFlags() was called with null user or null user key! returning null");
      return null;
    }

    Map<String, FeatureFlag> flags = this.config.featureStore.all();
    Map<String, JsonElement> result = new HashMap<>();

    for (Map.Entry<String, FeatureFlag> entry : flags.entrySet()) {
      try {
        JsonElement evalResult = entry.getValue().evaluate(user, config.featureStore).getValue();
          result.put(entry.getKey(), evalResult);

      } catch (EvaluationException e) {
        logger.error("Exception caught when evaluating all flags:", e);
      }
    }
    return result;
  }

  /**
   * Calculates the value of a feature flag for a given user.
   *
   * @param featureKey   the unique featureKey for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return whether or not the flag should be enabled, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  @Override
  public boolean boolVariation(String featureKey, LDUser user, boolean defaultValue) {
    JsonElement value = evaluate(featureKey, user, new JsonPrimitive(defaultValue), VariationType.Boolean);
    return value.getAsJsonPrimitive().getAsBoolean();
  }

  /**
   * @deprecated use {@link #boolVariation(String, LDUser, boolean)}
   */
  @Override
  @Deprecated
  public boolean toggle(String featureKey, LDUser user, boolean defaultValue) {
    logger.warn("Deprecated method: Toggle() called. Use boolVariation() instead.");
   return boolVariation(featureKey, user, defaultValue);
  }

  /**
   * Calculates the integer value of a feature flag for a given user.
   *
   * @param featureKey   the unique featureKey for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return the variation for the given user, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  @Override
  public Integer intVariation(String featureKey, LDUser user, int defaultValue) {
    JsonElement value = evaluate(featureKey, user, new JsonPrimitive(defaultValue), VariationType.Integer);
    return value.getAsJsonPrimitive().getAsInt();
  }

  /**
   * Calculates the floating point numeric value of a feature flag for a given user.
   *
   * @param featureKey   the unique featureKey for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return the variation for the given user, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  @Override
  public Double doubleVariation(String featureKey, LDUser user, Double defaultValue) {
    JsonElement value = evaluate(featureKey, user, new JsonPrimitive(defaultValue), VariationType.Double);
    return value.getAsJsonPrimitive().getAsDouble();
  }

  /**
   * Calculates the String value of a feature flag for a given user.
   *
   * @param featureKey   the unique featureKey for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return the variation for the given user, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  @Override
  public String stringVariation(String featureKey, LDUser user, String defaultValue) {
    JsonElement value = evaluate(featureKey, user, new JsonPrimitive(defaultValue), VariationType.String);
    return value.getAsJsonPrimitive().getAsString();
  }

  /**
   * Calculates the {@link JsonElement} value of a feature flag for a given user.
   *
   * @param featureKey   the unique featureKey for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return the variation for the given user, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  @Override
  public JsonElement jsonVariation(String featureKey, LDUser user, JsonElement defaultValue) {
    JsonElement value = evaluate(featureKey, user, defaultValue, VariationType.Json);
    return value;
  }

  @Override
  public boolean isFlagKnown(String featureKey) {
    if (!initialized()) {
      logger.warn("isFlagKnown called before Client has been initialized for feature flag " + featureKey + "; returning false");
      return false;
    }

    try {
      if (config.featureStore.get(featureKey) != null) {
        return true;
      }
    } catch (Exception e) {
      logger.error("Encountered exception in LaunchDarkly client", e);
    }

    return false;
  }

  private JsonElement evaluate(String featureKey, LDUser user, JsonElement defaultValue, VariationType expectedType) {
    if (user == null || user.getKey() == null) {
      logger.warn("Null user or null user key when evaluating flag: " + featureKey + "; returning default value");
      sendFlagRequestEvent(featureKey, user, defaultValue, defaultValue, null);
      return defaultValue;
    }
    if (user.getKeyAsString().isEmpty()) {
      logger.warn("User key is blank. Flag evaluation will proceed, but the user will not be stored in LaunchDarkly");
    }
    if (!initialized()) {
      logger.warn("Evaluation called before Client has been initialized for feature flag " + featureKey + "; returning default value");
      sendFlagRequestEvent(featureKey, user, defaultValue, defaultValue, null);
      return defaultValue;
    }

    try {
      FeatureFlag featureFlag = config.featureStore.get(featureKey);
      if (featureFlag == null) {
        logger.warn("Unknown feature flag " + featureKey + "; returning default value");
        sendFlagRequestEvent(featureKey, user, defaultValue, defaultValue, null);
        return defaultValue;
      }
      FeatureFlag.EvalResult evalResult = featureFlag.evaluate(user, config.featureStore);
      if (!isOffline()) {
        for (FeatureRequestEvent event : evalResult.getPrerequisiteEvents()) {
          eventProcessor.sendEvent(event);
        }
      }
      if (evalResult.getValue() != null) {
        expectedType.assertResultType(evalResult.getValue());
        sendFlagRequestEvent(featureKey, user, evalResult.getValue(), defaultValue, featureFlag.getVersion());
        return evalResult.getValue();
      }
    } catch (Exception e) {
      logger.error("Encountered exception in LaunchDarkly client", e);
    }
    sendFlagRequestEvent(featureKey, user, defaultValue, defaultValue, null);
    return defaultValue;
  }

  /**
   * Closes the LaunchDarkly client event processing thread and flushes all pending events. This should only
   * be called on application shutdown.
   *
   * @throws IOException
   */
  @Override
  public void close() throws IOException {
    logger.info("Closing LaunchDarkly Client");
    this.eventProcessor.close();
    if (this.updateProcessor != null) {
      this.updateProcessor.close();
    }
    if (this.config.httpClient != null) {
      if (this.config.httpClient.dispatcher() != null && this.config.httpClient.dispatcher().executorService() != null) {
        this.config.httpClient.dispatcher().executorService().shutdown();
      }
      if (this.config.httpClient.connectionPool() != null) {
        this.config.httpClient.connectionPool().evictAll();
      }
      if (this.config.httpClient.cache() != null) {
        this.config.httpClient.cache().close();
      }
    }
  }

  /**
   * Flushes all pending events
   */
  @Override
  public void flush() {
    this.eventProcessor.flush();
  }

  /**
   * @return whether the client is in offline mode
   */
  @Override
  public boolean isOffline() {
    return config.offline;
  }

  /**
   * For more info: <a href=https://github.com/launchdarkly/js-client#secure-mode>https://github.com/launchdarkly/js-client#secure-mode</a>
   * @param user The User to be hashed along with the sdk key
   * @return the hash, or null if the hash could not be calculated.
     */
  @Override
  public String secureModeHash(LDUser user) {
    if (user == null || user.getKey() == null) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(sdkKey.getBytes(), HMAC_ALGORITHM));
      return Hex.encodeHexString(mac.doFinal(user.getKeyAsString().getBytes("UTF8")));
    } catch (InvalidKeyException | UnsupportedEncodingException | NoSuchAlgorithmException e) {
      logger.error("Could not generate secure mode hash", e);
    }
    return null;
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