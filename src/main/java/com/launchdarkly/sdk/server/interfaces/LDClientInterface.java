package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.FeatureFlagsState;
import com.launchdarkly.sdk.server.FlagsStateOption;
import com.launchdarkly.sdk.server.LDClient;

import java.io.Closeable;
import java.io.IOException;

/**
 * This interface defines the public methods of {@link LDClient}.
 * <p>
 * Applications will normally interact directly with {@link LDClient}, and must use its constructor to
 * initialize the SDK, but being able to refer to it indirectly via an interface may be helpul in test
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
   * @see #track(String, LDUser)
   * @since 6.0.0
   */
  void track(String eventName, LDContext context);

  /**
   * Tracks that an application-defined event occurred.
   * <p>
   * This is equivalent to {@link #track(String, LDContext)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param eventName the name of the event
   * @param user      the user attributes
   * @see #trackData(String, LDContext, LDValue)
   * @see #trackMetric(String, LDContext, LDValue, double)
   * @see #track(String, LDContext)
   */
  default void track(String eventName, LDUser user) {
    track(eventName, LDContext.fromUser(user));
  }

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
   * @see #trackData(String, LDUser, LDValue)
   */
  void trackData(String eventName, LDContext context, LDValue data);

  /**
   * Tracks that an application-defined event occurred.
   * <p>
   * This is equivalent to {@link #trackData(String, LDContext, LDValue)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param eventName the name of the event
   * @param user      the user attributes
   * @param data      additional data associated with the event, if any
   * @since 4.8.0
   * @see #track(String, LDContext)
   * @see #trackMetric(String, LDContext, LDValue, double)
   * @see #trackData(String, LDContext, LDValue)
   */
  default void trackData(String eventName, LDUser user, LDValue data) {
    trackData(eventName, LDContext.fromUser(user), data);
  }

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
   * Tracks that an application-defined event occurred, and provides an additional numeric value for
   * custom metrics.
   * <p>
   * This is equivalent to {@link #trackMetric(String, LDContext, LDValue, double)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   * 
   * @param eventName the name of the event
   * @param user      the user attributes
   * @param data      an {@link LDValue} containing additional data associated with the event; if not applicable,
   * you may pass either {@code null} or {@link LDValue#ofNull()}
   * @param metricValue a numeric value used by the LaunchDarkly experimentation feature in numeric custom
   * metrics
   * @since 4.9.0
   * @see #track(String, LDContext)
   * @see #trackData(String, LDContext, LDValue)
   */
  default void trackMetric(String eventName, LDUser user, LDValue data, double metricValue) {
    trackMetric(eventName, LDContext.fromUser(user), data, metricValue);
  }

  /**
   * Reports details about an evaluation context.
   * <p>
   * This method simply creates an analytics event containing the context properties, to
   * that LaunchDarkly will know about that context if it does not already.
   * <p>
   * Calling any evaluation method, such as {@link #boolVariation(String, LDContext, boolean)},
   * also sends the context information to LaunchDarkly (if events are enabled), so you only
   * need to use {@link #identify(LDContext)} if you want to identify the context without
   * evaluating a flag.
   * <p>
   * Note that event delivery is asynchronous, so the event may not actually be sent until
   * later; see {@link #flush()}.
   *
   * @param context the context to register
   * @see #identify(LDUser)
   * @since 6.0.0
   */
  void identify(LDContext context);

  /**
   * Reports details about a user.
   * <p>
   * This is equivalent to {@link #identify(LDContext)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param user the user attributes
   * @see #identify(LDContext)
   */
  default void identify(LDUser user) {
    identify(LDContext.fromUser(user));
  }

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
   * @see #allFlagsState(LDUser, FlagsStateOption...)
   * @since 6.0.0
   */
  FeatureFlagsState allFlagsState(LDContext context, FlagsStateOption... options);

  /**
   * Returns an object that encapsulates the state of all feature flags for a given user, which can be
   * passed to front-end code.
   * <p>
   * This is equivalent to {@link #allFlagsState(LDContext, FlagsStateOption...)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *  
   * @param user the user attributes
   * @param options optional {@link FlagsStateOption} values affecting how the state is computed - for
   * instance, to filter the set of flags to only include the client-side-enabled ones
   * @return a {@link FeatureFlagsState} object (will never be null; see {@link FeatureFlagsState#isValid()}
   * @see #allFlagsState(LDContext, FlagsStateOption...)
   * @since 4.3.0
   */
  default FeatureFlagsState allFlagsState(LDUser user, FlagsStateOption... options) {
    return allFlagsState(LDContext.fromUser(user), options);
  }
  
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
   * @see #boolVariation(String, LDUser, boolean)
   * @since 6.0.0
   */
  boolean boolVariation(String key, LDContext context, boolean defaultValue);

  /**
   * Calculates the boolean value of a feature flag for a given user.
   * <p>
   * This is equivalent to {@link #boolVariation(String, LDContext, boolean)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   * 
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @see #boolVariation(String, LDContext, boolean)
   */
  default boolean boolVariation(String key, LDUser user, boolean defaultValue) {
    return boolVariation(key, LDContext.fromUser(user), defaultValue);
  }
  
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
   * @see #intVariation(String, LDUser, int)
   * @since 6.0.0
   */
  int intVariation(String key, LDContext context, int defaultValue);

  /**
   * Calculates the integer value of a feature flag for a given user.
   * <p>
   * This is equivalent to {@link #intVariation(String, LDContext, int)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @see #intVariation(String, LDContext, int)
   */
  default int intVariation(String key, LDUser user, int defaultValue) {
    return intVariation(key, LDContext.fromUser(user), defaultValue);
  }

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
   * @see #doubleVariation(String, LDUser, double)
   * @since 6.0.0
   */
  double doubleVariation(String key, LDContext context, double defaultValue);

  /**
   * Calculates the floating-point numeric value of a feature flag for a given context.
   * <p>
   * This is equivalent to {@link #doubleVariation(String, LDContext, double)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @see #doubleVariation(String, LDContext, double)
   */
  default double doubleVariation(String key, LDUser user, double defaultValue) {
    return doubleVariation(key, LDContext.fromUser(user), defaultValue);
  }

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
   * @see #stringVariation(String, LDUser, String)
   * @since 6.0.0
   */
  String stringVariation(String key, LDContext context, String defaultValue);

  /**
   * Calculates the string value of a feature flag for a given context.
   * <p>
   * This is equivalent to {@link #stringVariation(String, LDContext, String)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @see #stringVariation(String, LDContext, String)
   */
  default String stringVariation(String key, LDUser user, String defaultValue) {
    return stringVariation(key, LDContext.fromUser(user), defaultValue);
  }

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
   * @see #jsonValueVariation(String, LDUser, LDValue)
   * @since 6.0.0
   */
  LDValue jsonValueVariation(String key, LDContext context, LDValue defaultValue);

  /**
   * Calculates the value of a feature flag for a given context as any JSON value type.
   * <p>
   * This is equivalent to {@link #jsonValueVariation(String, LDContext, LDValue)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return the variation for the given context, or {@code defaultValue} if the flag cannot be evaluated
   * @see #jsonValueVariation(String, LDContext, LDValue)
   * @since 4.8.0
   */
  default LDValue jsonValueVariation(String key, LDUser user, LDValue defaultValue) {
    return jsonValueVariation(key, LDContext.fromUser(user), defaultValue);
  }

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
   * @see #boolVariationDetail(String, LDUser, boolean)
   */
  EvaluationDetail<Boolean> boolVariationDetail(String key, LDContext context, boolean defaultValue);

  /**
   * Calculates the boolean value of a feature flag for a given context, and returns an object that
   * describes the way the value was determined.
   * <p>
   * This is equivalent to {@link #boolVariationDetail(String, LDContext, boolean)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   * 
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 2.3.0
   * @see #boolVariationDetail(String, LDContext, boolean)
   */
  default EvaluationDetail<Boolean> boolVariationDetail(String key, LDUser user, boolean defaultValue) {
    return boolVariationDetail(key, LDContext.fromUser(user), defaultValue);
  }
  
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
   * @see #intVariationDetail(String, LDUser, int)
   * @since 6.0.0
   */
  EvaluationDetail<Integer> intVariationDetail(String key, LDContext context, int defaultValue);

  /**
   * Calculates the integer numeric value of a feature flag for a given context, and returns an object
   * that describes the way the value was determined.
   * <p>
   * This is equivalent to {@link #intVariationDetail(String, LDContext, int)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   * 
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @see #intVariationDetail(String, LDContext, int)
   * @since 2.3.0
   */
  default EvaluationDetail<Integer> intVariationDetail(String key, LDUser user, int defaultValue) {
    return intVariationDetail(key, LDContext.fromUser(user), defaultValue);
  }
  
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
   * @see #doubleVariationDetail(String, LDUser, double)
   * @since 6.0.0
   */
  EvaluationDetail<Double> doubleVariationDetail(String key, LDContext context, double defaultValue);

  /**
   * Calculates the floating-point numeric value of a feature flag for a given context, and returns an
   * object that describes the way the value was determined.
   * <p>
   * This is equivalent to {@link #doubleVariationDetail(String, LDContext, double)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @see #doubleVariation(String, LDContext, double)
   * @since 2.3.0
   */
  default EvaluationDetail<Double> doubleVariationDetail(String key, LDUser user, double defaultValue) {
    return doubleVariationDetail(key, LDContext.fromUser(user), defaultValue);
  }

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
   * @see #stringVariation(String, LDUser, String)
   * @return an {@link EvaluationDetail} object
   * @since 6.0.0
   */
  EvaluationDetail<String> stringVariationDetail(String key, LDContext context, String defaultValue);

  /**
   * Calculates the string value of a feature flag for a given context, and returns an object
   * that describes the way the value was determined.
   * <p>
   * This is equivalent to {@link #stringVariationDetail(String, LDContext, String)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   * 
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @see #stringVariationDetail(String, LDContext, String)
   * @since 2.3.0
   */
  default EvaluationDetail<String> stringVariationDetail(String key, LDUser user, String defaultValue) {
    return stringVariationDetail(key, LDContext.fromUser(user), defaultValue);
  }

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
   * @see #jsonValueVariation(String, LDUser, LDValue)
   * @since 6.0.0
   */
  EvaluationDetail<LDValue> jsonValueVariationDetail(String key, LDContext context, LDValue defaultValue);

  /**
   * Calculates the value of a feature flag for a given context as any JSON value type, and returns an
   * object that describes the way the value was determined.
   * that describes the way the value was determined.
   * <p>
   * This is equivalent to {@link #jsonValueVariationDetail(String, LDContext, LDValue)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param key          the unique key for the feature flag
   * @param user         the user attributes
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @see #jsonValueVariation(String, LDContext, LDValue)
   * @since 4.8.0
   */
  default EvaluationDetail<LDValue> jsonValueVariationDetail(String key, LDUser user, LDValue defaultValue) {
    return jsonValueVariationDetail(key, LDContext.fromUser(user), defaultValue);
  }

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
   * Creates a hash string that can be used by the JavaScript SDK to identify a context.
   * <p>
   * See <a href="https://docs.launchdarkly.com/sdk/features/secure-mode#configuring-secure-mode-in-the-javascript-client-side-sdk">
   * Secure mode</a> in the JavaScript SDK Reference.
   *
   * @param context the evaluation context
   * @return the hash, or null if the hash could not be calculated
   * @see #secureModeHash(LDUser)
   * @since 6.0.0
   */
  String secureModeHash(LDContext context);

  /**
   * Creates a hash string that can be used by the JavaScript SDK to identify a context.
   * <p>
   * This is equivalent to {@link #secureModeHash(LDContext)}, but using the {@link LDUser} type
   * instead of {@link LDContext}.
   *
   * @param user the user attributes
   * @return the hash, or null if the hash could not be calculated
   * @see #secureModeHash(LDContext)
   */
  default String secureModeHash(LDUser user) {
    return secureModeHash(LDContext.fromUser(user));
  }

  /**
   * The current version string of the SDK.
   * 
   * @return a string in Semantic Versioning 2.0.0 format
   */
  String version();
}
