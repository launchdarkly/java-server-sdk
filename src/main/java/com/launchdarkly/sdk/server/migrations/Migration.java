package com.launchdarkly.sdk.server.migrations;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.MigrationOp;
import com.launchdarkly.sdk.server.MigrationOpTracker;
import com.launchdarkly.sdk.server.MigrationOrigin;
import com.launchdarkly.sdk.server.MigrationStage;
import com.launchdarkly.sdk.server.MigrationVariation;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Class for performing a technology migration.
 * <p>
 * This class is not intended to be instanced directly, but instead should be constructed
 * using the {@link MigrationBuilder}.
 * <p>
 * The thread safety model for a migration depends on the usage of thread-safe elements. Specifically the tracker,
 * the client, and the thread pool should be thread-safe. Other elements of the migration instance itself are immutable
 * for their thread-safety.
 *
 * @param <TReadResult>  The result type for reads.
 * @param <TWriteResult> The result type for writes.
 * @param <TReadInput>   The input parameter type for reads.
 * @param <TWriteInput>  The input type for writes.
 */
public final class Migration<TReadResult, TWriteResult, TReadInput, TWriteInput> {
  private final Reader<TReadInput, TReadResult> readOld;
  private final Reader<TReadInput, TReadResult> readNew;

  private final Writer<TWriteInput, TWriteResult> writeOld;
  private final Writer<TWriteInput, TWriteResult> writeNew;

  private final ReadConsistencyChecker<TReadResult> checker;

  private final MigrationExecution execution;

  private final boolean latencyTracking;

  private final boolean errorTracking;

  private final LDClientInterface client;
  private final LDLogger logger;

  private final ExecutorService pool = Executors.newCachedThreadPool();

  Migration(
      LDClientInterface client,
      Reader<TReadInput, TReadResult> readOld,
      Reader<TReadInput, TReadResult> readNew,
      Writer<TWriteInput, TWriteResult> writeOld,
      Writer<TWriteInput, TWriteResult> writeNew,
      ReadConsistencyChecker<TReadResult> checker,
      MigrationExecution execution,
      boolean latencyTracking,
      boolean errorTracking) {
    this.client = client;
    this.readOld = readOld;
    this.readNew = readNew;
    this.writeOld = writeOld;

    this.writeNew = writeNew;
    this.checker = checker;
    this.execution = execution;
    this.latencyTracking = latencyTracking;
    this.errorTracking = errorTracking;
    this.logger = client.getLogger();
  }

  public interface Method<UInput, UOutput> {
    MigrationMethodResult<UOutput> execute(UInput payload);
  }


  /**
   * This interface defines a read method.
   *
   * @param <TReadInput>  the payload type of the read
   * @param <TReadResult> the result type of the read
   */
  public interface Reader<TReadInput, TReadResult> extends Method<TReadInput, TReadResult> {
  }

  /**
   * This interfaces defines a write method.
   *
   * @param <TWriteInput>  the payload type of the write
   * @param <TWriteResult> the return type of the write
   */
  public interface Writer<TWriteInput, TWriteResult> extends Method<TWriteInput, TWriteResult> {
  }

  /**
   * This interface defines a method for checking the consistency of two reads.
   *
   * @param <TReadResult> the result type of the read
   */
  public interface ReadConsistencyChecker<TReadResult> {
    boolean check(TReadResult a, TReadResult b);
  }

  /**
   * This class represents the result of a migration operation.
   * <p>
   * In the case of a read operation the result will be this type. Write operations may need to return multiple results
   * and therefore use the {@link MigrationWriteResult} type.
   *
   * @param <TResult> the result type of the operation
   */
  public static final class MigrationResult<TResult> {
    private final boolean success;
    private final MigrationOrigin origin;
    private final TResult result;
    private final Exception exception;

    public MigrationResult(
        boolean success,
        @NotNull MigrationOrigin origin,
        @Nullable TResult result,
        @Nullable Exception exception) {
      this.success = success;
      this.origin = origin;
      this.result = result;
      this.exception = exception;
    }

    /**
     * Check if the operation was a success.
     *
     * @return true if the operation was a success
     */
    public boolean isSuccess() {
      return success;
    }

    /**
     * Get the origin associated with the result.
     *
     * @return The origin of the result.
     */
    public MigrationOrigin getOrigin() {
      return origin;
    }

    /**
     * The result. This may be an empty optional if an error occurred.
     *
     * @return The result, or an empty optional if no result was generated.
     */
    public Optional<TResult> getResult() {
      return Optional.ofNullable(result);
    }

