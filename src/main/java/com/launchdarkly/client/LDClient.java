package com.launchdarkly.client;

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
public final class LDClient implements LDClientInterface {
  private static final Logger logger = LoggerFactory.getLogger(LDClient.class);
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  static final String CLIENT_VERSION = getClientVersion();

  private final LDConfig config;
  private final String sdkKey;
  final EventProcessor eventProcessor;
  final UpdateProcessor updateProcessor;
  final FeatureStore featureStore;
  final boolean shouldCloseFeatureStore;
  private final EventFactory eventFactory = EventFactory.DEFAULT;

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
    
    if (config.deprecatedFeatureStore != null) {
      this.featureStore = config.deprecatedFeatureStore;
      // The following line is for backward compatibility with the obsolete mechanism by which the
      // caller could pass in a FeatureStore implementation instance that we did not create.  We
      // were not disposing of that instance when the client was closed, so we should continue not
      // doing so until the next major version eliminates that mechanism.  We will always dispose
      // of instances that we created ourselves from a factory.
      this.shouldCloseFeatureStore = false;
    } else {
      FeatureStoreFactory factory = config.featureStoreFactory == null ?
          Components.inMemoryFeatureStore() : config.featureStoreFactory;
      this.featureStore = factory.createFeatureStore();
      this.shouldCloseFeatureStore = true;
    }
    
    EventProcessorFactory epFactory = config.eventProcessorFactory == null ?
        Components.defaultEventProcessor() : config.eventProcessorFactory;
    this.eventProcessor = epFactory.createEventProcessor(sdkKey, config);
    
    UpdateProcessorFactory upFactory = config.updateProcessorFactory == null ?
        Components.defaultUpdateProcessor() : config.updateProcessorFactory;
    this.updateProcessor = upFactory.createUpdateProcessor(sdkKey, config, featureStore);
    Future<Void> startFuture = updateProcessor.start();
    if (config.startWaitMillis > 0L) {
      if (!config.offline && !config.useLdd) {
        logger.info("Waiting up to " + config.startWaitMillis + " milliseconds for LaunchDarkly client to start...");
      }
      try {
        startFuture.get(config.startWaitMillis, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        logger.error("Timeout encountered waiting for LaunchDarkly client initialization");
      } catch (Exception e) {
        logger.error("Exception encountered waiting for LaunchDarkly client initialization: {}", e.toString());
        logger.debug(e.toString(), e);
      }
      if (!updateProcessor.initialized()) {
        logger.warn("LaunchDarkly client was not successfully initialized");
      }
    }
  }

  @Override
  public boolean initialized() {
    return updateProcessor.initialized();
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

  private void sendFlagRequestEvent(Event.FeatureRequest event) {
    eventProcessor.sendEvent(event);
    NewRelicReflector.annotateTransaction(event.key, String.valueOf(event.value));
  }

  @Override
  public Map<String, JsonElement> allFlags(LDUser user) {
    FeatureFlagsState state = allFlagsState(user);
    if (!state.isValid()) {
      return null;
    }
    return state.toValuesMap();
  }

  @Override
  public FeatureFlagsState allFlagsState(LDUser user, FlagsStateOption... options) {
    FeatureFlagsState.Builder builder = new FeatureFlagsState.Builder();
    
    if (isOffline()) {
      logger.debug("allFlagsState() was called when client is in offline mode.");
    }
    
    if (!initialized()) {
      if (featureStore.initialized()) {
        logger.warn("allFlagsState() was called before client initialized; using last known values from feature store");
      } else {
        logger.warn("allFlagsState() was called before client initialized; feature store unavailable, returning no data");
        return builder.valid(false).build();
      }
    }

    if (user == null || user.getKey() == null) {
      logger.warn("allFlagsState() was called with null user or null user key! returning no data");
      return builder.valid(false).build();
    }

    boolean clientSideOnly = FlagsStateOption.hasOption(options, FlagsStateOption.CLIENT_SIDE_ONLY);
    Map<String, FeatureFlag> flags = featureStore.all(FEATURES);
    for (Map.Entry<String, FeatureFlag> entry : flags.entrySet()) {
      FeatureFlag flag = entry.getValue();
      if (clientSideOnly && !flag.isClientSide()) {
        continue;
      }
      try {
        FeatureFlag.VariationAndValue eval = entry.getValue().evaluate(user, featureStore, EventFactory.DEFAULT).getResult();
        builder.addFlag(entry.getValue(), eval);
      } catch (EvaluationException e) {
        logger.error("Exception caught for feature flag \"{}\" when evaluating all flags: {}", entry.getKey(), e.toString());
        logger.debug(e.toString(), e);
        builder.addFlag(entry.getValue(), new FeatureFlag.VariationAndValue(null, null));
      }
    }
    return builder.build();
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
      if (featureStore.initialized()) {
        logger.warn("isFlagKnown called before client initialized for feature flag \"{}\"; using last known values from feature store", featureKey);
      } else {
        logger.warn("isFlagKnown called before client initialized for feature flag \"{}\"; feature store unavailable, returning false", featureKey);
        return false;
      }
    }

    try {
      if (featureStore.get(FEATURES, featureKey) != null) {
        return true;
      }
    } catch (Exception e) {
      logger.error("Encountered exception while calling isFlagKnown for feature flag \"{}\": {}", e.toString());
      logger.debug(e.toString(), e);
    }

    return false;
  }

