package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;

import com.launchdarkly.sdk.server.FeatureFlagsState;
import com.launchdarkly.sdk.server.FlagsStateOption;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.MigrationOpTracker;
import com.launchdarkly.sdk.server.MigrationStage;
import com.launchdarkly.sdk.server.MigrationVariation;
import com.launchdarkly.sdk.server.migrations.Migration;

import java.io.Closeable;
import java.io.IOException;

/**
 * This interface defines the public methods of {@link LDClient}.
 * <p>
 * Applications will normally interact directly with {@link LDClient}, and must use its constructor to
 * initialize the SDK, but being able to refer to it indirectly via an interface may be helpful in test
 * scenarios (mocking) or for some dependency injection frameworks.
 */
public interface LDClientInterface extends Closeable {
  /**
   * Tests whether the client is ready to be used.
   * @return true if the client is ready, or false if it is still initializing
   */
  boolean isInitialized();

  /**
   * Tracks that an application-defined event occurred.
   * <p>
   * This method creates a "custom" analytics event containing the specified event name (key)
   * and context properties. You may attach arbitrary data or a metric value to the event by calling
   * {@link #trackData(String, LDContext, LDValue)} or {@link #trackMetric(String, LDContext, LDValue, double)}
   * instead.
   * <p>
   * Note that event delivery is asynchronous, so the event may not actually be sent until
   * later; see {@link #flush()}.
   *
   * @param eventName the name of the event
   * @param context   the context associated with the event
   * @see #trackData(String, LDContext, LDValue)
   * @see #trackMetric(String, LDContext, LDValue, double)
   * @since 6.0.0
   */
  void track(String eventName, LDContext context);

  /**
   * Tracks that an application-defined event occurred.
   * <p>
   * This method creates a "custom" analytics event containing the specified event name (key),
   * context properties, and optional data. If you do not need custom data, pass {@link LDValue#ofNull()}
   * for the last parameter or simply omit the parameter. You may attach a metric value to the event by
   * calling {@link #trackMetric(String, LDContext, LDValue, double)} instead.
   * <p>
   * Note that event delivery is asynchronous, so the event may not actually be sent until
   * later; see {@link #flush()}.
   *
   * @param eventName the name of the event
   * @param context   the context associated with the event
   * @param data      additional data associated with the event, if any
   * @since 6.0.0
   * @see #track(String, LDContext)
   * @see #trackMetric(String, LDContext, LDValue, double)
   */
  void trackData(String eventName, LDContext context, LDValue data);

  /**
   * Tracks that an application-defined event occurred, and provides an additional numeric value for
   * custom metrics.
   * <p>
   * This value is used by the LaunchDarkly experimentation feature in numeric custom metrics,
   * and will also be returned as part of the custom event for Data Export.
   * <p>
   * Note that event delivery is asynchronous, so the event may not actually be sent until
   * later; see {@link #flush()}.
   * 
   * @param eventName the name of the event
   * @param context   the context associated with the event
   * @param data      an {@link LDValue} containing additional data associated with the event; if not applicable,
   * you may pass either {@code null} or {@link LDValue#ofNull()}
   * @param metricValue a numeric value used by the LaunchDarkly experimentation feature in numeric custom
   * metrics
   * @since 4.9.0
   * @see #track(String, LDContext)
   * @see #trackData(String, LDContext, LDValue)
   */
  void trackMetric(String eventName, LDContext context, LDValue data, double metricValue);

  /**
   * Track the details of a migration.
   *
   * @param tracker Migration tracker which was used to track details of the migration operation.
   * @since 7.0.0
   */
  void trackMigration(MigrationOpTracker tracker);

  /**
   * Reports details about an evaluation context.
   * <p>
   * This method simply creates an analytics event containing the context properties, to
   * that LaunchDarkly will know about that context if it does not already.
   * <p>
   * Calling any evaluation method, such as {@link #boolVariation(String, LDContext, boolean)},
   * also sends the context information to LaunchDarkly (if events are enabled), so you only
   * need to use this method if you want to identify the context without evaluating a flag.
   * <p>
   * Note that event delivery is asynchronous, so the event may not actually be sent until
   * later; see {@link #flush()}.
   *
   * @param context the context to register
   * @since 6.0.0
   */
  void identify(LDContext context);

