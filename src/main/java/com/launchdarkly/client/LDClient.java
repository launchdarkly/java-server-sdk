package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.launchdarkly.client.Components.NullUpdateProcessor;
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
import static com.launchdarkly.client.VersionedDataKind.FEATURES;

/**
 * A client for the LaunchDarkly API. Client instances are thread-safe. Applications should instantiate
 * a single {@code LDClient} for the lifetime of their application.
 */
public final class LDClient implements LDClientInterface {
  // Package-private so other classes can log under the top-level logger's tag
  static final Logger logger = LoggerFactory.getLogger(LDClient.class);
  
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  static final String CLIENT_VERSION = getClientVersion();

  private final LDConfig config;
  private final String sdkKey;
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
    this.config = new LDConfig(checkNotNull(config, "config must not be null"));
    this.sdkKey = checkNotNull(sdkKey, "sdkKey must not be null");

    FeatureStore store;
    if (this.config.deprecatedFeatureStore != null) {
      store = this.config.deprecatedFeatureStore;
      // The following line is for backward compatibility with the obsolete mechanism by which the
      // caller could pass in a FeatureStore implementation instance that we did not create.  We
      // were not disposing of that instance when the client was closed, so we should continue not
      // doing so until the next major version eliminates that mechanism.  We will always dispose
      // of instances that we created ourselves from a factory.
      this.shouldCloseFeatureStore = false;
    } else {
      FeatureStoreFactory factory = config.dataStoreFactory == null ?
          Components.inMemoryDataStore() : config.dataStoreFactory;
      store = factory.createFeatureStore();
      this.shouldCloseFeatureStore = true;
    }
    this.featureStore = new FeatureStoreClientWrapper(store);

    EventProcessorFactory epFactory = this.config.eventProcessorFactory == null ?
        Components.defaultEventProcessor() : this.config.eventProcessorFactory;

