package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.internal.events.DefaultEventProcessor;
import com.launchdarkly.sdk.internal.events.Event;
import com.launchdarkly.sdk.internal.events.EventsConfiguration;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;

import java.io.IOException;

final class DefaultEventProcessorWrapper implements EventProcessor {
  private final DefaultEventProcessor eventProcessor;
  final EventsConfiguration eventsConfig; // visible for testing
  
  DefaultEventProcessorWrapper(ClientContext clientContext, EventsConfiguration eventsConfig) {
    this.eventsConfig = eventsConfig;
    LDLogger baseLogger = clientContext.getBaseLogger();
    LDLogger logger = baseLogger.subLogger(Loggers.EVENTS_LOGGER_NAME);
    eventProcessor = new DefaultEventProcessor(
        eventsConfig,
        ClientContextImpl.get(clientContext).sharedExecutor,
        clientContext.getThreadPriority(),
        logger
        );
  }

  @Override
  public void recordEvaluationEvent(LDContext context, String flagKey, int flagVersion, int variation,
      LDValue value, EvaluationReason reason, LDValue defaultValue, String prerequisiteOfFlagKey,
      boolean requireFullEvent, Long debugEventsUntilDate) {
    eventProcessor.sendEvent(new Event.FeatureRequest(
        System.currentTimeMillis(),
        flagKey,
        context,
        flagVersion,
        variation,
        value,
        defaultValue,
        reason,
        prerequisiteOfFlagKey,
        requireFullEvent,
        debugEventsUntilDate,
        false
        ));
  }

  @Override
  public void recordIdentifyEvent(LDContext context) {
    eventProcessor.sendEvent(new Event.Identify(System.currentTimeMillis(), context));
  }

  @Override
  public void recordCustomEvent(LDContext context, String eventKey, LDValue data, Double metricValue) {
    eventProcessor.sendEvent(new Event.Custom(System.currentTimeMillis(), eventKey, context, data, metricValue));
  }

  @Override
  public void flush() {
    eventProcessor.flush();
  }
  
  @Override
  public void close() throws IOException {
    eventProcessor.close();
  }
}
