package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.server.Components;

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
   * @param dataStoreUpdates the data store can use this object to report information back to
   *   the SDK if desired
   * @return a {@link DataStore}
   */
  DataStore createDataStore(ClientContext context, DataStoreUpdates dataStoreUpdates);
}
