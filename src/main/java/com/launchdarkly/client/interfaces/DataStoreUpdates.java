package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.client.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.client.interfaces.DataStoreTypes.ItemDescriptor;

/**
 * Interface that a data source implementation will use to push data into the underlying
 * data store.
 * <p>
 * This layer of indirection allows the SDK to perform any other necessary operations that must
 * happen when data is updated, by providing its own implementation of {@link DataStoreUpdates}.
 * 
 * @since 5.0.0
 */
public interface DataStoreUpdates {
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
   */
  void upsert(DataKind kind, String key, ItemDescriptor item);  
}
