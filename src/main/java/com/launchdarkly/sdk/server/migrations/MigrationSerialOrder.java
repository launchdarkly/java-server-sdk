package com.launchdarkly.sdk.server.migrations;

/**
 * When using serial execution controls the order reads are executed.
 */
public enum MigrationSerialOrder {
  /**
   * Each time a read is performed randomize the order.
   */
  RANDOM,
  /**
   * Always execute reads in the same order.
   */
  FIXED
}
