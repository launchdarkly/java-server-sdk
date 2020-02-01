package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.DataStoreUpdates;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

import java.util.Map;

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
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    store.init(DataStoreDataSetSorter.sortAllCollections(allData));
  }

  @Override
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    store.upsert(kind, item); 
  }
}