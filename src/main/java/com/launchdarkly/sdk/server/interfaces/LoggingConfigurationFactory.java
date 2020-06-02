package com.launchdarkly.sdk.server.interfaces;

/**
 * Interface for a factory that creates a {@link LoggingConfiguration}.
 * 
 * @see com.launchdarkly.sdk.server.Components#logging()
 * @see com.launchdarkly.sdk.server.LDConfig.Builder#logging(LoggingConfigurationFactory)
 * @since 5.0.0
 */
public interface LoggingConfigurationFactory {
  /**
   * Creates the configuration object.
   * 
   * @param basicConfiguration provides the basic SDK configuration properties
   * @return a {@link LoggingConfiguration}
   */
  public LoggingConfiguration createLoggingConfiguration(BasicConfiguration basicConfiguration);
}
