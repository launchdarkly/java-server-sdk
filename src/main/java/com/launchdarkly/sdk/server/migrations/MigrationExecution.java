package com.launchdarkly.sdk.server.migrations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * This class is used to control the execution mechanism for migrations.
 * <p>
 * Read operations may be executed in parallel, sequentially in a fixed order, or sequentially in a randomized order.
 * <p>
 * This class facilitates correct combinations of parallel/serial with random/fixed.
 */
public class MigrationExecution {
  private final MigrationExecutionMode mode;
  private final MigrationSerialOrder order;

  private MigrationExecution(
      @NotNull MigrationExecutionMode mode,
      @Nullable MigrationSerialOrder order) {
    this.mode = mode;
    this.order = order;
  }

  /**
   * Construct a serial execution with the specified ordering.
   *
   * @param order The serial execution order fixed/random.
   * @return an execution instance
   */
  public static MigrationExecution Serial(@NotNull MigrationSerialOrder order) {
    return new MigrationExecution(MigrationExecutionMode.SERIAL, order);
  }

  /**
   * Constructs a parallel execution.
   *
   * @return an execution instance
   */
  public static MigrationExecution Parallel() {
    return new MigrationExecution(MigrationExecutionMode.PARALLEL, null);
  }

  /**
   * Get the current execution mode.
   *
   * @return The execution mode.
   */
  public MigrationExecutionMode getMode() {
    return mode;
  }

  /**
   * If the execution mode is {@link MigrationExecutionMode#SERIAL}, then this will contain an execution order.
   * If the mode is not SERIAL, then this will return an empty optional.
   *
   * @return The optional execution mode.
   */
  public Optional<MigrationSerialOrder> getOrder() {
    return Optional.ofNullable(order);
  }

  /**
   * A string representation of the migration execution. The return value from this function should only be used
   * for logging or human-read identification. It should not be used programmatically and will not follow semver.
   *
   * @return A string representation of the string.
   */
  @Override
  public String toString() {
    String strValue = "";

    strValue += mode.toString();
    if (order != null) {
      strValue += "-";
      strValue += order.toString();
    }
    return strValue;
  }
}
