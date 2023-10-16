package com.launchdarkly.sdk.server;

/**
 * The type of migration operation.
 */
public enum MigrationOp {
  READ("read"),
  WRITE("write");

  private final String strValue;

  MigrationOp(final String strValue) {
    this.strValue = strValue;
  }

  @Override
  public String toString() {
    return strValue;
  }
}