    /**
     * Get the exception associated with the result or an empty optional if there
     * was no exception.
     * <p>
     * A result may not be successful, but may also not have an exception associated with it.
     *
     * @return the exception, or an empty optional if no result was produced
     */
    public Optional<Exception> getException() {
      return Optional.ofNullable(exception);
    }
  }

  /**
   * The result of a migration write.
   * <p>
   * A migration write result will always include an authoritative result, and it may contain a non-authoritative result.
   * <p>
   * Not all migration stages will execute both writes, and in the case of a write error from the authoritative source
   * then the non-authoritative write will not be executed.
   *
   * @param <TWriteResult> The result type of the write.
   */
  public static final class MigrationWriteResult<TWriteResult> {
    private final MigrationResult<TWriteResult> authoritative;
    private final MigrationResult<TWriteResult> nonAuthoritative;

    public MigrationWriteResult(@NotNull Migration.MigrationResult<TWriteResult> authoritative) {
      this.authoritative = authoritative;
      this.nonAuthoritative = null;
    }

    public MigrationWriteResult(
        @NotNull Migration.MigrationResult<TWriteResult> authoritative,
        @Nullable Migration.MigrationResult<TWriteResult> nonAuthoritative) {
      this.authoritative = authoritative;
      this.nonAuthoritative = nonAuthoritative;
    }

    /**
     * Get the authoritative result of the write.
     *
     * @return the authoritative result
     */
    public MigrationResult<TWriteResult> getAuthoritative() {
      return authoritative;
    }

    /**
     * Get the non-authoritative result.
     *
     * @return the result, or an empty optional if no result was generated
     */
    public Optional<MigrationResult<TWriteResult>> getNonAuthoritative() {
      return Optional.ofNullable(nonAuthoritative);
    }
  }

  private static final class MultiReadResult<TReadResult> {
    private final MigrationResult<TReadResult> oldResult;
    private final MigrationResult<TReadResult> newResult;

    MultiReadResult(MigrationResult<TReadResult> oldResult, MigrationResult<TReadResult> newResult) {
      this.oldResult = oldResult;
      this.newResult = newResult;
    }

    MigrationResult<TReadResult> getOld() {
      return oldResult;
    }

    MigrationResult<TReadResult> getNew() {
      return newResult;
    }
  }

  @NotNull
  private <TInput, TOutput> MigrationResult<TOutput> doSingleOp(
      @Nullable TInput payload,
      @NotNull MigrationOpTracker tracker,
      @NotNull MigrationOrigin origin,
      @NotNull Method<TInput, TOutput> method
  ) {
    tracker.invoked(origin);
    MigrationMethodResult<TOutput> res = trackLatency(payload, tracker, origin, method);
    if (res.isSuccess()) {
      return new MigrationResult<>(true, origin, res.getResult().orElse(null), null);
    }
    if (errorTracking) {
      tracker.error(origin);
    }
    return new MigrationResult<>(false, origin, null, res.getException().orElse(null));
  }

  @NotNull
  private MultiReadResult<TReadResult> doMultiRead(
      @Nullable TReadInput payload,
      @NotNull MigrationOpTracker tracker) {

    MultiReadResult<TReadResult> result;
    switch (execution.getMode()) {
      case SERIAL:
        result = doSerialRead(payload, tracker);
        break;
      case PARALLEL:
        result = doParallelRead(payload, tracker);
        break;
      default: {
        // This would likely be an implementation error from extending the execution modes and not updating this code.
        logger.error("Unrecognized execution mode while executing migration.");
        result = doSerialRead(payload, tracker);
      }
    }

    if (checker != null &&
      result.oldResult.success &&
      result.newResult.success
    ) {
      // Temporary variables for the lambda invocation.
      MigrationResult<TReadResult> finalNewResult = result.newResult;
      MigrationResult<TReadResult> finalOldResult = result.oldResult;
      // Note the individual results could be null. For instance reading
      // a DB entry that does not exist.
      tracker.consistency(() -> checker.check(finalOldResult.result,
        finalNewResult.result));
    }

    return result;
  }

  @NotNull
  private MultiReadResult<TReadResult> doSerialRead(
      @Nullable TReadInput payload,
      @NotNull MigrationOpTracker tracker) {

    MigrationSerialOrder order = execution.getOrder().orElse(MigrationSerialOrder.FIXED);

    int result = 0;
    if (order == MigrationSerialOrder.RANDOM) {
      // This random number is not used for cryptographic purposes.
      result = ThreadLocalRandom.current().nextInt(2);
    }

    MigrationResult<TReadResult> oldResult;
    MigrationResult<TReadResult> newResult;
    if (result == 0) {
      oldResult = doSingleOp(payload, tracker, MigrationOrigin.OLD, readOld);
      newResult = doSingleOp(payload, tracker, MigrationOrigin.NEW, readNew);
    } else {
      newResult = doSingleOp(payload, tracker, MigrationOrigin.NEW, readNew);
      oldResult = doSingleOp(payload, tracker, MigrationOrigin.OLD, readOld);
    }

    return new MultiReadResult<>(oldResult, newResult);
  }

