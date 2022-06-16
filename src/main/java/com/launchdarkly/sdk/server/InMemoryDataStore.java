package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.CacheStats;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A thread-safe, versioned store for feature flags and related data based on a
 * {@link HashMap}. This is the default implementation of {@link DataStore}.
 * 
 * As of version 5.0.0, this is package-private; applications must use the factory method
 * {@link Components#inMemoryDataStore()}.
 */
class InMemoryDataStore implements DataStore {
  private volatile ImmutableMap<DataKind, Map<String, ItemDescriptor>> allData = ImmutableMap.of();
  private volatile boolean initialized = false;
  private Object writeLock = new Object();

  @Override
  public void init(FullDataSet<ItemDescriptor> allData) {
    synchronized (writeLock) {
      ImmutableMap.Builder<DataKind, Map<String, ItemDescriptor>> newData = ImmutableMap.builder();
      for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> entry: allData.getData()) {
        newData.put(entry.getKey(), ImmutableMap.copyOf(entry.getValue().getItems()));
      }
      this.allData = newData.build(); // replaces the entire map atomically
      this.initialized = true;
    }
  }

  @Override
  public ItemDescriptor get(DataKind kind, String key) {
    Map<String, ItemDescriptor> items = allData.get(kind);
    if (items == null) {
      return null;
    }
    return items.get(key);
  }

  @Override
  public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
    Map<String, ItemDescriptor> items = allData.get(kind);
    if (items == null) {
      return new KeyedItems<>(null);
    }
    return new KeyedItems<>(ImmutableList.copyOf(items.entrySet()));
  }

  @Override
  public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
    synchronized (writeLock) {
      Map<String, ItemDescriptor> existingItems = this.allData.get(kind);
      ItemDescriptor oldItem = null;
      if (existingItems != null) {
        oldItem = existingItems.get(key);
        if (oldItem != null && oldItem.getVersion() >= item.getVersion()) {
          return false;
        }
      }
      // The following logic is necessary because ImmutableMap.Builder doesn't support overwriting an existing key
      ImmutableMap.Builder<DataKind, Map<String, ItemDescriptor>> newData = ImmutableMap.builder();
      for (Map.Entry<DataKind, Map<String, ItemDescriptor>> e: this.allData.entrySet()) {
        if (!e.getKey().equals(kind)) {
          newData.put(e.getKey(), e.getValue());
        }
      }
      if (existingItems == null) {
        newData.put(kind, ImmutableMap.of(key, item));
      } else {
        ImmutableMap.Builder<String, ItemDescriptor> itemsBuilder = ImmutableMap.builder();
        if (oldItem == null) {
          itemsBuilder.putAll(existingItems);
        } else {
          for (Map.Entry<String, ItemDescriptor> e: existingItems.entrySet()) {
            if (!e.getKey().equals(key)) {
              itemsBuilder.put(e.getKey(), e.getValue());
            }
          }
        }
        itemsBuilder.put(key, item);
        newData.put(kind, itemsBuilder.build());
      }
      this.allData = newData.build(); // replaces the entire map atomically
      return true;
    }
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }
  
  @Override
  public boolean isStatusMonitoringEnabled() {
    return false;
  }
  
  @Override
  public CacheStats getCacheStats() {
    return null;
  }
  
  /**
   * Does nothing; this class does not have any resources to release
   *
   * @throws IOException will never happen
   */
  @Override
  public void close() throws IOException {
    return;
  }
}
