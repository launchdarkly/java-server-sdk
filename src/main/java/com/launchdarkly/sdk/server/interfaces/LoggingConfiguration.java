package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;

import java.time.Duration;

/**
 * Encapsulates the SDK's general logging configuration.
 * <p>
 * Use {@link LoggingConfigurationFactory} to construct an instance.
 * 
 * @since 5.0.0
 */
public interface LoggingConfiguration {
  /**
   * The time threshold, if any, after which the SDK will log a data source outage at {@code ERROR}
   * level instead of {@code WARN} level.
   * 
   * @return the error logging threshold, or null
   * @see LoggingConfigurationBuilder#logDataSourceOutageAsErrorAfter(java.time.Duration)
   */
  Duration getLogDataSourceOutageAsErrorAfter();
  
  /**
   * Returns the configured base logger name.
   * @return the logger name
   * @since 5.10.0
   */
  default String getBaseLoggerName() {
    return null;
  }
  
  /**
   * Returns the configured logging adapter.
   * @return the logging adapter
   * @since 5.10.0
   */
  default LDLogAdapter getLogAdapter() {
    return null;
  }
}
