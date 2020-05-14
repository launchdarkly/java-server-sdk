package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;

import java.util.function.Supplier;

final class DataSourceStatusProviderImpl implements DataSourceStatusProvider {
  private final EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> dataSourceStatusNotifier;
  private final Supplier<DataSourceStatusProvider.Status> statusSupplier;

  DataSourceStatusProviderImpl(EventBroadcasterImpl<StatusListener, Status> dataSourceStatusNotifier,
      Supplier<Status> statusSupplier) {
    this.dataSourceStatusNotifier = dataSourceStatusNotifier;
    this.statusSupplier = statusSupplier;
  }

  @Override
  public Status getStatus() {
    return statusSupplier.get();
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
