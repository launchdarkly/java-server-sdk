package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.Components;

/**
 * Interface for a factory that creates some implementation of {@link DataStore}.
 * @see Components
 * @since 4.11.0
 */
public interface DataStoreFactory {
  /**
   * Creates an implementation instance.
   * 
   * @param context allows access to the client configuration
   * @return a {@link DataStore}
   */
  DataStore createDataStore(ClientContext context);
}