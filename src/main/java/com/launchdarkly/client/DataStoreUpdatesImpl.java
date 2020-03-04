package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.client.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.client.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.client.interfaces.DataStoreUpdates;

/**
 * The data source will push updates into this component. We then apply any necessary
 * transformations before putting them into the data store; currently that just means sorting
 * the data set for init(). In the future we may also use this to provide an update listener
 * capability.
 * 
 * @since 4.11.0
 */
final class DataStoreUpdatesImpl implements DataStoreUpdates {
  private final DataStore store;

  DataStoreUpdatesImpl(DataStore store) {
    this.store = store;
  }
  
  @Override
  public void init(FullDataSet<ItemDescriptor> allData) {
    store.init(DataStoreDataSetSorter.sortAllCollections(allData));
  }

  @Override
  public void upsert(DataKind kind, String key, ItemDescriptor item) {
    store.upsert(kind, key, item); 
  }
}