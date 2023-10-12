package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.internal.events.Event;
import com.launchdarkly.sdk.internal.events.Sampler;
import com.launchdarkly.sdk.server.interfaces.ConsistencyCheck;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

/**
 * Used to track information related to a migration operation.
 */
public class MigrationOpTracker {

  private boolean oldError = false;
  private boolean newError = false;

  private boolean oldInvoked = false;

  private boolean newInvoked = false;

  private Duration oldLatency = null;
  private Duration newLatency = null;

  private MigrationOp operation = null;

  private ConsistencyCheck consistencyCheck = ConsistencyCheck.NOT_CHECKED;

  private final DataModel.FeatureFlag flag;

  private final MigrationStage stage;

  private final MigrationStage defaultStage;

  private final EvaluationDetail<String> evaluationDetail;

  private final LDContext context;

  private final String flagKey;

  private final long checkRatio;

  private final LDLogger logger;

  /**
   * Interface for specifying the callback function for the consistency check.
   * This should remain a SAM (Single Abstract Method) to facilitate a lambda callback.
   */
  public interface Checker {
    boolean check();
  }

  MigrationOpTracker(
      @NotNull String flagKey,
      @Nullable DataModel.FeatureFlag flag,
      @NotNull EvaluationDetail<String> evaluationDetail,
      @NotNull MigrationStage defaultStage,
      @NotNull MigrationStage stage,
      @NotNull LDContext context,
      long checkRatio,
      @NotNull LDLogger logger) {
    this.flag = flag;
    this.stage = stage;
    this.defaultStage = defaultStage;
    this.evaluationDetail = evaluationDetail;
    this.context = context;
    this.flagKey = flagKey;
    this.checkRatio = checkRatio;
    this.logger = logger;
  }

  /**
   * Sets the migration related operation associated with these tracking measurements.
   *
   * @param op the operation being tracked
   */
  public synchronized void op(@NotNull MigrationOp op) {
    operation = op;
  }

  /**
   * Report that an error has occurred for the specified origin.
   *
   * @param origin the origin of the error
   */
  public synchronized void error(@NotNull MigrationOrigin origin) {
    switch (origin) {
      case OLD:
        oldError = true;
        break;
      case NEW:
        newError = true;
        break;
    }
  }

  /**
   * Check the consistency of a read result. This method should be invoked if the `check` function
   * is defined for the migration and both reads ("new"/"old") were done.
   * <p>
   * The function will use the checkRatio to determine if the check should be executed, and it
   * will record the result.
   * <p>
   * If the consistency check function throws an exception, then no measurement for consistency will be included
   * in the generated migration op event.
   * <p>
   * Example calling the check function from the migration config.
   * <pre>
   * if (checker != null &amp;&amp;
   *   oldResult.success &amp;&amp;
   *   newResult.success
   * ) {
   *   // Temporary variables for the lambda invocation.
   *   MigrationResult@lt;TReadResult@gt; finalNewResult = newResult;
   *   MigrationResult@lt;TReadResult@gt; finalOldResult = oldResult;
   *
   *   tracker.consistency(() -&gt; checker.check(finalOldResult.result,
   *   finalNewResult.result));
   * }
   * </pre>
   *
   * @param checker The function which executes the check. This is not the `check` function from the
   *                migration options, but instead should be a parameter-less function that calls that function.
   */
  public synchronized void consistency(@NotNull Checker checker) {
    if (Sampler.shouldSample(checkRatio)) {
      try {
        consistencyCheck = checker.check() ? ConsistencyCheck.CONSISTENT : ConsistencyCheck.INCONSISTENT;
      } catch(Exception e) {
        logger.error("Exception when executing consistency check function for migration '{}' the consistency" +
          " check will not be included in the generated migration op event. Exception: {}", flagKey, e);
      }
    }
  }

  /**
   * Report the latency of an operation.
   *
   * @param origin   the origin the latency is being reported for
   * @param duration the latency of the operation
   */
  public synchronized void latency(@NotNull MigrationOrigin origin, @NotNull Duration duration) {
    switch (origin) {
      case OLD:
        oldLatency = duration;
        break;
      case NEW:
        newLatency = duration;
        break;
    }
  }

  /**
   * Call this to report that an origin was invoked (executed). There are some situations where the
   * expectation is that both the old and new implementation will be used, but with writes
   * it is possible that the non-authoritative will not execute. Reporting the execution allows
   * for more accurate analytics.
   *
   * @param origin the origin that was invoked
   */
  public synchronized void invoked(@NotNull MigrationOrigin origin) {
    switch (origin) {
      case OLD:
        oldInvoked = true;
        break;
      case NEW:
        newInvoked = true;
        break;
    }
  }

