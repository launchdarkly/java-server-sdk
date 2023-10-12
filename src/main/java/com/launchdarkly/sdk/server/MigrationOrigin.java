package com.launchdarkly.sdk.server;

/**
 * The origin/source for a migration step.
 */
public enum MigrationOrigin {
  /**
   * The "old" implementation.
   */
  OLD,

  /**
   * The "new" implementation.
   */
  NEW
}
