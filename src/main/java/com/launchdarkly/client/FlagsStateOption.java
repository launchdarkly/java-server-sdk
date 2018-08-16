package com.launchdarkly.client;

/**
 * Optional parameters that can be passed to {@link LDClientInterface#allFlagsState(LDUser)}.
 * @since 4.3.0
 */
public abstract class FlagsStateOption {
  /**
   * Specifies whether {@link EvaluationReason} data should be captured in the state object. By default, it is not.
   * @param value true if evaluation reasons should be stored
   * @return an option object
   */
  public static FlagsStateOption withReasons(boolean value) {
    return new WithReasons(value);
  }
  
  private static class WithReasons extends FlagsStateOption {
    final boolean value;
    WithReasons(boolean value) {
      this.value = value;
    }
  }
  
  static boolean isWithReasons(FlagsStateOption[] options) {
    for (FlagsStateOption o: options) {
      if (o instanceof WithReasons) {
        return ((WithReasons)o).value;
      }
    }
    return false;
  }
}