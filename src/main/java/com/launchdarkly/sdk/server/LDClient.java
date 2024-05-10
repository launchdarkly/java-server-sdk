package com.launchdarkly.sdk.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogValues;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.internal.http.HttpHelpers;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.BigSegmentsConfiguration;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagTracker;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;

/**
 * A client for the LaunchDarkly API. Client instances are thread-safe. Applications should instantiate
 * a single {@code LDClient} for the lifetime of their application.
 */
public final class LDClient implements LDClientInterface {
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final String sdkKey;
  private final boolean offline;
  @VisibleForTesting
  final EvaluatorInterface evaluator;
  final EvaluatorInterface migrationEvaluator;
  final EventProcessor eventProcessor;
  final DataSource dataSource;
  final DataStore dataStore;
  private final BigSegmentStoreStatusProvider bigSegmentStoreStatusProvider;
  private final BigSegmentStoreWrapper bigSegmentStoreWrapper;
  private final DataSourceUpdateSink dataSourceUpdates;
  private final DataStoreStatusProviderImpl dataStoreStatusProvider;
  private final DataSourceStatusProviderImpl dataSourceStatusProvider;
  private final FlagTrackerImpl flagTracker;
  private final EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeBroadcaster;
  private final ScheduledExecutorService sharedExecutor;
  private final LDLogger baseLogger;
  private final LDLogger evaluationLogger;

  private static final int EXCESSIVE_INIT_WAIT_MILLIS = 60000;

  /**
   * Creates a new client instance that connects to LaunchDarkly with the default configuration.
   * <p>
   * If you need to specify any custom SDK options, use {@link LDClient#LDClient(String, LDConfig)}
   * instead.
   * <p>
   * Applications should instantiate a single instance for the lifetime of the application. In
   * unusual cases where an application needs to evaluate feature flags from different LaunchDarkly
   * projects or environments, you may create multiple clients, but they should still be retained
   * for the lifetime of the application rather than created per request or per thread.
   * <p>
   * The client will begin attempting to connect to LaunchDarkly as soon as you call the constructor.
   * The constructor will return when it successfully connects, or when the default timeout of 5 seconds
   * expires, whichever comes first. If it has not succeeded in connecting when the timeout elapses,
   * you will receive the client in an uninitialized state where feature flags will return default
   * values; it will still continue trying to connect in the background. You can detect whether
   * initialization has succeeded by calling {@link #isInitialized()}. If you prefer to customize
   * this behavior, use {@link LDClient#LDClient(String, LDConfig)} instead.
   * <p>
   * For rules regarding the throwing of unchecked exceptions for error conditions, see
   * {@link LDClient#LDClient(String, LDConfig)}.
   *
   * @param sdkKey the SDK key for your LaunchDarkly environment
   * @throws IllegalArgumentException if a parameter contained a grossly malformed value;
   *                                  for security reasons, in case of an illegal SDK key, the exception message does
   *                                  not include the key
   * @throws NullPointerException     if a non-nullable parameter was null
   * @see LDClient#LDClient(String, LDConfig)
   */
  public LDClient(String sdkKey) {
    // COVERAGE: this constructor cannot be called in unit tests because it uses the default base
    // URI and will attempt to make a live connection to LaunchDarkly.
    this(sdkKey, LDConfig.DEFAULT);
  }

  private static DataModel.FeatureFlag getFlag(DataStore store, String key) {
    ItemDescriptor item = store.get(FEATURES, key);
    return item == null ? null : (DataModel.FeatureFlag) item.getItem();
  }

  private static DataModel.Segment getSegment(DataStore store, String key) {
    ItemDescriptor item = store.get(SEGMENTS, key);
    return item == null ? null : (DataModel.Segment) item.getItem();
  }

