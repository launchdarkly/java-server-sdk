package com.launchdarkly.sdk.server.interfaces;

/**
 * Parameter class used with {@link FlagChangeListener}.
 * <p>
 * This is not an analytics event to be sent to LaunchDarkly; it is a notification to the application.
 * 
 * @since 5.0.0
 * @see FlagChangeListener
 * @see FlagValueChangeEvent
 * @see FlagTracker#addFlagChangeListener(FlagChangeListener)
 */
public class FlagChangeEvent {
  private final String key;
  
  /**
   * Constructs a new instance.
   * 
   * @param key the feature flag key
   */
  public FlagChangeEvent(String key) {
    this.key = key;
  }
  
  /**
   * Returns the key of the feature flag whose configuration has changed.
   * <p>
   * The specified flag may have been modified directly, or this may be an indirect change due to a change
   * in some other flag that is a prerequisite for this flag, or a user segment that is referenced in the
   * flag's rules.
   * 
   * @return the flag key
   */
  public String getKey() {
    return key;
  }
}
