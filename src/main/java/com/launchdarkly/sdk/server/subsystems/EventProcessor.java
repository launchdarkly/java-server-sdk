package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.MigrationOpTracker;

import java.io.Closeable;

/**
 * Interface for an object that can send or store analytics events.
 * <p>
 * Application code normally does not need to interact with this interface. It is provided
 * to allow a custom implementation or test fixture to be substituted for the SDK's normal
 * analytics event logic.
 * 
 * @since 4.0.0
 */
public interface EventProcessor extends Closeable {
  /**
   * Constant used with {@link #recordEvaluationEvent}.
   */
  public static final int NO_VERSION = -1;
  
  /**
   * Records the action of evaluating a feature flag.
   * <p>
   * Depending on the feature flag properties and event properties, this may be transmitted to
   * the events service as an individual event, or may only be added into summary data.
   * 
   * @param context the evaluation context
   * @param flagKey key of the feature flag that was evaluated
   * @param flagVersion the version of the flag, or {@link #NO_VERSION} if the flag was not found
   * @param variation the result variation index, or {@link EvaluationDetail#NO_VARIATION} if evaluation failed
   * @param value the result value
   * @param reason the evaluation reason, or null if the reason was not requested
   * @param defaultValue the default value parameter for the evaluation
   * @param prerequisiteOfFlagKey the key of the flag that this flag was evaluated as a prerequisite of,
   *   or null if this flag was evaluated for itself
   * @param requireFullEvent true if full-fidelity analytics events should be sent for this flag
   * @param debugEventsUntilDate if non-null, debug events are to be generated until this millisecond time
   * @param excludeFromSummaries true if the event evaluation should not be included in summaries
   * @param samplingRatio ratio used to control event sampling
   */
  void recordEvaluationEvent(
      LDContext context,
      String flagKey,
      int flagVersion,
      int variation,
      LDValue value,
      EvaluationReason reason,
      LDValue defaultValue,
      String prerequisiteOfFlagKey,
      boolean requireFullEvent,
      Long debugEventsUntilDate,
      boolean excludeFromSummaries,
      Long samplingRatio
      );
  
  /**
   * Registers an evaluation context, as when the SDK's {@code identify} method is called.
   * 
   * @param context the evaluation context
   */
  void recordIdentifyEvent(
      LDContext context
      );
  
  /**
   * Creates a custom event, as when the SDK's {@code track} method is called.
   * 
   * @param context the evaluation context
   * @param eventKey the event key
   * @param data optional custom data provided for the event, may be null or {@link LDValue#ofNull()} if not used
   * @param metricValue optional numeric metric value provided for the event, or null
   */
  void recordCustomEvent(
      LDContext context,
      String eventKey,
      LDValue data,
      Double metricValue
      );

  /**
   * Creates a migration event when the SDK's {@code trackMigration} method is called.
   *
   * @param tracker Migration tracker which was used to track details of the migration operation.
   */
  void recordMigrationEvent(MigrationOpTracker tracker);
  
  /**
   * Specifies that any buffered events should be sent as soon as possible, rather than waiting
   * for the next flush interval. This method is asynchronous, so events still may not be sent
   * until a later time. However, calling {@link Closeable#close()} will synchronously deliver
   * any events that were not yet delivered prior to shutting down.
   */
  void flush();
}
