package com.launchdarkly.sdk.server.migrations;

import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * This builder is used to construct {@link Migration} instances.
 * <p>
 * This class is not thread-safe. The builder should be used on one thread and then the
 * built {@link Migration} is thread safe.
 *
 * @param <TReadResult>  The result type for reads.
 * @param <TWriteResult> The result type for writes.
 * @param <TReadInput>   The input parameter type for reads.
 * @param <TWriteInput>  The input type for writes.
 */
public class MigrationBuilder<TReadResult, TWriteResult, TReadInput, TWriteInput> {
  private Migration.Reader<TReadInput, TReadResult> readOld;
  private Migration.Reader<TReadInput, TReadResult> readNew;

  private Migration.Writer<TWriteInput, TWriteResult> writeOld;
  private Migration.Writer<TWriteInput, TWriteResult> writeNew;

  private Migration.ReadConsistencyChecker<TReadResult> checker;

  private MigrationExecution execution = MigrationExecution.Parallel();

  private boolean latencyTracking = true;

  private boolean errorTracking = true;

  private final LDClientInterface client;

  /**
   * Construct a new builder.
   *
   * @param client this client will be used for {@link Migration}s built from
   *               this builder
   */
  public MigrationBuilder(LDClientInterface client) {
    this.client = client;
  }

  /**
   * Enable or disable latency tracking. Tracking is enabled by default.
   *
   * @param track true to enable tracking, false to disable it
   * @return a reference to this builder
   */
  @NotNull
  public MigrationBuilder<TReadResult, TWriteResult, TReadInput, TWriteInput> trackLatency(boolean track) {
    this.latencyTracking = track;
    return this;
  }

  /**
   * Enable or disable error tracking. Tracking is enabled by default.
   *
   * @param track true to enable error tracking, false to disable it
   * @return a reference to this builder
   */
  @NotNull
  public MigrationBuilder<TReadResult, TWriteResult, TReadInput, TWriteInput> trackErrors(boolean track) {
    this.errorTracking = track;
    return this;
  }

  /**
   * Influences the level of concurrency when the migration stage calls for multiple execution reads.
   * <p>
   * The default read execution is {@link MigrationExecution#Parallel()}.
   * <p>
   * Setting the execution to randomized serial order.
   * <pre>
   *   builder.readExecution(MigrationExecution.Serial(MigrationSerialOrder.RANDOM));
   * </pre>
   *
   * @param execution the execution configuration
   * @return a reference to this builder
   */
  @NotNull
  public MigrationBuilder<TReadResult, TWriteResult, TReadInput, TWriteInput> readExecution(MigrationExecution execution) {
    this.execution = execution;
    return this;
  }

  /**
   * Configure the read methods of the migration.
   * <p>
   * Users are required to provide two different read methods -- one to read from the old migration source, and one to
   * read from the new source. This method allows specifying a check method for consistency tracking.
   * <p>
   * If you do not want consistency tracking, then use
   * {@link MigrationBuilder#read(Migration.Reader, Migration.Reader)}.
   *
   * @param oldImpl method for reading from the "old" migration source
   * @param newImpl method for reading from the "new" migration source
   * @param checker method which checks the consistency of the "old" and "new" source
   * @return a reference to this builder
   */
  @NotNull
  public MigrationBuilder<TReadResult, TWriteResult, TReadInput, TWriteInput> read(
      @NotNull Migration.Reader<TReadInput, TReadResult> oldImpl,
      @NotNull Migration.Reader<TReadInput, TReadResult> newImpl,
      @NotNull Migration.ReadConsistencyChecker<TReadResult> checker
  ) {
    this.readOld = oldImpl;
    this.readNew = newImpl;
    this.checker = checker;
    return this;
  }

  /**
   * Configure the read methods of the migration.
   * <p>
   * Users are required to provide two different read methods -- one to read from the old migration source, and one to
   * read from the new source. This method does not enable consistency tracking.
   * <p>
   * If you do want consistency tracking, then use
   * {@link MigrationBuilder#read(Migration.Reader, Migration.Reader, Migration.ReadConsistencyChecker)}.
   *
   * @param oldImpl method for reading from the "old" migration source
   * @param newImpl method for reading from the "new" migration source
   * @return a reference to this builder
   */
  @NotNull
  public MigrationBuilder<TReadResult, TWriteResult, TReadInput, TWriteInput> read(
      @NotNull Migration.Reader<TReadInput, TReadResult> oldImpl,
      @NotNull Migration.Reader<TReadInput, TReadResult> newImpl
  ) {
    this.readOld = oldImpl;
    this.readNew = newImpl;
    return this;
  }

  /**
   * Configure the write methods of the migration.
   * <p>
   * Users are required to provide two different write methods -- one to write to the old migration source, and one to
   * write to the new source. Not every stage requires
   *
   * @param oldImpl method which writes to the "old" source
   * @param newImpl method which writes to the "new" source
   * @return a reference to this builder
   */
  @NotNull
  public MigrationBuilder<TReadResult, TWriteResult, TReadInput, TWriteInput> write(
      @NotNull Migration.Writer<TWriteInput, TWriteResult> oldImpl,
      @NotNull Migration.Writer<TWriteInput, TWriteResult> newImpl
  ) {
    this.writeOld = oldImpl;
    this.writeNew = newImpl;
    return this;
  }

  /**
   * Build a {@link Migration}.
   * <p>
   * A migration requires that both the read and write methods are defined. If they have not been defined, then
   * a migration cannot be constructed. In this case an empty optional will be returned.
   *
   * @return Either an empty optional or an optional containing a {@link Migration}.
   */
  @NotNull
  public Optional<Migration<TReadResult, TWriteResult, TReadInput, TWriteInput>> build() {
    // All the methods must be set to make a valid migration.
    if (
        readNew == null ||
            readOld == null ||
            writeNew == null ||
            writeOld == null
    ) {
      // TODO: Log something.
      return Optional.empty();
    }
    return Optional.of(new Migration<>(
        client, readOld, readNew, writeOld, writeNew,
        checker, execution, latencyTracking, errorTracking
    ));
  }
}
