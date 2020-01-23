package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.EventProcessor;
import com.launchdarkly.client.interfaces.EventProcessorFactory;

interface EventProcessorFactoryWithDiagnostics extends EventProcessorFactory {
  EventProcessor createEventProcessor(String sdkKey, LDConfig config,
                                      DiagnosticAccumulator diagnosticAccumulator);
}
