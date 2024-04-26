package com.launchdarkly.sdk.server;

/**
 * Container class for options that can be provided along with the evaluation invocation to influence various
 * behavior of the evaluation.
 */
final class EvaluationOptions {
  final boolean recordEvents;
  final boolean includeReasonsWithEvents;

  /**
   * @param recordEvents              when true, events will be recorded while the evaluation is performed
   * @param includeReasonsWithEvents  when true, any events that are recorded will include reasons
   */
  private EvaluationOptions(boolean recordEvents, boolean includeReasonsWithEvents) {
    this.recordEvents = recordEvents;
    this.includeReasonsWithEvents = includeReasonsWithEvents;

  }

  /**
   * During evaluation, no events will be recorded.
   */
  public static final EvaluationOptions NO_EVENTS = new EvaluationOptions(false, false);

  /**
   * During evaluation, events will be recorded, but they will not include reasons.
   */
  public static final EvaluationOptions EVENTS_WITHOUT_REASONS = new EvaluationOptions(true, false);

  /**
   * During evaluation, events will be recorded and those events will include reasons.
   */
  public static final EvaluationOptions EVENTS_WITH_REASONS = new EvaluationOptions(true, true);
}