  /**
   * Creates a new client to connect to LaunchDarkly with a custom configuration.
   * <p>
   * This constructor can be used to configure advanced SDK features; see {@link LDConfig.Builder}.
   * <p>
   * Applications should instantiate a single instance for the lifetime of the application. In
   * unusual cases where an application needs to evaluate feature flags from different LaunchDarkly
   * projects or environments, you may create multiple clients, but they should still be retained
   * for the lifetime of the application rather than created per request or per thread.
   * <p>
   * Unless it is configured to be offline with {@link LDConfig.Builder#offline(boolean)} or
   * {@link Components#externalUpdatesOnly()}, the client will begin attempting to connect to
   * LaunchDarkly as soon as you call the constructor. The constructor will return when it successfully
   * connects, or when the timeout set by {@link LDConfig.Builder#startWait(java.time.Duration)} (default:
   * 5 seconds) expires, whichever comes first. If it has not succeeded in connecting when the timeout
   * elapses, you will receive the client in an uninitialized state where feature flags will return
   * default values; it will still continue trying to connect in the background. You can detect
   * whether initialization has succeeded by calling {@link #isInitialized()}.
   * <p>
   * If you prefer to have the constructor return immediately, and then wait for initialization to finish
   * at some other point, you can use {@link #getDataSourceStatusProvider()} as follows:
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .startWait(Duration.ZERO)
   *         .build();
   *     LDClient client = new LDClient(sdkKey, config);
   *
   *     // later, when you want to wait for initialization to finish:
   *     boolean inited = client.getDataSourceStatusProvider().waitFor(
   *         DataSourceStatusProvider.State.VALID, Duration.ofSeconds(10));
   *     if (!inited) {
   *         // do whatever is appropriate if initialization has timed out
   *     }
   * </code></pre>
   * <p>
   * This constructor can throw unchecked exceptions if it is immediately apparent that
   * the SDK cannot work with these parameters. For instance, if the SDK key contains a
   * non-printable character that cannot be used in an HTTP header, it will throw an
   * {@link IllegalArgumentException} since the SDK key is normally sent to LaunchDarkly
   * in an HTTP header and no such value could possibly be valid. Similarly, a null
   * value for a non-nullable parameter may throw a {@link NullPointerException}. The
   * constructor will not throw an exception for any error condition that could only be
   * detected after making a request to LaunchDarkly (such as an SDK key that is simply
   * wrong despite being valid ASCII, so it is invalid but not illegal); those are logged
   * and treated as an unsuccessful initialization, as described above.
   *
   * @param sdkKey the SDK key for your LaunchDarkly environment
   * @param config a client configuration object
   * @throws IllegalArgumentException if a parameter contained a grossly malformed value;
   *                                  for security reasons, in case of an illegal SDK key, the exception message does
   *                                  not include the key
   * @throws NullPointerException     if a non-nullable parameter was null
   * @see LDClient#LDClient(String, LDConfig)
   */
  public LDClient(String sdkKey, LDConfig config) {
    checkNotNull(config, "config must not be null");
    this.sdkKey = checkNotNull(sdkKey, "sdkKey must not be null");
    if (!HttpHelpers.isAsciiHeaderValue(sdkKey)) {
      throw new IllegalArgumentException("SDK key contained an invalid character");
    }
    this.offline = config.offline;

    this.sharedExecutor = createSharedExecutor(config);

    final ClientContextImpl context = ClientContextImpl.fromConfig(
        sdkKey,
        config,
        sharedExecutor
    );
    this.baseLogger = context.getBaseLogger();
    this.evaluationLogger = this.baseLogger.subLogger(Loggers.EVALUATION_LOGGER_NAME);

    this.eventProcessor = config.events.build(context);

    EventBroadcasterImpl<BigSegmentStoreStatusProvider.StatusListener, BigSegmentStoreStatusProvider.Status> bigSegmentStoreStatusNotifier =
        EventBroadcasterImpl.forBigSegmentStoreStatus(sharedExecutor, baseLogger);
    BigSegmentsConfiguration bigSegmentsConfig = config.bigSegments.build(context);
    if (bigSegmentsConfig.getStore() != null) {
      bigSegmentStoreWrapper = new BigSegmentStoreWrapper(bigSegmentsConfig, bigSegmentStoreStatusNotifier, sharedExecutor,
          this.baseLogger.subLogger(Loggers.BIG_SEGMENTS_LOGGER_NAME));
    } else {
      bigSegmentStoreWrapper = null;
    }
    bigSegmentStoreStatusProvider = new BigSegmentStoreStatusProviderImpl(bigSegmentStoreStatusNotifier, bigSegmentStoreWrapper);

    EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> dataStoreStatusNotifier =
        EventBroadcasterImpl.forDataStoreStatus(sharedExecutor, baseLogger);
    DataStoreUpdatesImpl dataStoreUpdates = new DataStoreUpdatesImpl(dataStoreStatusNotifier);
    this.dataStore = config.dataStore.build(context.withDataStoreUpdateSink(dataStoreUpdates));

    EvaluatorInterface evaluator = new InputValidatingEvaluator(dataStore, bigSegmentStoreWrapper, eventProcessor, evaluationLogger);

    // decorate evaluator with hooks if hooks were provided
    if (config.hooks.getHooks().isEmpty()) {
      this.evaluator = evaluator;
      this.migrationEvaluator = new MigrationStageEnforcingEvaluator(evaluator, evaluationLogger);
    } else {
      this.evaluator = new EvaluatorWithHooks(evaluator, config.hooks.getHooks(), this.baseLogger.subLogger(Loggers.HOOKS_LOGGER_NAME));
      this.migrationEvaluator = new EvaluatorWithHooks(new MigrationStageEnforcingEvaluator(evaluator, evaluationLogger), config.hooks.getHooks(), this.baseLogger.subLogger(Loggers.HOOKS_LOGGER_NAME));
    }

    this.flagChangeBroadcaster = EventBroadcasterImpl.forFlagChangeEvents(sharedExecutor, baseLogger);
    this.flagTracker = new FlagTrackerImpl(flagChangeBroadcaster,
        (key, ctx) -> jsonValueVariation(key, ctx, LDValue.ofNull()));

    this.dataStoreStatusProvider = new DataStoreStatusProviderImpl(this.dataStore, dataStoreUpdates);

    EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> dataSourceStatusNotifier =
        EventBroadcasterImpl.forDataSourceStatus(sharedExecutor, baseLogger);
    DataSourceUpdatesImpl dataSourceUpdates = new DataSourceUpdatesImpl(
        dataStore,
        dataStoreStatusProvider,
        flagChangeBroadcaster,
        dataSourceStatusNotifier,
        sharedExecutor,
        context.getLogging().getLogDataSourceOutageAsErrorAfter(),
        baseLogger
    );
    this.dataSourceUpdates = dataSourceUpdates;
    this.dataSource = config.dataSource.build(context.withDataSourceUpdateSink(dataSourceUpdates));
    this.dataSourceStatusProvider = new DataSourceStatusProviderImpl(dataSourceStatusNotifier, dataSourceUpdates);

    Future<Void> startFuture = dataSource.start();
    if (!config.startWait.isZero() && !config.startWait.isNegative()) {
      if (!(dataSource instanceof ComponentsImpl.NullDataSource)) {
        baseLogger.info("Waiting up to {} milliseconds for LaunchDarkly client to start...",
            config.startWait.toMillis());
        if (config.startWait.toMillis() > EXCESSIVE_INIT_WAIT_MILLIS) {
          baseLogger.warn("LaunchDarkly client created with start wait time of {} milliseconds.  We recommend a timeout of less than {} milliseconds.", config.startWait.toMillis(), EXCESSIVE_INIT_WAIT_MILLIS);
        }
      }
      try {
        startFuture.get(config.startWait.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        baseLogger.error("Timeout encountered waiting for LaunchDarkly client initialization");
      } catch (Exception e) {
        baseLogger.error("Exception encountered waiting for LaunchDarkly client initialization: {}",
            LogValues.exceptionSummary(e));
        baseLogger.debug("{}", LogValues.exceptionTrace(e));
      }
      if (!dataSource.isInitialized()) {
        baseLogger.warn("LaunchDarkly client was not successfully initialized");
      }
    }
  }

  @Override
  public boolean isInitialized() {
    return dataSource.isInitialized();
  }

  @Override
  public void track(String eventName, LDContext context) {
    trackData(eventName, context, LDValue.ofNull());
  }

  @Override
  public void trackMigration(MigrationOpTracker tracker) {
    eventProcessor.recordMigrationEvent(tracker);
  }

  @Override
  public void trackData(String eventName, LDContext context, LDValue data) {
    if (context == null) {
      baseLogger.warn("Track called with null context!");
    } else if (!context.isValid()) {
      baseLogger.warn("Track called with invalid context: " + context.getError());
    } else {
      eventProcessor.recordCustomEvent(context, eventName, data, null);
    }
  }

  @Override
  public void trackMetric(String eventName, LDContext context, LDValue data, double metricValue) {
    if (context == null) {
      baseLogger.warn("Track called with null context!");
    } else if (!context.isValid()) {
      baseLogger.warn("Track called with invalid context: " + context.getError());
    } else {
      eventProcessor.recordCustomEvent(context, eventName, data, metricValue);
    }
  }

  @Override
  public void identify(LDContext context) {
    if (context == null) {
      baseLogger.warn("Identify called with null context!");
    } else if (!context.isValid()) {
      baseLogger.warn("Identify called with invalid context: " + context.getError());
    } else {
      eventProcessor.recordIdentifyEvent(context);
    }
  }

  @Override
  public FeatureFlagsState allFlagsState(LDContext context, FlagsStateOption... options) {
    if (isOffline()) {
      evaluationLogger.debug("allFlagsState() was called when client is in offline mode.");
    }

    return evaluator.allFlagsState(context, options);
  }

  @Override
  public boolean boolVariation(String featureKey, LDContext context, boolean defaultValue) {
    return evaluator.evalAndFlag("LDClient.boolVariation", featureKey, context, LDValue.of(defaultValue), LDValueType.BOOLEAN,
        EvaluationOptions.EVENTS_WITHOUT_REASONS).getResult().getValue().booleanValue();
  }

  @Override
  public int intVariation(String featureKey, LDContext context, int defaultValue) {
    return evaluator.evalAndFlag("LDClient.intVariation", featureKey, context, LDValue.of(defaultValue), LDValueType.NUMBER,
        EvaluationOptions.EVENTS_WITHOUT_REASONS).getResult().getValue().intValue();
  }

  @Override
  public double doubleVariation(String featureKey, LDContext context, double defaultValue) {
    return evaluator.evalAndFlag("LDClient.doubleVariation", featureKey, context, LDValue.of(defaultValue), LDValueType.NUMBER,
        EvaluationOptions.EVENTS_WITHOUT_REASONS).getResult().getValue().doubleValue();
  }

  @Override
  public String stringVariation(String featureKey, LDContext context, String defaultValue) {
    return evaluator.evalAndFlag("LDClient.stringVariation", featureKey, context, LDValue.of(defaultValue), LDValueType.STRING,
        EvaluationOptions.EVENTS_WITHOUT_REASONS).getResult().getValue().stringValue();

  }

  @Override
  public LDValue jsonValueVariation(String featureKey, LDContext context, LDValue defaultValue) {
    return evaluator.evalAndFlag("LDClient.jsonValueVariation", featureKey, context, LDValue.normalize(defaultValue), null,
        EvaluationOptions.EVENTS_WITHOUT_REASONS).getResult().getValue();
  }

  @Override
  public EvaluationDetail<Boolean> boolVariationDetail(String featureKey, LDContext context, boolean defaultValue) {
    return evaluator.evalAndFlag("LDClient.boolVariationDetail", featureKey, context, LDValue.of(defaultValue), LDValueType.BOOLEAN,
        EvaluationOptions.EVENTS_WITH_REASONS).getResult().getAsBoolean();
  }

  @Override
  public EvaluationDetail<Integer> intVariationDetail(String featureKey, LDContext context, int defaultValue) {
    return evaluator.evalAndFlag("LDClient.intVariationDetail", featureKey, context, LDValue.of(defaultValue), LDValueType.NUMBER,
        EvaluationOptions.EVENTS_WITH_REASONS).getResult().getAsInteger();
  }

  @Override
  public EvaluationDetail<Double> doubleVariationDetail(String featureKey, LDContext context, double defaultValue) {
    return evaluator.evalAndFlag("LDClient.doubleVariationDetail", featureKey, context, LDValue.of(defaultValue), LDValueType.NUMBER,
        EvaluationOptions.EVENTS_WITH_REASONS).getResult().getAsDouble();
  }

  @Override
  public EvaluationDetail<String> stringVariationDetail(String featureKey, LDContext context, String defaultValue) {
    return evaluator.evalAndFlag("LDClient.stringVariationDetail", featureKey, context, LDValue.of(defaultValue), LDValueType.STRING,
        EvaluationOptions.EVENTS_WITH_REASONS).getResult().getAsString();
  }

  @Override
  public EvaluationDetail<LDValue> jsonValueVariationDetail(String featureKey, LDContext context, LDValue defaultValue) {
    return evaluator.evalAndFlag("LDClient.jsonValueVariationDetail", featureKey, context, LDValue.normalize(defaultValue), null,
        EvaluationOptions.EVENTS_WITH_REASONS).getResult().getAnyType();
  }

  @Override
  public MigrationVariation migrationVariation(String key, LDContext context, MigrationStage defaultStage) {
    // The migration evaluator is decorated with logic that will enforce the result is for a recognized migration
    // stage or an error result is returned with the default stage value.  This decorator was added as part of
    // the Hooks implementation to ensure that the Hook would be given the result after that migration stage
    // enforcement.
    EvalResultAndFlag res = migrationEvaluator.evalAndFlag("LDClient.migrationVariation", key, context, LDValue.of(defaultStage.toString()), LDValueType.STRING,
        EvaluationOptions.EVENTS_WITHOUT_REASONS);

    // since evaluation result inner types are boxed primitives, it is necessary to still make this mapping to the
    // MigrationState type.
    EvaluationDetail<String> resDetail = res.getResult().getAsString();
    MigrationStage stageChecked = MigrationStage.of(resDetail.getValue(), defaultStage);

    long checkRatio = 1;

    if (res.getFlag() != null &&
        res.getFlag().getMigration() != null &&
        res.getFlag().getMigration().getCheckRatio() != null) {
      checkRatio = res.getFlag().getMigration().getCheckRatio();
    }

    MigrationOpTracker tracker = new MigrationOpTracker(key, res.getFlag(), resDetail, defaultStage,
        stageChecked, context, checkRatio, baseLogger);
    return new MigrationVariation(stageChecked, tracker);
  }

  @Override
  public boolean isFlagKnown(String featureKey) {
    if (!isInitialized()) {
      if (dataStore.isInitialized()) {
        baseLogger.warn("isFlagKnown called before client initialized for feature flag \"{}\"; using last known values from data store", featureKey);
      } else {
        baseLogger.warn("isFlagKnown called before client initialized for feature flag \"{}\"; data store unavailable, returning false", featureKey);
        return false;
      }
    }

    try {
      if (getFlag(dataStore, featureKey) != null) {
        return true;
      }
    } catch (Exception e) {
      baseLogger.error("Encountered exception while calling isFlagKnown for feature flag \"{}\": {}", featureKey,
          LogValues.exceptionSummary(e));
      baseLogger.debug("{}", LogValues.exceptionTrace(e));
    }

    return false;
  }

  @Override
  public FlagTracker getFlagTracker() {
    return flagTracker;
  }

  @Override
  public BigSegmentStoreStatusProvider getBigSegmentStoreStatusProvider() {
    return bigSegmentStoreStatusProvider;
  }

  @Override
  public DataStoreStatusProvider getDataStoreStatusProvider() {
    return dataStoreStatusProvider;
  }

  @Override
  public LDLogger getLogger() {
    return baseLogger;
  }

  @Override
  public DataSourceStatusProvider getDataSourceStatusProvider() {
    return dataSourceStatusProvider;
  }

  /**
   * Shuts down the client and releases any resources it is using.
   * <p>
   * Unless it is offline, the client will attempt to deliver any pending analytics events before
   * closing.
   */
  @Override
  public void close() throws IOException {
    baseLogger.info("Closing LaunchDarkly Client");
    this.dataStore.close();
    this.eventProcessor.close();
    this.dataSource.close();
    this.dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.OFF, null);
    if (this.bigSegmentStoreWrapper != null) {
      this.bigSegmentStoreWrapper.close();
    }
    this.sharedExecutor.shutdownNow();
  }

