package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;

final class DataStoreStatusProviderImpl implements DataStoreStatusProvider {
  private final DataStore store;
  private final DataStoreUpdatesImpl dataStoreUpdates;

  DataStoreStatusProviderImpl(
      DataStore store,
      DataStoreUpdatesImpl dataStoreUpdates
      ) {
    this.store = store;
    this.dataStoreUpdates = dataStoreUpdates;
  }
  
  @Override
  public Status getStoreStatus() {
    return dataStoreUpdates.lastStatus.get();
  }

  @Override
  public void addStatusListener(StatusListener listener) {
    dataStoreUpdates.statusBroadcaster.register(listener);
  }

  @Override
  public void removeStatusListener(StatusListener listener) {
    dataStoreUpdates.statusBroadcaster.unregister(listener);
  }

  @Override
  public boolean isStatusMonitoringEnabled() {
    return store.isStatusMonitoringEnabled();
  }

  @Override
  public CacheStats getCacheStats() {
    return store.getCacheStats();
  }
}
