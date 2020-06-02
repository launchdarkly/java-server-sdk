package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.LoggingConfiguration;

import java.time.Duration;

final class LoggingConfigurationImpl implements LoggingConfiguration {
  private final Duration logDataSourceOutageAsErrorAfter;
  
  LoggingConfigurationImpl(Duration logDataSourceOutageAsErrorAfter) {
    this.logDataSourceOutageAsErrorAfter = logDataSourceOutageAsErrorAfter;
  }
  
  @Override
  public Duration getLogDataSourceOutageAsErrorAfter() {
    return logDataSourceOutageAsErrorAfter;
  }
}
