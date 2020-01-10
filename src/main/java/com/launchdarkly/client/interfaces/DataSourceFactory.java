package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.LDConfig;

/**
 * Interface for a factory that creates some implementation of {@link DataSource}.
 * @see Components
 * @since 5.0.0
 */
public interface DataSourceFactory {
  /**
   * Creates an implementation instance.
   * @param sdkKey the SDK key for your LaunchDarkly environment
   * @param config the LaunchDarkly configuration
   * @param dataStore the {@link DataStore} to use for storing the latest flag state
   * @return an {@link DataSource}
   */
  public DataSource createDataSource(String sdkKey, LDConfig config, DataStore dataStore);
}
