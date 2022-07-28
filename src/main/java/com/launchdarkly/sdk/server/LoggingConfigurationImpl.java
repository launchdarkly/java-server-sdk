package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

import java.time.Duration;

final class LoggingConfigurationImpl implements LoggingConfiguration {
  private final String baseLoggerName;
  private final LDLogAdapter logAdapter;
  private final Duration logDataSourceOutageAsErrorAfter;
  
  LoggingConfigurationImpl(
      String baseLoggerName,
      LDLogAdapter logAdapter,
      Duration logDataSourceOutageAsErrorAfter
      ) {
    this.baseLoggerName = baseLoggerName;
    this.logAdapter = logAdapter;
    this.logDataSourceOutageAsErrorAfter = logDataSourceOutageAsErrorAfter;
  }

  @Override
  public String getBaseLoggerName() {
    return baseLoggerName;
  }
  
  @Override
  public LDLogAdapter getLogAdapter() {
    return logAdapter;
  }
  
  @Override
  public Duration getLogDataSourceOutageAsErrorAfter() {
    return logDataSourceOutageAsErrorAfter;
  }
}
