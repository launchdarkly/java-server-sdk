package com.launchdarkly.sdk.server;

import java.util.Arrays;

/**
 * Stage denotes one of six possible stages a technology migration could be a
 * part of, progressing through the following order.
 * <p>
 * Off DualWrite Shadow Live RampDown Complete
 */
public enum MigrationStage {
  /**
   * Off - migration hasn't started, "old" is authoritative for reads and writes
   */
  OFF("off"),

  /**
   * DualWrite - write to both "old" and "new", "old" is authoritative for reads
   */
  DUAL_WRITE("dualwrite"),

  /**
   * Shadow - both "new" and "old" versions run with a preference for "old"
   */
  SHADOW("shadow"),

  /**
   * Live - both "new" and "old" versions run with a preference for "new"
   */
  LIVE("live"),

  /**
   * RampDown - only read from "new", write to "old" and "new"
   */
  RAMP_DOWN("rampdown"),

  /**
   * Complete - migration is done
   */
  COMPLETE("complete");

  private final String strValue;

  MigrationStage(final String strValue) {
    this.strValue = strValue;
  }

  @Override
  public String toString() {
    return strValue;
  }

  /**
   * Check if the provided string is a migration stage.
   *
   * @param strValue The string to check.
   * @return True if the string represents a migration stage.
   */
  public static boolean isStage(String strValue) {
    return Arrays.stream(MigrationStage.values()).anyMatch(item -> item.strValue.equals(strValue));
  }

  /**
   * Convert a string into a migration stage.
   * <p>
   * If the string is not a stage, then the provided default will be returned.
   *
   * @param strValue     The string to convert.
   * @param defaultStage The default value to use if the string does not represent a migration stage.
   * @return The converted migration stage.
   */
  public static MigrationStage of(String strValue, MigrationStage defaultStage) {
    return Arrays.stream(MigrationStage.values())
        .filter(item -> item.strValue.equals(strValue))
        .findFirst()
        .orElse(defaultStage);
  }
}
