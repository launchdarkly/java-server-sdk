package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;

import java.time.Duration;

/**
 * Encapsulates the SDK's general logging configuration.
 * <p>
 * Use {@link LoggingConfigurationBuilder} to construct an instance.
 * 
 * @since 5.0.0
 */
public final class LoggingConfiguration {
  private final String baseLoggerName;
  private final LDLogAdapter logAdapter;
  private final Duration logDataSourceOutageAsErrorAfter;

  /**
   * Creates an instance.
   * 
   * @param baseLoggerName see {@link #getBaseLoggerName()}
   * @param logAdapter see {@link #getLogAdapter()}
   * @param logDataSourceOutageAsErrorAfter see {@link #getLogDataSourceOutageAsErrorAfter()}
   */
  public LoggingConfiguration(
      String baseLoggerName,
      LDLogAdapter logAdapter,
      Duration logDataSourceOutageAsErrorAfter
      ) {
    this.baseLoggerName = baseLoggerName;
    this.logAdapter = logAdapter;
    this.logDataSourceOutageAsErrorAfter = logDataSourceOutageAsErrorAfter;
  }

  /**
   * Returns the configured base logger name.
   * @return the logger name
   * @since 5.10.0
   */
  public String getBaseLoggerName() {
    return baseLoggerName;
  }

  /**
   * Returns the configured logging adapter.
   * @return the logging adapter
   * @since 5.10.0
   */
  public LDLogAdapter getLogAdapter() {
    return logAdapter;
  }

  /**
   * The time threshold, if any, after which the SDK will log a data source outage at {@code ERROR}
   * level instead of {@code WARN} level.
   * 
   * @return the error logging threshold, or null
   * @see LoggingConfigurationBuilder#logDataSourceOutageAsErrorAfter(java.time.Duration)
   */
  public Duration getLogDataSourceOutageAsErrorAfter() {
    return logDataSourceOutageAsErrorAfter;
  }
}
