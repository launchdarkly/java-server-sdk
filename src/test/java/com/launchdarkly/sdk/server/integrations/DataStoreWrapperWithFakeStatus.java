package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingDeque;

@SuppressWarnings("javadoc")
public class DataStoreWrapperWithFakeStatus implements DataStore, DataStoreStatusProvider {
  private final DataStore store;
  private final PersistentDataStoreStatusManager statusManager;
  private final BlockingQueue<FullDataSet<ItemDescriptor>> initQueue = new LinkedBlockingDeque<>(); 
  private volatile boolean available;
  
  public DataStoreWrapperWithFakeStatus(DataStore store, boolean refreshOnRecovery) {
    this.store = store;
    this.statusManager = new PersistentDataStoreStatusManager(refreshOnRecovery, true, new Callable<Boolean>() {
      public Boolean call() throws Exception {
        return available;
      }
    });
    this.available = true;
  }

  public void updateAvailability(boolean available) {
    this.available = available;
    statusManager.updateAvailability(available);
  }
  
  public FullDataSet<ItemDescriptor> awaitInit() {
    try {
      return initQueue.take();
    } catch (InterruptedException e) { // shouldn't happen in our tests
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public void close() throws IOException {
    store.close();
    statusManager.close();
  }

  @Override
  public Status getStoreStatus() {
    return new PersistentDataStoreStatusManager.StatusImpl(available, false);
  }

  @Override
  public void addStatusListener(StatusListener listener) {
    statusManager.addStatusListener(listener);
  }

  @Override
  public void removeStatusListener(StatusListener listener) {
    statusManager.removeStatusListener(listener);
  }

  @Override
  public CacheStats getCacheStats() {
    return null;
  }

  @Override
  public void init(FullDataSet<ItemDescriptor> allData) {
    store.init(allData);
  }

  @Override
  public ItemDescriptor get(DataKind kind, String key) {
    return store.get(kind, key);
  }

  @Override
  public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
    return store.getAll(kind);
  }

  @Override
  public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
    return store.upsert(kind, key, item);
  }

  @Override
  public boolean isInitialized() {
    return store.isInitialized();
  }
}
