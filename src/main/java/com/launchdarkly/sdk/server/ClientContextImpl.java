package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

final class ClientContextImpl implements ClientContext {
  private final String sdkKey;
  private final HttpConfiguration httpConfiguration;
  private final boolean offline;
  private final DiagnosticAccumulator diagnosticAccumulator;
  private final DiagnosticEvent.Init diagnosticInitEvent;
  
  ClientContextImpl(String sdkKey, LDConfig configuration, DiagnosticAccumulator diagnosticAccumulator) {
    this.sdkKey = sdkKey;
    this.httpConfiguration = configuration.httpConfig;
    this.offline = configuration.offline;
    if (!configuration.diagnosticOptOut && diagnosticAccumulator != null) {
      this.diagnosticAccumulator = diagnosticAccumulator;
      this.diagnosticInitEvent = new DiagnosticEvent.Init(diagnosticAccumulator.dataSinceDate, diagnosticAccumulator.diagnosticId, configuration);
    } else {
      this.diagnosticAccumulator = null;
      this.diagnosticInitEvent = null;
    }
  }

  @Override
  public String getSdkKey() {
    return sdkKey;
  }

  @Override
  public boolean isOffline() {
    return offline;
  }
  
  @Override
  public HttpConfiguration getHttpConfiguration() {
    return httpConfiguration;
  }
  
  // Note that the following two properties are package-private - they are only used by SDK internal components,
  // not any custom components implemented by an application.
  DiagnosticAccumulator getDiagnosticAccumulator() {
    return diagnosticAccumulator;
  }
  
  DiagnosticEvent.Init getDiagnosticInitEvent() {
    return diagnosticInitEvent;
  }
  
  static DiagnosticAccumulator getDiagnosticAccumulator(ClientContext context) {
    if (context instanceof ClientContextImpl) {
      return ((ClientContextImpl)context).getDiagnosticAccumulator();
    }
    return null;
  }

  static DiagnosticEvent.Init getDiagnosticInitEvent(ClientContext context) {
    if (context instanceof ClientContextImpl) {
      return ((ClientContextImpl)context).getDiagnosticInitEvent();
    }
    return null;
  }
}