  private JsonElement evaluate(String featureKey, LDUser user, JsonElement defaultValue, VariationType expectedType) {
    if (!initialized()) {
      if (featureStore.initialized()) {
        logger.warn("Evaluation called before client initialized for feature flag \"{}\"; using last known values from feature store", featureKey);
      } else {
        logger.warn("Evaluation called before client initialized for feature flag \"{}\"; feature store unavailable, returning default value", featureKey);
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue));
        return defaultValue;
      }
    }

    FeatureFlag featureFlag = null;
    try {
      featureFlag = featureStore.get(FEATURES, featureKey);
      if (featureFlag == null) {
        logger.info("Unknown feature flag \"{}\"; returning default value", featureKey);
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue));
        return defaultValue;
      }
      if (user == null || user.getKey() == null) {
        logger.warn("Null user or null user key when evaluating flag \"{}\"; returning default value", featureKey);
        sendFlagRequestEvent(eventFactory.newDefaultFeatureRequestEvent(featureFlag, user, defaultValue));
        return defaultValue;
      }
      if (user.getKeyAsString().isEmpty()) {
        logger.warn("User key is blank. Flag evaluation will proceed, but the user will not be stored in LaunchDarkly");
      }
      FeatureFlag.EvalResult evalResult = featureFlag.evaluate(user, featureStore, eventFactory);
      for (Event.FeatureRequest event : evalResult.getPrerequisiteEvents()) {
        eventProcessor.sendEvent(event);
      }
      if (evalResult.getResult() != null && evalResult.getResult().getValue() != null) {
        expectedType.assertResultType(evalResult.getResult().getValue());
        sendFlagRequestEvent(eventFactory.newFeatureRequestEvent(featureFlag, user, evalResult.getResult(), defaultValue));
        return evalResult.getResult().getValue();
      } else {
        sendFlagRequestEvent(eventFactory.newDefaultFeatureRequestEvent(featureFlag, user, defaultValue));
        return defaultValue;
      }
    } catch (Exception e) {
      logger.error("Encountered exception while evaluating feature flag \"{}\": {}", featureKey, e.toString());
      logger.debug(e.toString(), e);
      if (featureFlag == null) {
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue));
      } else {
        sendFlagRequestEvent(eventFactory.newDefaultFeatureRequestEvent(featureFlag, user, defaultValue));
      }
      return defaultValue;
    }
  }

  @Override
  public void close() throws IOException {
    logger.info("Closing LaunchDarkly Client");
    if (shouldCloseFeatureStore) { // see comment in constructor about this variable
      this.featureStore.close();
    }
    this.eventProcessor.close();
    this.updateProcessor.close();
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
      logger.error("Could not generate secure mode hash: {}", e.toString());
      logger.debug(e.toString(), e);
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
