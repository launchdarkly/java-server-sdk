package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.launchdarkly.client.interfaces.Event;
import com.launchdarkly.client.interfaces.EventProcessor;
import com.launchdarkly.client.interfaces.EventProcessorFactory;
import com.launchdarkly.client.interfaces.FeatureStore;
import com.launchdarkly.client.interfaces.FeatureStoreFactory;
import com.launchdarkly.client.interfaces.UpdateProcessor;
import com.launchdarkly.client.interfaces.UpdateProcessorFactory;
import com.launchdarkly.client.value.LDValue;

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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.launchdarkly.client.DataModel.DataKinds.FEATURES;
import static com.launchdarkly.client.DataModel.DataKinds.SEGMENTS;

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
  private final Evaluator evaluator;
  final EventProcessor eventProcessor;
  final UpdateProcessor updateProcessor;
  final FeatureStore featureStore;
  final boolean shouldCloseFeatureStore;
  
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
    this.config = checkNotNull(config, "config must not be null");
    this.sdkKey = checkNotNull(sdkKey, "sdkKey must not be null");
    
    FeatureStore store;
    if (config.deprecatedFeatureStore != null) {
      store = config.deprecatedFeatureStore;
      // The following line is for backward compatibility with the obsolete mechanism by which the
      // caller could pass in a FeatureStore implementation instance that we did not create.  We
      // were not disposing of that instance when the client was closed, so we should continue not
      // doing so until the next major version eliminates that mechanism.  We will always dispose
      // of instances that we created ourselves from a factory.
      this.shouldCloseFeatureStore = false;
    } else {
      FeatureStoreFactory factory = config.featureStoreFactory == null ?
          Components.inMemoryFeatureStore() : config.featureStoreFactory;
      store = factory.createFeatureStore();
      this.shouldCloseFeatureStore = true;
    }
    this.featureStore = new FeatureStoreClientWrapper(store);
    
    this.evaluator = new Evaluator(new Evaluator.Getters() {
      public DataModel.FeatureFlag getFlag(String key) {
        return LDClient.this.featureStore.get(FEATURES, key);
      }

      public DataModel.Segment getSegment(String key) {
        return LDClient.this.featureStore.get(SEGMENTS, key);
      }
    });
    
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
  public void track(String eventName, LDUser user) {
    trackData(eventName, user, LDValue.ofNull());
  }

  @Override
  public void trackData(String eventName, LDUser user, LDValue data) {
    if (user == null || user.getKeyAsString() == null) {
      logger.warn("Track called with null user or null user key!");
    } else {
      eventProcessor.sendEvent(EventFactory.DEFAULT.newCustomEvent(eventName, user, data, null));
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void track(String eventName, LDUser user, JsonElement data) {
    trackData(eventName, user, LDValue.unsafeFromJsonElement(data));
  }

  @SuppressWarnings("deprecation")
  @Override
  public void track(String eventName, LDUser user, JsonElement data, double metricValue) {
    trackMetric(eventName, user, LDValue.unsafeFromJsonElement(data), metricValue);
  }

  @Override
  public void trackMetric(String eventName, LDUser user, LDValue data, double metricValue) {
    if (user == null || user.getKeyAsString() == null) {
      logger.warn("Track called with null user or null user key!");
    } else {
      eventProcessor.sendEvent(EventFactory.DEFAULT.newCustomEvent(eventName, user, data, metricValue));
    }
  }

  @Override
  public void identify(LDUser user) {
    if (user == null || user.getKeyAsString() == null) {
      logger.warn("Identify called with null user or null user key!");
    } else {
      eventProcessor.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(user));
    }
  }

  private void sendFlagRequestEvent(Event.FeatureRequest event) {
    eventProcessor.sendEvent(event);
    NewRelicReflector.annotateTransaction(event.getKey(), String.valueOf(event.getValue()));
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
    FeatureFlagsState.Builder builder = new FeatureFlagsState.Builder(options);
    
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

    if (user == null || user.getKeyAsString() == null) {
      logger.warn("allFlagsState() was called with null user or null user key! returning no data");
      return builder.valid(false).build();
    }

    boolean clientSideOnly = FlagsStateOption.hasOption(options, FlagsStateOption.CLIENT_SIDE_ONLY);
    Map<String, DataModel.FeatureFlag> flags = featureStore.all(FEATURES);
    for (Map.Entry<String, DataModel.FeatureFlag> entry : flags.entrySet()) {
      DataModel.FeatureFlag flag = entry.getValue();
      if (clientSideOnly && !flag.isClientSide()) {
        continue;
      }
      try {
        Evaluator.EvalResult result = evaluator.evaluate(flag, user, EventFactory.DEFAULT);
        builder.addFlag(flag, result);
      } catch (Exception e) {
        logger.error("Exception caught for feature flag \"{}\" when evaluating all flags: {}", entry.getKey(), e.toString());
        logger.debug(e.toString(), e);
        builder.addFlag(entry.getValue(), errorResult(EvaluationReason.ErrorKind.EXCEPTION, LDValue.ofNull()));
      }
    }
    return builder.build();
  }
  
  @Override
  public boolean boolVariation(String featureKey, LDUser user, boolean defaultValue) {
    return evaluate(featureKey, user, LDValue.of(defaultValue), true).booleanValue();
  }

  @Override
  public Integer intVariation(String featureKey, LDUser user, int defaultValue) {
    return evaluate(featureKey, user, LDValue.of(defaultValue), true).intValue();
  }

  @Override
  public Double doubleVariation(String featureKey, LDUser user, Double defaultValue) {
    return evaluate(featureKey, user, LDValue.of(defaultValue), true).doubleValue();
  }

  @Override
  public String stringVariation(String featureKey, LDUser user, String defaultValue) {
    return evaluate(featureKey, user, LDValue.of(defaultValue), true).stringValue();
  }

  @SuppressWarnings("deprecation")
  @Override
  public JsonElement jsonVariation(String featureKey, LDUser user, JsonElement defaultValue) {
    return evaluate(featureKey, user, LDValue.unsafeFromJsonElement(defaultValue), false).asUnsafeJsonElement();
  }

  @Override
  public LDValue jsonValueVariation(String featureKey, LDUser user, LDValue defaultValue) {
    return evaluate(featureKey, user, defaultValue == null ? LDValue.ofNull() : defaultValue, false);
  }

  @Override
  public EvaluationDetail<Boolean> boolVariationDetail(String featureKey, LDUser user, boolean defaultValue) {
    Evaluator.EvalResult result = evaluateInternal(featureKey, user, LDValue.of(defaultValue), true,
         EventFactory.DEFAULT_WITH_REASONS);
     return EvaluationDetail.fromValue(result.getValue().booleanValue(),
         result.getVariationIndex(), result.getReason());
  }

  @Override
  public EvaluationDetail<Integer> intVariationDetail(String featureKey, LDUser user, int defaultValue) {
    Evaluator.EvalResult result = evaluateInternal(featureKey, user, LDValue.of(defaultValue), true,
         EventFactory.DEFAULT_WITH_REASONS);
    return EvaluationDetail.fromValue(result.getValue().intValue(),
        result.getVariationIndex(), result.getReason());
  }

  @Override
  public EvaluationDetail<Double> doubleVariationDetail(String featureKey, LDUser user, double defaultValue) {
    Evaluator.EvalResult result = evaluateInternal(featureKey, user, LDValue.of(defaultValue), true,
         EventFactory.DEFAULT_WITH_REASONS);
    return EvaluationDetail.fromValue(result.getValue().doubleValue(),
        result.getVariationIndex(), result.getReason());
  }

  @Override
  public EvaluationDetail<String> stringVariationDetail(String featureKey, LDUser user, String defaultValue) {
    Evaluator.EvalResult result = evaluateInternal(featureKey, user, LDValue.of(defaultValue), true,
         EventFactory.DEFAULT_WITH_REASONS);
    return EvaluationDetail.fromValue(result.getValue().stringValue(),
        result.getVariationIndex(), result.getReason());
  }

  @SuppressWarnings("deprecation")
  @Override
  public EvaluationDetail<JsonElement> jsonVariationDetail(String featureKey, LDUser user, JsonElement defaultValue) {
    Evaluator.EvalResult result = evaluateInternal(featureKey, user, LDValue.unsafeFromJsonElement(defaultValue), false,
         EventFactory.DEFAULT_WITH_REASONS);
    return EvaluationDetail.fromValue(result.getValue().asUnsafeJsonElement(),
        result.getVariationIndex(), result.getReason());
  }

  @Override
  public EvaluationDetail<LDValue> jsonValueVariationDetail(String featureKey, LDUser user, LDValue defaultValue) {
    Evaluator.EvalResult result = evaluateInternal(featureKey, user, defaultValue == null ? LDValue.ofNull() : defaultValue, false, EventFactory.DEFAULT_WITH_REASONS);
    return EvaluationDetail.fromValue(result.getValue(), result.getVariationIndex(), result.getReason());
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

  private LDValue evaluate(String featureKey, LDUser user, LDValue defaultValue, boolean checkType) {
    return evaluateInternal(featureKey, user, defaultValue, checkType, EventFactory.DEFAULT).getValue();
  }
  
  private Evaluator.EvalResult errorResult(EvaluationReason.ErrorKind errorKind, final LDValue defaultValue) {
    return new Evaluator.EvalResult(defaultValue, null, EvaluationReason.error(errorKind));
  }
  
  private Evaluator.EvalResult evaluateInternal(String featureKey, LDUser user, LDValue defaultValue, boolean checkType,
      EventFactory eventFactory) {
    if (!initialized()) {
      if (featureStore.initialized()) {
        logger.warn("Evaluation called before client initialized for feature flag \"{}\"; using last known values from feature store", featureKey);
      } else {
        logger.warn("Evaluation called before client initialized for feature flag \"{}\"; feature store unavailable, returning default value", featureKey);
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue,
            EvaluationReason.ErrorKind.CLIENT_NOT_READY));
        return errorResult(EvaluationReason.ErrorKind.CLIENT_NOT_READY, defaultValue);
      }
    }

    DataModel.FeatureFlag featureFlag = null;
    try {
      featureFlag = featureStore.get(FEATURES, featureKey);
      if (featureFlag == null) {
        logger.info("Unknown feature flag \"{}\"; returning default value", featureKey);
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue,
            EvaluationReason.ErrorKind.FLAG_NOT_FOUND));
        return errorResult(EvaluationReason.ErrorKind.FLAG_NOT_FOUND, defaultValue);
      }
      if (user == null || user.getKeyAsString() == null) {
        logger.warn("Null user or null user key when evaluating flag \"{}\"; returning default value", featureKey);
        sendFlagRequestEvent(eventFactory.newDefaultFeatureRequestEvent(featureFlag, user, defaultValue,
            EvaluationReason.ErrorKind.USER_NOT_SPECIFIED));
        return errorResult(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED, defaultValue);
      }
      if (user.getKeyAsString().isEmpty()) {
        logger.warn("User key is blank. Flag evaluation will proceed, but the user will not be stored in LaunchDarkly");
      }
      Evaluator.EvalResult evalResult = evaluator.evaluate(featureFlag, user, eventFactory);
      for (Event.FeatureRequest event : evalResult.getPrerequisiteEvents()) {
        eventProcessor.sendEvent(event);
      }
      if (evalResult.isDefault()) {
        evalResult.setValue(defaultValue);
      } else {
        LDValue value = evalResult.getValue();
        if (checkType && value != null && !value.isNull() && defaultValue.getType() != value.getType()) {
          logger.error("Feature flag evaluation expected result as {}, but got {}", defaultValue.getType(), value.getType());
          sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue,
              EvaluationReason.ErrorKind.WRONG_TYPE));
          return errorResult(EvaluationReason.ErrorKind.WRONG_TYPE, defaultValue);
        }
      }
      sendFlagRequestEvent(eventFactory.newFeatureRequestEvent(featureFlag, user, evalResult, defaultValue));
      return evalResult;
    } catch (Exception e) {
      logger.error("Encountered exception while evaluating feature flag \"{}\": {}", featureKey, e.toString());
      logger.debug(e.toString(), e);
      if (featureFlag == null) {
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue,
            EvaluationReason.ErrorKind.EXCEPTION));
      } else {
        sendFlagRequestEvent(eventFactory.newDefaultFeatureRequestEvent(featureFlag, user, defaultValue,
            EvaluationReason.ErrorKind.EXCEPTION));
      }
      return errorResult(EvaluationReason.ErrorKind.EXCEPTION, defaultValue);
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
    if (user == null || user.getKeyAsString() == null) {
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
