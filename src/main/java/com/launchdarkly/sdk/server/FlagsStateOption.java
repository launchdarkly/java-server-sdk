package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;

/**
 * Optional parameters that can be passed to {@link LDClientInterface#allFlagsState(com.launchdarkly.sdk.LDContext, FlagsStateOption...)}.
 * @since 4.3.0
 */
public final class FlagsStateOption {
  private final String description;
  
  private FlagsStateOption(String description) {
    this.description = description;
  }
  
  @Override
  public String toString() {
    return description;
  }

  /**
   * Specifies that only flags marked for use with the client-side SDK should be included in the state object.
   * By default, all flags are included.
   */
  public static final FlagsStateOption CLIENT_SIDE_ONLY = new FlagsStateOption("CLIENT_SIDE_ONLY");

  /**
   * Specifies that {@link EvaluationReason} data should be captured in the state object. By default, it is not.
   */
  public static final FlagsStateOption WITH_REASONS = new FlagsStateOption("WITH_REASONS");
  
  /**
   * Specifies that any flag metadata that is normally only used for event generation - such as flag versions and
   * evaluation reasons - should be omitted for any flag that does not have event tracking or debugging turned on.
   * This reduces the size of the JSON data if you are passing the flag state to the front end.
   * @since 4.4.0
   */
  public static final FlagsStateOption DETAILS_ONLY_FOR_TRACKED_FLAGS = new FlagsStateOption("DETAILS_ONLY_FOR_TRACKED_FLAGS");
  
  static boolean hasOption(FlagsStateOption[] options, FlagsStateOption option) {
    for (FlagsStateOption o: options) {
      if (o == option) {
        return true;
      }
    }
    return false;
  }
}