package com.launchdarkly.client;

/**
 * Optional parameters that can be passed to {@link LDClientInterface#allFlagsState(LDUser, FlagsStateOption...)}.
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
  
  static boolean hasOption(FlagsStateOption[] options, FlagsStateOption option) {
    for (FlagsStateOption o: options) {
      if (o == option) {
        return true;
      }
    }
    return false;
  }
}