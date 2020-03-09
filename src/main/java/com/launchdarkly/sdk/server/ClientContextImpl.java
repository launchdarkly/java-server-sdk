package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.ClientContext;

final class ClientContextImpl implements ClientContext {
  private final String sdkKey;
  private final LDConfig configuration;
  private final DiagnosticAccumulator diagnosticAccumulator;
  
  ClientContextImpl(String sdkKey, LDConfig configuration, DiagnosticAccumulator diagnosticAccumulator) {
    this.sdkKey = sdkKey;
    this.configuration = configuration;
    this.diagnosticAccumulator = diagnosticAccumulator;
  }

  @Override
  public String getSdkKey() {
    return sdkKey;
  }

  @Override
  public LDConfig getConfiguration() {
    return configuration;
  }
  
  // Note that this property is package-private - it is only used by SDK internal components, not any
  // custom components implemented by an application.
  DiagnosticAccumulator getDiagnosticAccumulator() {
    return diagnosticAccumulator;
  }
  
  static DiagnosticAccumulator getDiagnosticAccumulator(ClientContext context) {
    if (context instanceof ClientContextImpl) {
      return ((ClientContextImpl)context).getDiagnosticAccumulator();
    }
    return null;
  }
}
