package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import org.jetbrains.annotations.NotNull;

/**
 * Result of an {@link LDClient#migrationVariation(String, LDContext, MigrationStage)} call.
 * <p>
 * This includes the stage of the migration as well as a tracker.
 */
public final class MigrationVariation {
  private final MigrationStage stage;
  private final MigrationOpTracker tracker;

  public MigrationVariation(@NotNull MigrationStage stage, @NotNull MigrationOpTracker tracker) {
    this.stage = stage;
    this.tracker = tracker;
  }

  /**
   * The result of the flag evaluation. This will be either one of the flag's variations or
   *  the default value that was passed to {@link LDClient#migrationVariation(String, LDContext, MigrationStage)}.
   * @return The migration stage.
   */
  public MigrationStage getStage() {
    return this.stage;
  }

  /**
   * A tracker which can be used to generate analytics for the migration.
   * @return The tracker.
   */
  public MigrationOpTracker getTracker() {
    return this.tracker;
  }
}