  /**
   * Returns an object that encapsulates the state of all feature flags for a given context, which can be
   * passed to front-end code.
   * <p>
   * The object returned by this method contains the flag values as well as other metadata that
   * is used by the LaunchDarkly JavaScript client, so it can be used for
   * <a href="https://docs.launchdarkly.com/sdk/features/bootstrapping#javascript">bootstrapping</a>.
   * <p>
   * This method will not send analytics events back to LaunchDarkly.
   *  
   * @param context the evaluation context
   * @param options optional {@link FlagsStateOption} values affecting how the state is computed - for
   * instance, to filter the set of flags to only include the client-side-enabled ones
   * @return a {@link FeatureFlagsState} object (will never be null; see {@link FeatureFlagsState#isValid()}
   * @since 6.0.0
   */
  FeatureFlagsState allFlagsState(LDContext context, FlagsStateOption... options);

  /**
   * Calculates the boolean value of a feature flag for a given context.
   * <p>
   * If the flag variation does not have a boolean value, {@code defaultValue} is returned.
   * <p>
   * If an error makes it impossible to evaluate the flag (for instance, the feature flag key
   * does not match any existing flag), {@code defaultValue} is returned.
   * 
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @since 6.0.0
   */
  boolean boolVariation(String key, LDContext context, boolean defaultValue);

  /**
   * Calculates the integer value of a feature flag for a given context.
   * <p>
   * If the flag variation has a numeric value that is not an integer, it is rounded toward zero
   * (truncated).
   * <p>
   * If the flag variation does not have a numeric value, {@code defaultValue} is returned.
   * <p>
   * If an error makes it impossible to evaluate the flag (for instance, the feature flag key
   * does not match any existing flag), {@code defaultValue} is returned.
   *
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @since 6.0.0
   */
  int intVariation(String key, LDContext context, int defaultValue);

  /**
   * Calculates the floating-point numeric value of a feature flag for a given context.
   * <p>
   * If the flag variation does not have a numeric value, {@code defaultValue} is returned.
   * <p>
   * If an error makes it impossible to evaluate the flag (for instance, the feature flag key
   * does not match any existing flag), {@code defaultValue} is returned.
   *
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @since 6.0.0
   */
  double doubleVariation(String key, LDContext context, double defaultValue);

  /**
   * Calculates the string value of a feature flag for a given context.
   * <p>
   * If the flag variation does not have a string value, {@code defaultValue} is returned.
   * <p>
   * If an error makes it impossible to evaluate the flag (for instance, the feature flag key
   * does not match any existing flag), {@code defaultValue} is returned.
   *
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @since 6.0.0
   */
  String stringVariation(String key, LDContext context, String defaultValue);

  /**
   * Calculates the value of a feature flag for a given context as any JSON value type.
   * <p>
   * The type {@link LDValue} is used to represent any of the value types that can
   * exist in JSON. Use {@link LDValue} methods to examine its type and value.
   *
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @since 6.0.0
   */
  LDValue jsonValueVariation(String key, LDContext context, LDValue defaultValue);

  /**
   * Calculates the boolean value of a feature flag for a given context, and returns an object that
   * describes the way the value was determined.
   * <p>
   * The {@link EvaluationDetail#getReason()} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * <p>
   * The behavior is otherwise identical to {@link #boolVariation(String, LDContext, boolean)}.
   * 
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 6.0.0
   */
  EvaluationDetail<Boolean> boolVariationDetail(String key, LDContext context, boolean defaultValue);
  
  /**
   * Calculates the integer numeric value of a feature flag for a given context, and returns an object
   * that describes the way the value was determined.
   * <p>
   * The {@link EvaluationDetail#getReason()} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * <p>
   * The behavior is otherwise identical to {@link #intVariation(String, LDContext, int)}.
   * 
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 6.0.0
   */
  EvaluationDetail<Integer> intVariationDetail(String key, LDContext context, int defaultValue);
  
  /**
   * Calculates the floating-point numeric value of a feature flag for a given context, and returns an
   * object that describes the way the value was determined.
   * <p>
   * The {@link EvaluationDetail#getReason()} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * <p>
   * The behavior is otherwise identical to {@link #doubleVariation(String, LDContext, double)}.
   *
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 6.0.0
   */
  EvaluationDetail<Double> doubleVariationDetail(String key, LDContext context, double defaultValue);

  /**
   * Calculates the string value of a feature flag for a given context, and returns an object
   * that describes the way the value was determined.
   * <p>
   * The {@link EvaluationDetail#getReason()} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * <p>
   * The behavior is otherwise identical to {@link #stringVariation(String, LDContext, String)}.
   * 
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 6.0.0
   */
  EvaluationDetail<String> stringVariationDetail(String key, LDContext context, String defaultValue);

