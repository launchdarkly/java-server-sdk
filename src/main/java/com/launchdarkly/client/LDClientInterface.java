package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.launchdarkly.client.value.LDValue;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

/**
 * This interface defines the public methods of {@link LDClient}.
 */
public interface LDClientInterface extends Closeable {
  boolean initialized();

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
   * @param data      a JSON object containing additional data associated with the event; may be null
   * @deprecated Use {@link #trackData(String, LDUser, LDValue)}.
   */
  @Deprecated
  void track(String eventName, LDUser user, JsonElement data);

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
   * behavior from calling {@link #track(String, LDUser, JsonElement)} without a {@code metricValue}.
   * Refer to the <a href="https://docs.launchdarkly.com/docs/java-sdk-reference#section-track">SDK reference guide</a> for the latest status.
   * 
   * @param eventName the name of the event
   * @param user      the user that performed the event
   * @param data      a JSON object containing additional data associated with the event; may be null
   * @param metricValue a numeric value used by the LaunchDarkly experimentation feature in numeric custom
   * metrics. Can be omitted if this event is used by only non-numeric metrics. This field will also be
   * returned as part of the custom event for Data Export.
   * @since 4.8.0
   * @deprecated Use {@link #trackMetric(String, LDUser, LDValue, double)}.
   */
  @Deprecated
  void track(String eventName, LDUser user, JsonElement data, double metricValue);

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
   * Returns a map from feature flag keys to {@code JsonElement} feature flag values for a given user.
   * If the result of a flag's evaluation would have returned the default variation, it will have a null entry
   * in the map. If the client is offline, has not been initialized, or a null user or user with null/empty user key a {@code null} map will be returned.
   * This method will not send analytics events back to LaunchDarkly.
   * <p>
   * The most common use case for this method is to bootstrap a set of client-side feature flags from a back-end service.
   *
   * @param user the end user requesting the feature flags
   * @return a map from feature flag keys to {@code JsonElement} for the specified user
   * 
   * @deprecated Use {@link #allFlagsState} instead. Current versions of the client-side SDK will not
   * generate analytics events correctly if you pass the result of {@code allFlags()}.
   */
  @Deprecated
  Map<String, JsonElement> allFlags(LDUser user);

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
   * Calculates the {@link JsonElement} value of a feature flag for a given user.
   *
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return the variation for the given user, or {@code defaultValue} if the flag is disabled in the LaunchDarkly control panel
   * @deprecated Use {@link #jsonValueVariation(String, LDUser, LDValue)}. Gson types may be removed
   * from the public API in the future.
   */
  @Deprecated
  JsonElement jsonVariation(String featureKey, LDUser user, JsonElement defaultValue);

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
   * Calculates the value of a feature flag for a given user, and returns an object that describes the
   * way the value was determined. The {@code reason} property in the result will also be included in
   * analytics events, if you are capturing detailed event data for this flag.
   * @param featureKey   the unique key for the feature flag
   * @param user         the end user requesting the flag
   * @param defaultValue the default value of the flag
   * @return an {@link EvaluationDetail} object
   * @since 2.3.0
   * @deprecated Use {@link #jsonValueVariationDetail(String, LDUser, LDValue)}. Gson types may be removed
   * from the public API in the future.
   */
  @Deprecated
  EvaluationDetail<JsonElement> jsonVariationDetail(String featureKey, LDUser user, JsonElement defaultValue);

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
   * For more info: <a href="https://github.com/launchdarkly/js-client#secure-mode">https://github.com/launchdarkly/js-client#secure-mode</a>
   * @param user the user to be hashed along with the SDK key
   * @return the hash, or null if the hash could not be calculated
     */
  String secureModeHash(LDUser user);
  
  String version();
}
