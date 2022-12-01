package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.server.DataStoreTestTypes.TestItem;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.SerializedItemDescriptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("javadoc")
public final class MockPersistentDataStore implements PersistentDataStore {
  public static final class MockDatabaseInstance {
    Map<String, Map<DataKind, Map<String, SerializedItemDescriptor>>> dataByPrefix = new HashMap<>();
    Map<String, AtomicBoolean> initedByPrefix = new HashMap<>();
  }
  
  public final Map<DataKind, Map<String, SerializedItemDescriptor>> data;
  public final AtomicBoolean inited;
  public final AtomicInteger initedCount = new AtomicInteger(0);
  public volatile int initedQueryCount;
  public volatile int getQueryCount;
  public volatile boolean persistOnlyAsString;
  public volatile boolean unavailable;
  public volatile RuntimeException fakeError;
  public volatile Runnable updateHook;
  
  public MockPersistentDataStore() {
    this.data = new HashMap<>();
    this.inited = new AtomicBoolean();
  }
  
  public MockPersistentDataStore(MockDatabaseInstance sharedData, String prefix) {
    synchronized (sharedData) {
      if (sharedData.dataByPrefix.containsKey(prefix)) {
        this.data = sharedData.dataByPrefix.get(prefix);
        this.inited = sharedData.initedByPrefix.get(prefix);
      } else {
        this.data = new HashMap<>();
        this.inited = new AtomicBoolean();
        sharedData.dataByPrefix.put(prefix, this.data);
        sharedData.initedByPrefix.put(prefix, this.inited);        
      }
    }
  }
  
  @Override
  public void close() throws IOException {
  }

  @Override
  public SerializedItemDescriptor get(DataKind kind, String key) {
    getQueryCount++;
    maybeThrow();
    if (data.containsKey(kind)) {
      SerializedItemDescriptor item = data.get(kind).get(key);
      if (item != null) {
        if (persistOnlyAsString) {
          // This simulates the kind of store implementation that can't track metadata separately  
          return new SerializedItemDescriptor(0, false, item.getSerializedItem());
        } else {
          return item;
        }
      }
    }
    return null;
  }

  @Override
  public KeyedItems<SerializedItemDescriptor> getAll(DataKind kind) {
    maybeThrow();
    return data.containsKey(kind) ? new KeyedItems<>(ImmutableList.copyOf(data.get(kind).entrySet())) : new KeyedItems<>(null);
  }

  @Override
  public void init(FullDataSet<SerializedItemDescriptor> allData) {
    initedCount.incrementAndGet();
    maybeThrow();
    data.clear();
    for (Map.Entry<DataKind, KeyedItems<SerializedItemDescriptor>> entry: allData.getData()) {
      DataKind kind = entry.getKey();
      HashMap<String, SerializedItemDescriptor> items = new LinkedHashMap<>();
      for (Map.Entry<String, SerializedItemDescriptor> e: entry.getValue().getItems()) {
        items.put(e.getKey(), storableItem(kind, e.getValue()));
      }
      data.put(kind, items);
    }
    inited.set(true);
  }

  @Override
  public boolean upsert(DataKind kind, String key, SerializedItemDescriptor item) {
    maybeThrow();
    if (updateHook != null) {
      updateHook.run();
    }
    if (!data.containsKey(kind)) {
      data.put(kind, new HashMap<>());
    }
    Map<String, SerializedItemDescriptor> items = data.get(kind);
    SerializedItemDescriptor oldItem = items.get(key);
    if (oldItem != null) {
      // If persistOnlyAsString is true, simulate the kind of implementation where we can't see the
      // version as a separate attribute in the database and must deserialize the item to get it.
      int oldVersion = persistOnlyAsString ?
          kind.deserialize(oldItem.getSerializedItem()).getVersion() :
          oldItem.getVersion();
      if (oldVersion >= item.getVersion()) {
        return false;
      }
    }
    items.put(key, storableItem(kind, item));
    return true;
  }

  @Override
  public boolean isInitialized() {
    maybeThrow();
    initedQueryCount++;
    return inited.get();
  }
  
  @Override
  public boolean isStoreAvailable() {
    return !unavailable;
  }
  
  public void forceSet(DataKind kind, TestItem item) {
    forceSet(kind, item.key, item.toSerializedItemDescriptor());
  }

  public void forceSet(DataKind kind, String key, SerializedItemDescriptor item) {
    if (!data.containsKey(kind)) {
      data.put(kind, new HashMap<>());
    }
    Map<String, SerializedItemDescriptor> items = data.get(kind);
    items.put(key, storableItem(kind, item));
  }

  public void forceRemove(DataKind kind, String key) {
    if (data.containsKey(kind)) {
      data.get(kind).remove(key);
    }
  }
  
  private SerializedItemDescriptor storableItem(DataKind kind, SerializedItemDescriptor item) {
    if (item.isDeleted() && !persistOnlyAsString) {
      // This simulates the kind of store implementation that *can* track metadata separately, so we don't
      // have to persist the placeholder string for deleted items
      return new SerializedItemDescriptor(item.getVersion(), true, null);
    }
    return item;
  }
  
  private void maybeThrow() {
    if (fakeError != null) {
      throw fakeError;
    }
  }
}