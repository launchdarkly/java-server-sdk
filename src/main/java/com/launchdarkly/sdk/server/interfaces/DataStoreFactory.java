package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.Components;

import java.util.function.Consumer;

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
   * @param statusUpdater if non-null, the store can call this method to provide an update of its status;
   *   if the store never calls this method, the SDK will report its status as "available"
   * @return a {@link DataStore}
   */
  DataStore createDataStore(ClientContext context, Consumer<DataStoreStatusProvider.Status> statusUpdater);
}
