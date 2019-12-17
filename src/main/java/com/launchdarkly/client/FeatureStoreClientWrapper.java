package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.FeatureStore;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

import java.io.IOException;
import java.util.Map;

/**
 * Provides additional behavior that the client requires before or after feature store operations.
 * Currently this just means sorting the data set for init(). In the future we may also use this
 * to provide an update listener capability.
 * 
 * @since 4.6.1
 */
class FeatureStoreClientWrapper implements FeatureStore {
  private final FeatureStore store;
  
  public FeatureStoreClientWrapper(FeatureStore store) {
    this.store = store;
  }
  
  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    store.init(FeatureStoreDataSetSorter.sortAllCollections(allData));
  }

  @Override
  public <T extends VersionedData> T get(VersionedDataKind<T> kind, String key) {
    return store.get(kind, key);
  }

  @Override
  public <T extends VersionedData> Map<String, T> all(VersionedDataKind<T> kind) {
    return store.all(kind);
  }

  @Override
  public <T extends VersionedData> void delete(VersionedDataKind<T> kind, String key, int version) {
    store.delete(kind, key, version);
  }

  @Override
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    store.upsert(kind, item);
  }

  @Override
  public boolean initialized() {
    return store.initialized();
  }

  @Override
  public void close() throws IOException {
    store.close();
  }
}
