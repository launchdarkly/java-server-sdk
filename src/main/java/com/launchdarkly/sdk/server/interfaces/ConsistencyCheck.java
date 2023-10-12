package com.launchdarkly.sdk.server.interfaces;

/**
 * Consistency check result.
 */
public enum ConsistencyCheck {
  /**
   * Consistency was checked and found to be inconsistent.
   */
  INCONSISTENT,

  /**
   * Consistency was checked and found to be consistent.
   */
  CONSISTENT,

  /**
   * Consistency check was not performed.
   */
  NOT_CHECKED
}
