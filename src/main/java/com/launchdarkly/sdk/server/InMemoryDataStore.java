package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DiagnosticDescription;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe, versioned store for feature flags and related data based on a
 * {@link HashMap}. This is the default implementation of {@link DataStore}.
 * 
 * As of version 5.0.0, this is package-private; applications must use the factory method
 * {@link Components#inMemoryDataStore()}.
 */
class InMemoryDataStore implements DataStore, DiagnosticDescription {
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Map<DataKind, Map<String, ItemDescriptor>> allData = new HashMap<>();
  private volatile boolean initialized = false;

  @Override
  public void init(FullDataSet<ItemDescriptor> allData) {
    try {
      lock.writeLock().lock();
      this.allData.clear();
      for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> e0: allData.getData()) {
        DataKind kind = e0.getKey();
        Map<String, ItemDescriptor> itemsMap = new HashMap<>();
        for (Map.Entry<String, ItemDescriptor> e1: e0.getValue().getItems()) {
          itemsMap.put(e1.getKey(), e1.getValue());
        }
        this.allData.put(kind, itemsMap);
      }
      initialized = true;
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public ItemDescriptor get(DataKind kind, String key) {
    try {
      lock.readLock().lock();
      Map<String, ItemDescriptor> items = allData.get(kind);
      if (items == null) {
        return null;
      }
      return items.get(key);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
    try {
      lock.readLock().lock();
      Map<String, ItemDescriptor> items = allData.get(kind);
      if (items == null) {
        return new KeyedItems<>(null);
      }
      return new KeyedItems<>(ImmutableList.copyOf(items.entrySet()));
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
    try {
      lock.writeLock().lock();
      Map<String, ItemDescriptor> items = allData.get(kind);
      if (items == null) {
        items = new HashMap<>();
        allData.put(kind, items);
      }
      ItemDescriptor old = items.get(key);
      if (old == null || old.getVersion() < item.getVersion()) {
        items.put(key, item);
        return true;
      }
      return false;
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public boolean isInitialized() {
    return initialized;
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

  @Override
  public LDValue describeConfiguration(LDConfig config) {
    return LDValue.of("memory");
  }
}
