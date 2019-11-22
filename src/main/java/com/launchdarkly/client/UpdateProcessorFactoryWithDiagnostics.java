package com.launchdarkly.client;

interface UpdateProcessorFactoryWithDiagnostics extends UpdateProcessorFactory {
    UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore,
                                          DiagnosticAccumulator diagnosticAccumulator);
}
