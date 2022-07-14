package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.LDValue;

/**
 * Parameter class used with {@link FlagValueChangeListener}.
 * <p>
 * This is not an analytics event to be sent to LaunchDarkly; it is a notification to the application.
 * 
 * @since 5.0.0
 * @see FlagValueChangeListener
 * @see FlagTracker#addFlagValueChangeListener(String, com.launchdarkly.sdk.LDContext, FlagValueChangeListener)
 */
public class FlagValueChangeEvent extends FlagChangeEvent {
  private final LDValue oldValue;
  private final LDValue newValue;
  
  /**
   * Constructs a new instance.
   * 
   * @param key the feature flag key
   * @param oldValue the previous flag value
   * @param newValue the new flag value 
   */
  public FlagValueChangeEvent(String key, LDValue oldValue, LDValue newValue) {
    super(key);
    this.oldValue = LDValue.normalize(oldValue);
    this.newValue = LDValue.normalize(newValue);
  }
  
  /**
   * Returns the last known value of the flag for the specified evaluation context prior to the update.
   * <p>
   * Since flag values can be of any JSON data type, this is represented as {@link LDValue}. That class
   * has methods for converting to a primitive Java type such as {@link LDValue#booleanValue()}.
   * <p>
   * If the flag did not exist before or could not be evaluated, this will be {@link LDValue#ofNull()}.
   * Note that there is no application default value parameter as there is for the {@code variation}
   * methods; it is up to your code to substitute whatever fallback value is appropriate.
   * 
   * @return the previous flag value
   */
  public LDValue getOldValue() {
    return oldValue;
  }

  /**
   * Returns the new value of the flag for the specified evaluation context.
   * <p>
   * Since flag values can be of any JSON data type, this is represented as {@link LDValue}. That class
   * has methods for converting to a primitive Java type such {@link LDValue#booleanValue()}.
   * <p>
   * If the flag was deleted or could not be evaluated, this will be {@link LDValue#ofNull()}.
   * Note that there is no application default value parameter as there is for the {@code variation}
   * methods; it is up to your code to substitute whatever fallback value is appropriate.
   *  
   * @return the new flag value
   */
  public LDValue getNewValue() {
    return newValue;
  }
}
