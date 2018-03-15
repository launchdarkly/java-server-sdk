package com.launchdarkly.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;

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
  private final EventFactory eventFactory = EventFactory.DEFAULT;
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
    if (config.offline || !config.sendEvents) {
      this.eventProcessor = new NullEventProcessor();
    } else {
      this.eventProcessor = createEventProcessor(sdkKey, config);
    }

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
      logger.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");
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
    return new DefaultEventProcessor(sdkKey, config);
  }

  @VisibleForTesting
  protected StreamProcessor createStreamProcessor(String sdkKey, LDConfig config, FeatureRequestor requestor) {
    return new StreamProcessor(sdkKey, config, requestor);
  }

  @VisibleForTesting
  protected PollingProcessor createPollingProcessor(LDConfig config) {
    return new PollingProcessor(config, requestor);
  }
  
  @Override
  public void track(String eventName, LDUser user, JsonElement data) {
    if (isOffline()) {
      return;
    }
    if (user == null || user.getKey() == null) {
      logger.warn("Track called with null user or null user key!");
    }
    eventProcessor.sendEvent(eventFactory.newCustomEvent(eventName, user, data));
  }

  @Override
  public void track(String eventName, LDUser user) {
    if (isOffline()) {
      return;
    }
    track(eventName, user, null);
  }

  @Override
  public void identify(LDUser user) {
    if (user == null || user.getKey() == null) {
      logger.warn("Identify called with null user or null user key!");
    }
    eventProcessor.sendEvent(eventFactory.newIdentifyEvent(user));
  }

  private void sendFlagRequestEvent(FeatureRequestEvent event) {
    eventProcessor.sendEvent(event);
    NewRelicReflector.annotateTransaction(event.key, String.valueOf(event.value));
  }

  @Override
  public Map<String, JsonElement> allFlags(LDUser user) {
    if (isOffline()) {
      logger.debug("allFlags() was called when client is in offline mode.");
    }

    if (!initialized()) {
      if (config.featureStore.initialized()) {
        logger.warn("allFlags() was called before client initialized; using last known values from feature store");
      } else {
        logger.warn("allFlags() was called before client initialized; feature store unavailable, returning null");
        return null;
      }
    }

    if (user == null || user.getKey() == null) {
      logger.warn("allFlags() was called with null user or null user key! returning null");
      return null;
    }

    Map<String, FeatureFlag> flags = this.config.featureStore.all(FEATURES);
    Map<String, JsonElement> result = new HashMap<>();

    for (Map.Entry<String, FeatureFlag> entry : flags.entrySet()) {
      try {
        JsonElement evalResult = entry.getValue().evaluate(user, config.featureStore, eventFactory).getResult().getValue();
          result.put(entry.getKey(), evalResult);

      } catch (EvaluationException e) {
        logger.error("Exception caught when evaluating all flags:", e);
      }
    }
    return result;
  }

  @Override
  public boolean boolVariation(String featureKey, LDUser user, boolean defaultValue) {
    JsonElement value = evaluate(featureKey, user, new JsonPrimitive(defaultValue), VariationType.Boolean);
    return value.getAsJsonPrimitive().getAsBoolean();
  }

  @Override
  public Integer intVariation(String featureKey, LDUser user, int defaultValue) {
    JsonElement value = evaluate(featureKey, user, new JsonPrimitive(defaultValue), VariationType.Integer);
    return value.getAsJsonPrimitive().getAsInt();
  }

  @Override
  public Double doubleVariation(String featureKey, LDUser user, Double defaultValue) {
    JsonElement value = evaluate(featureKey, user, new JsonPrimitive(defaultValue), VariationType.Double);
    return value.getAsJsonPrimitive().getAsDouble();
  }

  @Override
  public String stringVariation(String featureKey, LDUser user, String defaultValue) {
    JsonElement value = evaluate(featureKey, user, new JsonPrimitive(defaultValue), VariationType.String);
    return value.getAsJsonPrimitive().getAsString();
  }

  @Override
  public JsonElement jsonVariation(String featureKey, LDUser user, JsonElement defaultValue) {
    JsonElement value = evaluate(featureKey, user, defaultValue, VariationType.Json);
    return value;
  }

  @Override
  public boolean isFlagKnown(String featureKey) {
    if (!initialized()) {
      if (config.featureStore.initialized()) {
        logger.warn("isFlagKnown called before client initialized for feature flag " + featureKey + "; using last known values from feature store");
      } else {
        logger.warn("isFlagKnown called before client initialized for feature flag " + featureKey + "; feature store unavailable, returning false");
        return false;
      }
    }

    try {
      if (config.featureStore.get(FEATURES, featureKey) != null) {
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
      sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue));
      return defaultValue;
    }
    if (user.getKeyAsString().isEmpty()) {
      logger.warn("User key is blank. Flag evaluation will proceed, but the user will not be stored in LaunchDarkly");
    }
    if (!initialized()) {
      if (config.featureStore.initialized()) {
        logger.warn("Evaluation called before client initialized for feature flag " + featureKey + "; using last known values from feature store");
      } else {
        logger.warn("Evaluation called before client initialized for feature flag " + featureKey + "; feature store unavailable, returning default value");
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue));
        return defaultValue;
      }
    }

    try {
      FeatureFlag featureFlag = config.featureStore.get(FEATURES, featureKey);
      if (featureFlag == null) {
        logger.info("Unknown feature flag " + featureKey + "; returning default value");
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue));
        return defaultValue;
      }
      FeatureFlag.EvalResult evalResult = featureFlag.evaluate(user, config.featureStore, eventFactory);
      for (FeatureRequestEvent event : evalResult.getPrerequisiteEvents()) {
        eventProcessor.sendEvent(event);
      }
      if (evalResult.getResult() != null && evalResult.getResult().getValue() != null) {
        expectedType.assertResultType(evalResult.getResult().getValue());
        sendFlagRequestEvent(eventFactory.newFeatureRequestEvent(featureFlag, user, evalResult.getResult(), defaultValue));
        return evalResult.getResult().getValue();
      }
    } catch (Exception e) {
      logger.error("Encountered exception in LaunchDarkly client", e);
    }
    sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue));
    return defaultValue;
  }

  @Override
  public void close() throws IOException {
    logger.info("Closing LaunchDarkly Client");
    this.eventProcessor.close();
    if (this.updateProcessor != null) {
      this.updateProcessor.close();
    }
    if (this.config.httpClient != null) {
      if (this.config.httpClient.dispatcher() != null && this.config.httpClient.dispatcher().executorService() != null) {
        this.config.httpClient.dispatcher().cancelAll();
        this.config.httpClient.dispatcher().executorService().shutdownNow();
      }
      if (this.config.httpClient.connectionPool() != null) {
        this.config.httpClient.connectionPool().evictAll();
      }
      if (this.config.httpClient.cache() != null) {
        this.config.httpClient.cache().close();
      }
    }
  }

  @Override
  public void flush() {
    this.eventProcessor.flush();
  }

  @Override
  public boolean isOffline() {
    return config.offline;
  }

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

  /**
   * Returns the current version string of the client library.
   * @return a version string conforming to Semantic Versioning (http://semver.org)
   */
  @Override
  public String version() {
    return CLIENT_VERSION;
  }
  
  private static String getClientVersion() {
    Class<?> clazz = LDConfig.class;
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