  private boolean invokedForOrigin(MigrationOrigin origin) {
    if (origin == MigrationOrigin.OLD) {
      return oldInvoked;
    }
    return newInvoked;
  }

  private Duration latencyForOrigin(MigrationOrigin origin) {
    if (origin == MigrationOrigin.OLD) {
      return oldLatency;
    }
    return newLatency;
  }

  private boolean errorForOrigin(MigrationOrigin origin) {
    if (origin == MigrationOrigin.OLD) {
      return oldError;
    }
    return newError;
  }

  private boolean checkOriginEventConsistency(MigrationOrigin origin) {
    if (invokedForOrigin(origin)) {
      return true;
    }

    // The origin was not invoked so any measurements involving it represent an inconsistency.

    String logTag = String.format("For migration op(%s) flagKey(%s):", operation, flagKey);

    if (latencyForOrigin(origin) != null) {
      logger.error("{} Latency was recorded for {}, but that origin was not invoked.", logTag, origin);
      return false;
    }

    if (errorForOrigin(origin)) {
      logger.error("{} Error reported for {}, but that origin was not invoked.", logTag, origin);
      return false;
    }

    if (this.consistencyCheck != ConsistencyCheck.NOT_CHECKED) {
      logger.error("{} Consistency check was done, but {} was not invoked." +
          " Both \"old\" and \"new\" must be invoked to do a comparison.", logTag, origin);
      return false;
    }
    return true;
  }

  /**
   * Check for inconsistencies in the data used to generate an event.
   * Log any inconsistencies found.
   */
  private boolean checkEventConsistency() {
    return checkOriginEventConsistency(MigrationOrigin.OLD) &&
    checkOriginEventConsistency(MigrationOrigin.NEW);
  }

  synchronized Optional<Event.MigrationOp> createEvent() {
    if(flagKey.isEmpty()) {
      logger.error("The migration was executed against an empty flag key and no event will be created.");
      return Optional.empty();
    }
    if (operation == null) {
      logger.error("The operation must be set, using \"op\" before an event can be created.");
      return Optional.empty();
    }
    if (!newInvoked && !oldInvoked) {
      logger.error("The migration invoked neither the \"old\" or \"new\" implementation" +
          " and an event cannot be generated.");
      return Optional.empty();
    }
    if (!context.isValid()) {
      logger.error("The migration was not done against a valid context and cannot generate an event.");
      return Optional.empty();
    }

    if(!checkEventConsistency()) {
      return Optional.empty();
    }

    long samplingRatio = 1;
    if (flag != null && flag.getSamplingRatio() != null) {
      samplingRatio = flag.getSamplingRatio();
    }

    int flagVersion = -1;
    flagVersion = flag != null ? flag.getVersion() : -1;

    Event.MigrationOp.InvokedMeasurement invokedMeasurement =
        new Event.MigrationOp.InvokedMeasurement(oldInvoked, newInvoked);


    Event.MigrationOp.LatencyMeasurement latencyMeasurement = null;
    if (oldLatency != null | newLatency != null) {
      latencyMeasurement = new Event.MigrationOp.LatencyMeasurement(
          oldLatency != null ? oldLatency.toMillis() : null,
          newLatency != null ? newLatency.toMillis() : null);
    }

    Event.MigrationOp.ConsistencyMeasurement consistencyMeasurement = null;
    if (consistencyCheck != ConsistencyCheck.NOT_CHECKED) {
      consistencyMeasurement = new Event.MigrationOp.ConsistencyMeasurement(
          consistencyCheck == ConsistencyCheck.CONSISTENT,
          checkRatio);
    }

    Event.MigrationOp.ErrorMeasurement errorMeasurement = null;

    if (oldError || newError) {
      errorMeasurement = new Event.MigrationOp.ErrorMeasurement(oldError, newError);
    }

    return Optional.of(new Event.MigrationOp(
        System.currentTimeMillis(),
        context,
        flagKey,
        evaluationDetail.getVariationIndex(),
        flagVersion,
        LDValue.of(stage.toString()),
        LDValue.of(defaultStage.toString()),
        evaluationDetail.getReason(),
        samplingRatio,
        operation.toString(),
        invokedMeasurement,
        consistencyMeasurement,
        latencyMeasurement,
        errorMeasurement
    ));
  }
}
