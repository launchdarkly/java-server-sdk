package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;

import java.io.IOException;

/**
 * An {@link EventProcessor} that does nothing when invoked.
 */
class NoOpEventProcessor implements EventProcessor {

  @Override
  public void recordEvaluationEvent(LDContext context, String flagKey, int flagVersion, int variation, LDValue value,
                                    EvaluationReason reason, LDValue defaultValue, String prerequisiteOfFlagKey,
                                    boolean requireFullEvent, Long debugEventsUntilDate, boolean excludeFromSummaries,
                                    Long samplingRatio) {
    // no-op
  }

  @Override
  public void recordIdentifyEvent(LDContext context) {
    // no-op
  }

  @Override
  public void recordCustomEvent(LDContext context, String eventKey, LDValue data, Double metricValue) {
    // no-op
  }

  @Override
  public void recordMigrationEvent(MigrationOpTracker tracker) {
    // no-op
  }

  @Override
  public void flush() {
    // no-op
  }

  @Override
  public void close() throws IOException {
    // no-op
  }
}
