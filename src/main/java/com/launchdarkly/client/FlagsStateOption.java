package com.launchdarkly.client;

/**
 * Optional parameters that can be passed to {@link LDClientInterface#allFlagsState(LDUser)}.
 * @since 4.3.0
 */
public abstract class FlagsStateOption {
  /**
   * Specifies that {@link EvaluationReason} data should be captured in the state object. By default, it is not.
   */
  public static final FlagsStateOption WITH_REASONS = new WithReasons();
  
  private static class WithReasons extends FlagsStateOption { }

  static boolean hasOption(FlagsStateOption[] options, FlagsStateOption option) {
    for (FlagsStateOption o: options) {
      if (o.equals(option)) {
        return true;
      }
    }
    return false;
  }
}