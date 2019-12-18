package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.Components;

/**
 * Interface for a factory that creates some implementation of {@link DataStore}.
 * @see Components
 * @since 5.0.0
 */
public interface DataStoreFactory {
  /**
   * Creates an implementation instance.
   * @return a {@link DataStore}
   */
  DataStore createDataStore();
}
