package com.launchdarkly.client.interfaces;

/**
 * Interface for a factory that creates an {@link HttpConfiguration}.
 * 
 * @see com.launchdarkly.client.Components#httpConfiguration()
 * @see com.launchdarkly.client.LDConfig.Builder#http(HttpConfigurationFactory)
 * @since 4.13.0
 */
public interface HttpConfigurationFactory {
  /**
   * Creates the configuration object.
   * @return an {@link HttpConfiguration}
   */
  public HttpConfiguration createHttpConfiguration();
}
