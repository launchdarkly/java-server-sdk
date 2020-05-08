package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;

/**
 * Interface that a data source implementation will use to push data into the SDK.
 * <p>
 * The data source interacts with this object, rather than manipulating the data store directly, so
 * that the SDK can perform any other necessary operations that must happen when data is updated.
 * 
 * @since 5.0.0
 */
public interface DataSourceUpdates {
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
  
  /**
   * Returns an object that provides status tracking for the data store, if applicable.
   * <p>
   * This may be useful if the data source needs to be aware of storage problems that might require it
   * to take some special action: for instance, if a database outage may have caused some data to be
   * lost and therefore the data should be re-requested from LaunchDarkly.
   * 
   * @return a {@link DataStoreStatusProvider}
   */
  DataStoreStatusProvider getDataStoreStatusProvider();
}
