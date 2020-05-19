package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
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
   * Tracks that a user performed an event.
   * <p>
   * To add custom data to the event, use {@link #trackData(String, LDUser, LDValue)}.
   *
   * @param eventName the name of the event
   * @param user      the user that performed the event
   */
  void track(String eventName, LDUser user);

  /**
   * Tracks that a user performed an event, and provides additional custom data.
   *
   * @param eventName the name of the event
   * @param user      the user that performed the event
   * @param data      an {@link LDValue} containing additional data associated with the event
   * @since 4.8.0
   */
  void trackData(String eventName, LDUser user, LDValue data);

  /**
   * Tracks that a user performed an event, and provides an additional numeric value for custom metrics.
   * <p>
   * As of this version’s release date, the LaunchDarkly service does not support the {@code metricValue}
   * parameter. As a result, calling this overload of {@code track} will not yet produce any different
   * behavior from calling {@link #trackData(String, LDUser, LDValue)} without a {@code metricValue}.
   * Refer to the <a href="https://docs.launchdarkly.com/docs/java-sdk-reference#section-track">SDK reference guide</a> for the latest status.
   * 
   * @param eventName the name of the event
   * @param user      the user that performed the event
   * @param data      an {@link LDValue} containing additional data associated with the event; if not applicable,
   * you may pass either {@code null} or {@link LDValue#ofNull()}
   * @param metricValue a numeric value used by the LaunchDarkly experimentation feature in numeric custom
   * metrics. Can be omitted if this event is used by only non-numeric metrics. This field will also be
   * returned as part of the custom event for Data Export.
   * @since 4.9.0
   */
  void trackMetric(String eventName, LDUser user, LDValue data, double metricValue);

  /**
   * Registers the user.
   *
   * @param user the user to register
   */
  void identify(LDUser user);

  /**
   * Returns an object that encapsulates the state of all feature flags for a given user, including the flag
   * values and also metadata that can be used on the front end. This method does not send analytics events
   * back to LaunchDarkly.
   * <p>
   * The most common use case for this method is to bootstrap a set of client-side feature flags from a back-end service.
   *  
   * @param user the end user requesting the feature flags
   * @param options optional {@link FlagsStateOption} values affecting how the state is computed - for
   * instance, to filter the set of flags to only include the client-side-enabled ones
   * @return a {@link FeatureFlagsState} object (will never be null; see {@link FeatureFlagsState#isValid()}
   * @since 4.3.0
   */
  FeatureFlagsState allFlagsState(LDUser user, FlagsStateOption... options);
  
  /**
   * Calculates the value of a feature flag for a given user.
   *
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return whether or not the flag should be enabled, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  boolean boolVariation(String featureKey, LDUser user, boolean defaultValue);
  
  /**
   * Calculates the integer value of a feature flag for a given user.
   * <p>
   * If the flag variation has a numeric value that is not an integer, it is rounded toward zero (truncated).
   * 
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return the variation for the given user, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  Integer intVariation(String featureKey, LDUser user, int defaultValue);

  /**
   * Calculates the floating point numeric value of a feature flag for a given user.
   *
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return the variation for the given user, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  Double doubleVariation(String featureKey, LDUser user, Double defaultValue);

  /**
   * Calculates the String value of a feature flag for a given user.
   *
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return the variation for the given user, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   */
  String stringVariation(String featureKey, LDUser user, String defaultValue);

  /**
   * Calculates the {@link LDValue} value of a feature flag for a given user.
   *
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return the variation for the given user, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel;
   * will never be a null reference, but may be {@link LDValue#ofNull()}
   * 
   * @since 4.8.0
   */
  LDValue jsonValueVariation(String featureKey, LDUser user, LDValue defaultValue);

  /**
   * Calculates the value of a feature flag for a given user, and returns an object that describes the
   * way the value was determined. The {@code reason} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 2.3.0
   */
  EvaluationDetail<Boolean> boolVariationDetail(String featureKey, LDUser user, boolean defaultValue);
  
  /**
   * Calculates the value of a feature flag for a given user, and returns an object that describes the
   * way the value was determined. The {@code reason} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * <p>
   * If the flag variation has a numeric value that is not an integer, it is rounded toward zero (truncated).
   * 
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 2.3.0
   */
  EvaluationDetail<Integer> intVariationDetail(String featureKey, LDUser user, int defaultValue);
  
  /**
   * Calculates the value of a feature flag for a given user, and returns an object that describes the
   * way the value was determined. The {@code reason} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 2.3.0
   */
  EvaluationDetail<Double> doubleVariationDetail(String featureKey, LDUser user, double defaultValue);

  /**
   * Calculates the value of a feature flag for a given user, and returns an object that describes the
   * way the value was determined. The {@code reason} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 2.3.0
   */
  EvaluationDetail<String> stringVariationDetail(String featureKey, LDUser user, String defaultValue);

  /**
   * Calculates the {@link LDValue} value of a feature flag for a given user.
   *
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * 
   * @since 4.8.0
   */
  EvaluationDetail<LDValue> jsonValueVariationDetail(String featureKey, LDUser user, LDValue defaultValue);

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
   * Registers a listener to be notified of feature flag changes.
   * <p>
   * The listener will be notified whenever the SDK receives any change to any feature flag's configuration,
   * or to a user segment that is referenced by a feature flag. If the updated flag is used as a prerequisite
   * for other flags, the SDK assumes that those flags may now behave differently and sends events for them
   * as well.
   * <p>
   * Note that this does not necessarily mean the flag's value has changed for any particular user, only that
   * some part of the flag configuration was changed so that it <i>may</i> return a different value than it
   * previously returned for some user.
   * <p>
   * Change events only work if the SDK is actually connecting to LaunchDarkly (or using the file data source).
   * If the SDK is only reading flags from a database ({@link Components#externalUpdatesOnly()}) then it cannot
   * know when there is a change, because flags are read on an as-needed basis.
   * <p>
   * The listener will be called from a worker thread.
   * <p>
   * Calling this method for an already-registered listener has no effect.
   * 
   * @param listener the event listener to register
   * @see #unregisterFlagChangeListener(FlagChangeListener)
   * @see FlagChangeListener
   * @since 5.0.0
   */
  void registerFlagChangeListener(FlagChangeListener listener);
  
  /**
   * Unregisters a listener so that it will no longer be notified of feature flag changes.
   * <p>
   * Calling this method for a listener that was not previously registered has no effect.
   * 
   * @param listener the event listener to unregister
   * @see #registerFlagChangeListener(FlagChangeListener)
   * @see FlagChangeListener
   * @since 5.0.0
   */
  void unregisterFlagChangeListener(FlagChangeListener listener);

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
   * For more info: <a href="https://github.com/launchdarkly/js-client#secure-mode">https://github.com/launchdarkly/js-client#secure-mode</a>
   * @param user the user to be hashed along with the SDK key
   * @return the hash, or null if the hash could not be calculated
     */
  String secureModeHash(LDUser user);
  
  /**
   * The current version string of the SDK.
   * @return a string in Semantic Versioning 2.0.0 format
   */
  String version();
}
