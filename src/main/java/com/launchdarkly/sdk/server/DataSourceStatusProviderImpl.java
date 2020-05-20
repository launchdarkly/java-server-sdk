package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;

import java.time.Duration;

final class DataSourceStatusProviderImpl implements DataSourceStatusProvider {
  private final EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> dataSourceStatusNotifier;
  private final DataSourceUpdatesImpl dataSourceUpdates;

  DataSourceStatusProviderImpl(
      EventBroadcasterImpl<StatusListener, Status> dataSourceStatusNotifier,
      DataSourceUpdatesImpl dataSourceUpdates
      ) {
    this.dataSourceStatusNotifier = dataSourceStatusNotifier;
    this.dataSourceUpdates = dataSourceUpdates;
  }

  @Override
  public Status getStatus() {
    return dataSourceUpdates.getLastStatus();
  }

  @Override
  public boolean waitFor(State desiredState, Duration timeout) throws InterruptedException {
    return dataSourceUpdates.waitFor(desiredState, timeout);
  }
  
  @Override
  public void addStatusListener(StatusListener listener) {
    dataSourceStatusNotifier.register(listener);
  }

  @Override
  public void removeStatusListener(StatusListener listener) {
    dataSourceStatusNotifier.unregister(listener);
  }
}
