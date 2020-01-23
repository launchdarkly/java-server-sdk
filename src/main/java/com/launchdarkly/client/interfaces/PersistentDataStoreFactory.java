package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.integrations.PersistentDataStoreBuilder;

/**
 * Interface for a factory that creates some implementation of a persistent data store.
 * <p>
 * This interface is implemented by database integrations. Usage is described in
 * {@link com.launchdarkly.client.Components#persistentDataStore}.
 * 
 * @see com.launchdarkly.client.Components
 * @since 4.11.0
 */
public interface PersistentDataStoreFactory {
  /**
   * Called internally from {@link PersistentDataStoreBuilder} to create the implementation object
   * for the specific type of data store.
   * 
   * @return the implementation object
   */
  PersistentDataStore createPersistentDataStore();
}
