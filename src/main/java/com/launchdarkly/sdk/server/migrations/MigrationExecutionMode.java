package com.launchdarkly.sdk.server.migrations;

/**
 * Execution mode for a migration.
 * <p>
 * This applies only to a single read, not multiple reads using the same migration.
 */
public enum MigrationExecutionMode {
  /**
   * Execute one read fully before executing another read.
   */
  SERIAL,
  /**
   * Start reads in parallel and wait for them to both finish.
   */
  PARALLEL
}
