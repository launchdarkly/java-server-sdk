package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.CacheStats;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;

import java.io.Closeable;

/**
 * Interface for a data store that holds feature flags and related data received by the SDK.
 * <p>
 * Ordinarily, the only implementations of this interface are the default in-memory implementation,
 * which holds references to actual SDK data model objects, and the persistent data store
 * implementation that delegates to a {@link PersistentDataStore}.
 * <p> 
 * All implementations must permit concurrent access and updates.
 *
 * @since 5.0.0
 */
public interface DataStore extends Closeable {
  /**
   * Overwrites the store's contents with a set of items for each collection.
   * <p>
   * All previous data should be discarded, regardless of versioning.
   * <p>
   * The update should be done atomically. If it cannot be done atomically, then the store
   * must first add or update each item in the same order that they are given in the input
   * data, and then delete any previously stored items that were not in the input data.
   * 
   * @param allData a list of {@link DataStoreTypes.DataKind} instances and their corresponding data sets
   */
  void init(FullDataSet<ItemDescriptor> allData);
  
  /**
   * Retrieves an item from the specified collection, if available.
   * <p>
   * If the item has been deleted and the store contains a placeholder, it should
   * return that placeholder rather than null.
   * 
   * @param kind specifies which collection to use
   * @param key the unique key of the item within that collection
   * @return a versioned item that contains the stored data (or placeholder for deleted data);
   *   null if the key is unknown
   */
  ItemDescriptor get(DataKind kind, String key);
  
  /**
   * Retrieves all items from the specified collection.
   * <p>
   * If the store contains placeholders for deleted items, it should include them in
   * the results, not filter them out.
   * 
   * @param kind specifies which collection to use
   * @return a collection of key-value pairs; the ordering is not significant
   */
  KeyedItems<ItemDescriptor> getAll(DataKind kind);
  
  /**
   * Updates or inserts an item in the specified collection. For updates, the object will only be
   * updated if the existing version is less than the new version.
   * <p>
   * The SDK may pass an {@link ItemDescriptor} that contains a null, to represent a placeholder
   * for a deleted item. In that case, assuming the version is greater than any existing version of
   * that item, the store should retain that placeholder rather than simply not storing anything.
   * 
   * @param kind specifies which collection to use
   * @param key the unique key for the item within that collection
   * @param item the item to insert or update
   * @return true if the item was updated; false if it was not updated because the store contains
   *   an equal or greater version
   */
  boolean upsert(DataKind kind, String key, ItemDescriptor item);
  
  /**
   * Checks whether this store has been initialized with any data yet.
   *
   * @return true if the store contains data
   */
  boolean isInitialized();
  
  /**
   * Returns true if this data store implementation supports status monitoring.
   * <p>
   * This is normally only true for persistent data stores created with
   * {@link com.launchdarkly.sdk.server.Components#persistentDataStore(ComponentConfigurer)},
   * but it could also be true for any custom {@link DataStore} implementation that makes use of
   * {@link ClientContext#getDataStoreUpdateSink()}.
   * Returning true means that the store guarantees that if it ever enters an invalid state (that is, an
   * operation has failed or it knows that operations cannot succeed at the moment), it will publish a
   * status update, and will then publish another status update once it has returned to a valid state.
   * <p>
   * The same value will be returned from {@link DataStoreStatusProvider#isStatusMonitoringEnabled()}.
   * 
   * @return true if status monitoring is enabled
   */
  boolean isStatusMonitoringEnabled();
  
  /**
   * Returns statistics about cache usage, if this data store implementation supports caching.
   * 
   * @return a cache statistics object, or null if not applicable
   */
  CacheStats getCacheStats();
}