  @NotNull
  private MultiReadResult<TReadResult> doParallelRead(
      @Nullable TReadInput payload,
      @NotNull MigrationOpTracker tracker) {
    List<Callable<MigrationResult<TReadResult>>> tasks = new ArrayList<>();
    tasks.add(() -> doSingleOp(payload, tracker, MigrationOrigin.OLD, readOld));
    tasks.add(() -> doSingleOp(payload, tracker, MigrationOrigin.NEW, readNew));
    try {
      List<Future<MigrationResult<TReadResult>>> futures = pool.invokeAll(tasks);

      // We do not initialize bad results here in order to reduce the amount of garbage that needs collected.
      // For happy path the result would never be used.
      MigrationResult<TReadResult> oldResult = null;
      MigrationResult<TReadResult> newResult = null;

      for (Future<MigrationResult<TReadResult>> future : futures) {
        try {
          MigrationResult<TReadResult> result = future.get();
          switch (result.origin) {
            case OLD:
              oldResult = result;
              break;
            case NEW:
              newResult = result;
              break;
          }
        } catch (Exception e) {
          // We do not know which result, just that one of them failed.
          // After this stage we can null check and add failed results.
          logger.error("An error occurred executing parallel reads: {}", e);
        }
      }

      // If either of these is null, then we know that we failed to get the task.
      // This represents a threading failure.
      if (oldResult == null) {
        oldResult = new MigrationResult<>(false, MigrationOrigin.OLD, null, null);
      }
      if (newResult == null) {
        newResult = new MigrationResult<>(false, MigrationOrigin.NEW, null, null);
      }

      return new MultiReadResult<>(oldResult, newResult);
    } catch (Exception e) {
      logger.error("An error occurred executing parallel reads: {}", e);
    }

    // Something threading related happened, and we could not get any results.
    return new MultiReadResult<>(
        new MigrationResult<>(false, MigrationOrigin.OLD, null, null),
        new MigrationResult<>(false, MigrationOrigin.NEW, null, null));
  }

  @NotNull
  private <UInput, UOutput> MigrationMethodResult<UOutput> trackLatency(
      @Nullable UInput payload,
      @NotNull MigrationOpTracker tracker,
      @NotNull MigrationOrigin origin,
      @NotNull Method<UInput, UOutput> method
  ) {
    MigrationMethodResult<UOutput> res;
    if (latencyTracking) {
      long start = System.currentTimeMillis();
      res = safeCall(payload, method);
      long stop = System.currentTimeMillis();
      tracker.latency(origin, Duration.of(stop - start, ChronoUnit.MILLIS));
    } else {
      res = safeCall(payload, method);
    }
    return res;
  }

  @NotNull
  private static <UInput, UOutput> MigrationMethodResult<UOutput> safeCall(
      @Nullable UInput payload,
      @NotNull Method<UInput, UOutput> method
  ) {
    MigrationMethodResult<UOutput> res;
    try {
      res = method.execute(payload);
    } catch (Exception e) {
      res = MigrationMethodResult.Failure(e);
    }
    return res;
  }

  @NotNull
  private MigrationResult<TReadResult> handleReadStage(
      @Nullable TReadInput payload,
      @NotNull MigrationVariation migrationVariation,
      @NotNull MigrationOpTracker tracker) {
    switch (migrationVariation.getStage()) {
      case OFF: // Intentionally falls through.
      case DUAL_WRITE: {
        return doSingleOp(payload, tracker, MigrationOrigin.OLD, readOld);
      }
      case SHADOW: {
        return doMultiRead(payload, tracker).getOld();
      }
      case LIVE: {
        return doMultiRead(payload, tracker).getNew();
      }
      case RAMP_DOWN: // Intentionally falls through.
      case COMPLETE: {
        return doSingleOp(payload, tracker, MigrationOrigin.NEW, readNew);
      }
      default: {
        // If this error occurs it would be because an additional migration stage
        // was added, but this code was not updated to support it.
        throw new RuntimeException("Unsupported migration stage.");
      }
    }
  }

