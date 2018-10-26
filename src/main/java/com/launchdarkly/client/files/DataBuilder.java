package com.launchdarkly.client.files;

import com.launchdarkly.client.VersionedData;
import com.launchdarkly.client.VersionedDataKind;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal data structure that organizes flag/segment data into the format that the feature store
 * expects. Will throw an exception if we try to add the same flag or segment key more than once.
 */
class DataBuilder {
  private final Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData = new HashMap<>();
  
  public Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> build() {
    return allData;
  }
  
  public void add(VersionedDataKind<?> kind, VersionedData item) throws DataLoaderException {
    @SuppressWarnings("unchecked")
    Map<String, VersionedData> items = (Map<String, VersionedData>)allData.get(kind);
    if (items == null) {
      items = new HashMap<String, VersionedData>();
      allData.put(kind, items);
    }
    if (items.containsKey(item.getKey())) {
      throw new DataLoaderException("in " + kind.getNamespace() + ", key \"" + item.getKey() + "\" was already defined", null, null);
    }
    items.put(item.getKey(), item);
  }
}
