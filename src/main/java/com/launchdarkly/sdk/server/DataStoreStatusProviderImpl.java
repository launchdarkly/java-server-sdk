package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;

import java.util.concurrent.atomic.AtomicReference;

// Simple delegator to ensure that LDClient.getDataStoreStatusProvider() never returns null and that
// the application isn't given direct access to the store.
final class DataStoreStatusProviderImpl implements DataStoreStatusProvider {
  private final DataStore store;
  private final EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> statusBroadcaster;
  private final AtomicReference<DataStoreStatusProvider.Status> lastStatus;

  DataStoreStatusProviderImpl(
      DataStore store,
      EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> statusBroadcaster
      ) {
    this.store = store;
    this.statusBroadcaster = statusBroadcaster;
    this.lastStatus = new AtomicReference<>(new DataStoreStatusProvider.Status(true, false)); // initially "available"
  }
  
  // package-private
  void updateStatus(DataStoreStatusProvider.Status newStatus) {
    if (newStatus != null) {
      DataStoreStatusProvider.Status oldStatus = lastStatus.getAndSet(newStatus);
      if (!newStatus.equals(oldStatus)) {
        statusBroadcaster.broadcast(newStatus);
      }
    }
  }
  
  @Override
  public Status getStoreStatus() {
    return lastStatus.get();
  }

  @Override
  public void addStatusListener(StatusListener listener) {
    statusBroadcaster.register(listener);
  }

  @Override
  public void removeStatusListener(StatusListener listener) {
    statusBroadcaster.unregister(listener);
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
