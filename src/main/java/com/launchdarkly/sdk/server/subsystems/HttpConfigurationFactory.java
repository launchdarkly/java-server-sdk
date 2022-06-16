package com.launchdarkly.sdk.server.subsystems;

/**
 * Interface for a factory that creates an {@link HttpConfiguration}.
 * 
 * @see com.launchdarkly.sdk.server.Components#httpConfiguration()
 * @see com.launchdarkly.sdk.server.LDConfig.Builder#http(HttpConfigurationFactory)
 * @since 4.13.0
 */
public interface HttpConfigurationFactory {
  /**
   * Creates the configuration object.
   * 
   * @param clientContext allows access to the client configuration
   * @return an {@link HttpConfiguration}
   */
  public HttpConfiguration createHttpConfiguration(ClientContext clientContext);
}
