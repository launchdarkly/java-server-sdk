package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;

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
}
