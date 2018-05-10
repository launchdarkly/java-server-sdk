package com.launchdarkly.client;

/**
 * Interface for a factory that creates some implementation of {@link FeatureStore}.
 * @see Components
 * @since 4.0.0
 */
public interface FeatureStoreFactory {
  /**
   * Creates an implementation instance.
   * @return a {@link FeatureStore}
   */
  FeatureStore createFeatureStore();
}