  @Override
  public void flush() {
    this.eventProcessor.flush();
  }

  @Override
  public boolean isOffline() {
    return offline;
  }

  @Override
  public String secureModeHash(LDContext context) {
    if (context == null || !context.isValid()) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(sdkKey.getBytes(), HMAC_ALGORITHM));
      return Hex.encodeHexString(mac.doFinal(context.getFullyQualifiedKey().getBytes("UTF8")));
    } catch (InvalidKeyException | UnsupportedEncodingException | NoSuchAlgorithmException e) {
      // COVERAGE: there is no way to cause these errors in a unit test.
      baseLogger.error("Could not generate secure mode hash: {}", LogValues.exceptionSummary(e));
      baseLogger.debug("{}", LogValues.exceptionTrace(e));
    }
    return null;
  }

  /**
   * Returns the current version string of the client library.
   *
   * @return a version string conforming to Semantic Versioning (http://semver.org)
   */
  @Override
  public String version() {
    return Version.SDK_VERSION;
  }

  // This executor is used for a variety of SDK tasks such as flag change events, checking the data store
  // status after an outage, and the poll task in polling mode. These are all tasks that we do not expect
  // to be executing frequently so that it is acceptable to use a single thread to execute them one at a
  // time rather than a thread pool, thus reducing the number of threads spawned by the SDK. This also
  // has the benefit of producing predictable delivery order for event listener notifications.
  private ScheduledExecutorService createSharedExecutor(LDConfig config) {
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("LaunchDarkly-tasks-%d")
        .setPriority(config.threadPriority)
        .build();
    return Executors.newSingleThreadScheduledExecutor(threadFactory);
  }
}
