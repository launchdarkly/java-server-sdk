package com.launchdarkly.client;

import com.google.gson.JsonElement;

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
   *
   * @param eventName the name of the event
   * @param user      the user that performed the event
   * @param data      a JSON object containing additional data associated with the event
   */
  void track(String eventName, LDUser user, JsonElement data);

  /**
   * Tracks that a user performed an event.
   *
   * @param eventName the name of the event
   * @param user      the user that performed the event
   */
  void track(String eventName, LDUser user);

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
   */
  Map<String, JsonElement> allFlags(LDUser user);

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
   */
  JsonElement jsonVariation(String featureKey, LDUser user, JsonElement defaultValue);

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