  /**
   * Execute a migration based read with a payload.
   * <p>
   * To execute a read without a payload use {@link #read(String, LDContext, MigrationStage)}.
   *
   * @param key          the flag key of migration flag
   * @param context      the context for the migration
   * @param defaultStage the default migration stage
   * @param payload      an optional payload that will be passed to the new/old read implementations
   * @return the result of the read
   */
  @NotNull
  public MigrationResult<TReadResult> read(
      @NotNull String key,
      @NotNull LDContext context,
      @NotNull MigrationStage defaultStage,
      @Nullable TReadInput payload) {
    MigrationVariation migrationVariation = client.migrationVariation(key, context, defaultStage);
    MigrationOpTracker tracker = migrationVariation.getTracker();
    tracker.op(MigrationOp.READ);

    MigrationResult<TReadResult> res = handleReadStage(payload, migrationVariation, tracker);

    client.trackMigration(tracker);

    return res;
  }

  /**
   * Execute a migration based read.
   * <p>
   * To execute a read with a payload use {@link #read(String, LDContext, MigrationStage, Object)}.
   *
   * @param key          the flag key of migration flag
   * @param context      the context for the migration
   * @param defaultStage the default migration stage
   * @return the result of the read
   */
  @NotNull
  public MigrationResult<TReadResult> read(
      @NotNull String key,
      @NotNull LDContext context,
      @NotNull MigrationStage defaultStage) {
    return read(key, context, defaultStage, null);
  }

  @NotNull
  private MigrationWriteResult<TWriteResult> handleWriteStage(
      @Nullable TWriteInput payload,
      @NotNull MigrationVariation migrationVariation,
      @NotNull MigrationOpTracker tracker) {
    switch (migrationVariation.getStage()) {
      case OFF: {
        MigrationResult<TWriteResult> res = doSingleOp(payload, tracker, MigrationOrigin.OLD, writeOld);
        return new MigrationWriteResult<>(res);
      }
      case DUAL_WRITE:  // Intentionally falls through.
      case SHADOW: {
        MigrationResult<TWriteResult> oldResult = doSingleOp(payload, tracker, MigrationOrigin.OLD, writeOld);

        if (!oldResult.success) {
          return new MigrationWriteResult<>(oldResult);
        }
        MigrationResult<TWriteResult> newResult = doSingleOp(payload, tracker, MigrationOrigin.NEW, writeNew);
        return new MigrationWriteResult<>(oldResult, newResult);
      }
      case LIVE: // Intentionally falls through.
      case RAMP_DOWN: {
        MigrationResult<TWriteResult> newResult = doSingleOp(payload, tracker, MigrationOrigin.NEW, writeNew);

        if (!newResult.success) {
          return new MigrationWriteResult<>(newResult);
        }
        MigrationResult<TWriteResult> oldResult = doSingleOp(payload, tracker, MigrationOrigin.OLD, writeOld);
        return new MigrationWriteResult<>(newResult, oldResult);
      }
      case COMPLETE: {
        MigrationResult<TWriteResult> res = doSingleOp(payload, tracker, MigrationOrigin.NEW, writeNew);
        return new MigrationWriteResult<>(res);
      }
      default: {
        // If this error occurs it would be because an additional migration stage
        // was added, but this code was not updated to support it.
        throw new RuntimeException("Unsupported migration stage.");
      }
    }
  }

  /**
   * Execute a migration based write with a payload.
   * <p>
   * To execute a write without a payload use {@link #write(String, LDContext, MigrationStage)}.
   *
   * @param key          the flag key of migration flag
   * @param context      the context for the migration
   * @param defaultStage the default migration stage
   * @param payload      an optional payload that will be passed to the new/old write implementations
   * @return the result of the write
   */
  @NotNull
  public MigrationWriteResult<TWriteResult> write(
      @NotNull String key,
      @NotNull LDContext context,
      @NotNull MigrationStage defaultStage,
      @Nullable TWriteInput payload) {
    MigrationVariation migrationVariation = client.migrationVariation(key, context, defaultStage);
    MigrationOpTracker tracker = migrationVariation.getTracker();
    tracker.op(MigrationOp.WRITE);

    MigrationWriteResult<TWriteResult> res = handleWriteStage(payload, migrationVariation, tracker);

    client.trackMigration(tracker);

    return res;
  }

  /**
   * Execute a migration based write.
   * <p>
   * To execute a read with a payload use {@link #write(String, LDContext, MigrationStage, Object)}.
   *
   * @param key          the flag key of migration flag
   * @param context      the context for the migration
   * @param defaultStage the default migration stage
   * @return the result of the write
   */
  @NotNull
  public MigrationWriteResult<TWriteResult> write(
      @NotNull String key,
      @NotNull LDContext context,
      @NotNull MigrationStage defaultStage) {
    return write(key, context, defaultStage, null);
  }
}
