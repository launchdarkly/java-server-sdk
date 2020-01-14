package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.client.utils.FeatureStoreCore;

/**
 * Interface for a factory that creates some implementation of a persistent data store.
 * <p>
 * Note that currently this interface contains methods that are duplicates of the methods in
 * {@link PersistentDataStoreBuilder}. This is necessary to preserve backward compatibility with the
 * implementation of persistent data stores in earlier versions of the SDK. The new recommended usage
 * is described in {@link com.launchdarkly.client.Components#persistentDataStore}, and starting in
 * version 5.0 these redundant methods will be removed.
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
   * @deprecated Do not reference this method directly, as the {@link FeatureStoreCore} interface
   * will be replaced in 5.0.
   */
  @Deprecated
  FeatureStoreCore createPersistentDataStore();
}