  /**
   * Calculates the value of a feature flag for a given context as any JSON value type, and returns an
   * object that describes the way the value was determined.
   * <p>
   * The {@link EvaluationDetail#getReason()} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * <p>
   * The behavior is otherwise identical to {@link #jsonValueVariation(String, LDContext, LDValue)}.
   *
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 6.0.0
   */
  EvaluationDetail<LDValue> jsonValueVariationDetail(String key, LDContext context, LDValue defaultValue);

  /**
   * Returns the migration stage of the migration feature flag for the given
   * evaluation context.
   * <p>
   * If the evaluated value of the flag cannot be converted to an LDMigrationStage, then the default
   * value will be returned and error will be logged.
   *
   * @param key          the unique key for the feature flag
   * @param context      the evaluation context
   * @param defaultStage the default stage of the migration
   * @return the current stage and a tracker which can be used to track the migration operation
   * @since 7.0.0
   */
  MigrationVariation migrationVariation(String key, LDContext context, MigrationStage defaultStage);

  /**
   * Returns true if the specified feature flag currently exists.
   * @param featureKey the unique key for the feature flag
   * @return true if the flag exists
   */
  boolean isFlagKnown(String featureKey);

  /**
   * Closes the LaunchDarkly client event processing thread. This should only
   * be called on application shutdown.
   *
   * @throws IOException if an exception is thrown by one of the underlying network services
   */
  @Override
  void close() throws IOException;

  /**
   * Flushes all pending events.
   */
  void flush();

  /**
   * Returns true if the client is in offline mode.
   * @return whether the client is in offline mode
   */
  boolean isOffline();

  /**
   * Returns an interface for tracking changes in feature flag configurations.
   * <p>
   * The {@link FlagTracker} contains methods for requesting notifications about feature flag changes using
   * an event listener model.
   *
   * @return a {@link FlagTracker}
   * @since 5.0.0
   */
  FlagTracker getFlagTracker();
  
  /**
   * Returns an interface for tracking the status of the Big Segment store.
   * <p>
   * The returned object has methods for checking whether the Big Segment store is (as far as the
   * SDK knows) currently operational and tracking changes in this status. See
   * {@link BigSegmentStoreStatusProvider} for more about this functionality.
   *
   * @return a {@link BigSegmentStoreStatusProvider}
   * @since 5.7.0
   */
  BigSegmentStoreStatusProvider getBigSegmentStoreStatusProvider();

  /**
   * Returns an interface for tracking the status of the data source.
   * <p>
   * The data source is the mechanism that the SDK uses to get feature flag configurations, such as a
   * streaming connection (the default) or poll requests. The {@link DataSourceStatusProvider} has methods
   * for checking whether the data source is (as far as the SDK knows) currently operational and tracking
   * changes in this status.
   * 
   * @return a {@link DataSourceStatusProvider}
   * @since 5.0.0
   */
  DataSourceStatusProvider getDataSourceStatusProvider();
  
  /**
   * Returns an interface for tracking the status of a persistent data store.
   * <p>
   * The {@link DataStoreStatusProvider} has methods for checking whether the data store is (as far as the
   * SDK knows) currently operational, tracking changes in this status, and getting cache statistics. These
   * are only relevant for a persistent data store; if you are using an in-memory data store, then this
   * method will return a stub object that provides no information.
   * 
   * @return a {@link DataStoreStatusProvider}
   * @since 5.0.0
   */
  DataStoreStatusProvider getDataStoreStatusProvider();

  /**
   * Returns the logger instance used by this SDK instance.
   * <p>
   * This allows for access to the logger by other LaunchDarkly components, such as the {@link Migration}
   * class.
   * <p>
   * It also allows for usage of the logger in wrapper implementations.
   * <p>
   * It is not intended for general purpose application logging.
   *
   * @return a {@link LDLogger}
   * @since 7.0.0
   */
  LDLogger getLogger();
  
  /**
   * Creates a hash string that can be used by the JavaScript SDK to identify a context.
   * <p>
   * See <a href="https://docs.launchdarkly.com/sdk/features/secure-mode#configuring-secure-mode-in-the-javascript-client-side-sdk">
   * Secure mode</a> in the JavaScript SDK Reference.
   *
   * @param context the evaluation context
   * @return the hash, or null if the hash could not be calculated
   * @since 6.0.0
   */
  String secureModeHash(LDContext context);

  /**
   * The current version string of the SDK.
   * 
   * @return a string in Semantic Versioning 2.0.0 format
   */
  String version();
}
