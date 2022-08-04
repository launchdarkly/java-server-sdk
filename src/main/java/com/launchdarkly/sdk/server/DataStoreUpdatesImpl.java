package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.subsystems.DataStoreUpdateSink;

import java.util.concurrent.atomic.AtomicReference;

class DataStoreUpdatesImpl implements DataStoreUpdateSink {
  // package-private because it's convenient to use these from DataStoreStatusProviderImpl
  final EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> statusBroadcaster;
  final AtomicReference<DataStoreStatusProvider.Status> lastStatus;

  DataStoreUpdatesImpl(
      EventBroadcasterImpl<DataStoreStatusProvider.StatusListener, DataStoreStatusProvider.Status> statusBroadcaster
      ) {
    this.statusBroadcaster = statusBroadcaster;
    this.lastStatus = new AtomicReference<>(new DataStoreStatusProvider.Status(true, false)); // initially "available"
  }

  @Override
  public void updateStatus(DataStoreStatusProvider.Status newStatus) {
    if (newStatus != null) {
      DataStoreStatusProvider.Status oldStatus = lastStatus.getAndSet(newStatus);
      if (!newStatus.equals(oldStatus)) {
        statusBroadcaster.broadcast(newStatus);
      }
    }
  }
}