    DiagnosticAccumulator diagnosticAccumulator = null;
    // Do not create accumulator if config has specified is opted out, or if epFactory doesn't support diagnostics
    if (!this.config.diagnosticOptOut && epFactory instanceof EventProcessorFactoryWithDiagnostics) {
      diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId(sdkKey));
    }

    if (epFactory instanceof EventProcessorFactoryWithDiagnostics) {
      EventProcessorFactoryWithDiagnostics epwdFactory = ((EventProcessorFactoryWithDiagnostics) epFactory);
      this.eventProcessor = epwdFactory.createEventProcessor(sdkKey, this.config, diagnosticAccumulator);
    } else {
      this.eventProcessor = epFactory.createEventProcessor(sdkKey, this.config);
    }

    @SuppressWarnings("deprecation") // defaultUpdateProcessor will be replaced by streamingDataSource once the deprecated config.stream is removed
    UpdateProcessorFactory upFactory = config.dataSourceFactory == null ?
        Components.defaultUpdateProcessor() : config.dataSourceFactory;
    
    if (upFactory instanceof UpdateProcessorFactoryWithDiagnostics) {
      UpdateProcessorFactoryWithDiagnostics upwdFactory = ((UpdateProcessorFactoryWithDiagnostics) upFactory);
      this.updateProcessor = upwdFactory.createUpdateProcessor(sdkKey, this.config, featureStore, diagnosticAccumulator);
    } else {
      this.updateProcessor = upFactory.createUpdateProcessor(sdkKey, this.config, featureStore);
    }

    Future<Void> startFuture = updateProcessor.start();
    if (this.config.startWaitMillis > 0L) {
      if (!(updateProcessor instanceof NullUpdateProcessor)) {
        logger.info("Waiting up to " + this.config.startWaitMillis + " milliseconds for LaunchDarkly client to start...");
      }
      try {
        startFuture.get(this.config.startWaitMillis, TimeUnit.MILLISECONDS);
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
    Map<String, FeatureFlag> flags = featureStore.all(FEATURES);
    for (Map.Entry<String, FeatureFlag> entry : flags.entrySet()) {
      FeatureFlag flag = entry.getValue();
      if (clientSideOnly && !flag.isClientSide()) {
        continue;
      }
      try {
        EvaluationDetail<LDValue> result = flag.evaluate(user, featureStore, EventFactory.DEFAULT).getDetails();
        builder.addFlag(flag, result);
      } catch (Exception e) {
        logger.error("Exception caught for feature flag \"{}\" when evaluating all flags: {}", entry.getKey(), e.toString());
        logger.debug(e.toString(), e);
        builder.addFlag(entry.getValue(), EvaluationDetail.fromValue(LDValue.ofNull(), null, EvaluationReason.exception(e)));
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
     EvaluationDetail<LDValue> details = evaluateDetail(featureKey, user, LDValue.of(defaultValue), true,
         EventFactory.DEFAULT_WITH_REASONS);
     return EvaluationDetail.fromValue(details.getValue().booleanValue(),
         details.getVariationIndex(), details.getReason());
  }

  @Override
  public EvaluationDetail<Integer> intVariationDetail(String featureKey, LDUser user, int defaultValue) {
    EvaluationDetail<LDValue> details = evaluateDetail(featureKey, user, LDValue.of(defaultValue), true,
         EventFactory.DEFAULT_WITH_REASONS);
    return EvaluationDetail.fromValue(details.getValue().intValue(),
        details.getVariationIndex(), details.getReason());
  }

  @Override
  public EvaluationDetail<Double> doubleVariationDetail(String featureKey, LDUser user, double defaultValue) {
    EvaluationDetail<LDValue> details = evaluateDetail(featureKey, user, LDValue.of(defaultValue), true,
         EventFactory.DEFAULT_WITH_REASONS);
    return EvaluationDetail.fromValue(details.getValue().doubleValue(),
        details.getVariationIndex(), details.getReason());
  }

  @Override
  public EvaluationDetail<String> stringVariationDetail(String featureKey, LDUser user, String defaultValue) {
    EvaluationDetail<LDValue> details = evaluateDetail(featureKey, user, LDValue.of(defaultValue), true,
         EventFactory.DEFAULT_WITH_REASONS);
    return EvaluationDetail.fromValue(details.getValue().stringValue(),
        details.getVariationIndex(), details.getReason());
  }

  @SuppressWarnings("deprecation")
  @Override
  public EvaluationDetail<JsonElement> jsonVariationDetail(String featureKey, LDUser user, JsonElement defaultValue) {
    EvaluationDetail<LDValue> details = evaluateDetail(featureKey, user, LDValue.unsafeFromJsonElement(defaultValue), false,
         EventFactory.DEFAULT_WITH_REASONS);
    return EvaluationDetail.fromValue(details.getValue().asUnsafeJsonElement(),
        details.getVariationIndex(), details.getReason());
  }

  @Override
  public EvaluationDetail<LDValue> jsonValueVariationDetail(String featureKey, LDUser user, LDValue defaultValue) {
    return evaluateDetail(featureKey, user, defaultValue == null ? LDValue.ofNull() : defaultValue, false, EventFactory.DEFAULT_WITH_REASONS);
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
    return evaluateDetail(featureKey, user, defaultValue, checkType, EventFactory.DEFAULT).getValue();
  }
  
  private EvaluationDetail<LDValue> evaluateDetail(String featureKey, LDUser user, LDValue defaultValue,
      boolean checkType, EventFactory eventFactory) {
    EvaluationDetail<LDValue> details = evaluateInternal(featureKey, user, defaultValue, eventFactory);
    if (details.getValue() != null && checkType) {
      if (defaultValue.getType() != details.getValue().getType()) {
        logger.error("Feature flag evaluation expected result as {}, but got {}", defaultValue.getType(), details.getValue().getType());
        return EvaluationDetail.error(EvaluationReason.ErrorKind.WRONG_TYPE, defaultValue);
      }
    }
    return details;
  }
  
  private EvaluationDetail<LDValue> evaluateInternal(String featureKey, LDUser user, LDValue defaultValue, EventFactory eventFactory) {
    if (!initialized()) {
      if (featureStore.initialized()) {
        logger.warn("Evaluation called before client initialized for feature flag \"{}\"; using last known values from feature store", featureKey);
      } else {
        logger.warn("Evaluation called before client initialized for feature flag \"{}\"; feature store unavailable, returning default value", featureKey);
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue,
            EvaluationReason.ErrorKind.CLIENT_NOT_READY));
        return EvaluationDetail.error(EvaluationReason.ErrorKind.CLIENT_NOT_READY, defaultValue);
      }
    }

    FeatureFlag featureFlag = null;
    try {
      featureFlag = featureStore.get(FEATURES, featureKey);
      if (featureFlag == null) {
        logger.info("Unknown feature flag \"{}\"; returning default value", featureKey);
        sendFlagRequestEvent(eventFactory.newUnknownFeatureRequestEvent(featureKey, user, defaultValue,
            EvaluationReason.ErrorKind.FLAG_NOT_FOUND));
        return EvaluationDetail.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND, defaultValue);
      }
      if (user == null || user.getKeyAsString() == null) {
        logger.warn("Null user or null user key when evaluating flag \"{}\"; returning default value", featureKey);
        sendFlagRequestEvent(eventFactory.newDefaultFeatureRequestEvent(featureFlag, user, defaultValue,
            EvaluationReason.ErrorKind.USER_NOT_SPECIFIED));
        return EvaluationDetail.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED, defaultValue);
      }
      if (user.getKeyAsString().isEmpty()) {
        logger.warn("User key is blank. Flag evaluation will proceed, but the user will not be stored in LaunchDarkly");
      }
      FeatureFlag.EvalResult evalResult = featureFlag.evaluate(user, featureStore, eventFactory);
      for (Event.FeatureRequest event : evalResult.getPrerequisiteEvents()) {
        eventProcessor.sendEvent(event);
      }
      EvaluationDetail<LDValue> details = evalResult.getDetails();
      if (details.isDefaultValue()) {
        details = EvaluationDetail.fromValue(defaultValue, null, details.getReason());
      }
      sendFlagRequestEvent(eventFactory.newFeatureRequestEvent(featureFlag, user, details, defaultValue));
      return details;
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
      return EvaluationDetail.fromValue(defaultValue, null, EvaluationReason.exception(e));
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
