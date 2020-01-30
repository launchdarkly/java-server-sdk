package com.launchdarkly.client;

interface EventProcessorFactoryWithDiagnostics extends EventProcessorFactory {
    EventProcessor createEventProcessor(String sdkKey, LDConfig config,
                                        DiagnosticAccumulator diagnosticAccumulator);
}
