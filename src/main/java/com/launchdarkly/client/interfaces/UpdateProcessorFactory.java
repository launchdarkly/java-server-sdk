package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.LDConfig;

/**
 * Interface for a factory that creates some implementation of {@link UpdateProcessor}.
 * @see Components
 * @since 4.0.0
 */
public interface UpdateProcessorFactory {
  /**
   * Creates an implementation instance.
   * @param sdkKey the SDK key for your LaunchDarkly environment
   * @param config the LaunchDarkly configuration
   * @param featureStore the {@link FeatureStore} to use for storing the latest flag state
   * @return an {@link UpdateProcessor}
   */
  public UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config, FeatureStore featureStore);
}
