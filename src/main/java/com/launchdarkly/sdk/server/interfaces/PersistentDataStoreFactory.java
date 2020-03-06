package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder;

/**
 * Interface for a factory that creates some implementation of a persistent data store.
 * <p>
 * This interface is implemented by database integrations. Usage is described in
 * {@link com.launchdarkly.sdk.server.Components#persistentDataStore}.
 * 
 * @see com.launchdarkly.sdk.server.Components
 * @since 4.12.0
 */
public interface PersistentDataStoreFactory {
  /**
   * Called internally from {@link PersistentDataStoreBuilder} to create the implementation object
   * for the specific type of data store.
   * 
   * @param context allows access to the client configuration
   * @return the implementation object
   */
  PersistentDataStore createPersistentDataStore(ClientContext context);
}
