package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;

final class BigSegmentStoreStatusProviderImpl implements BigSegmentStoreStatusProvider {
  private final EventBroadcasterImpl<StatusListener, Status> statusNotifier;
  private final BigSegmentStoreWrapper storeWrapper;

  BigSegmentStoreStatusProviderImpl(
      EventBroadcasterImpl<StatusListener, Status> bigSegmentStatusNotifier,
      BigSegmentStoreWrapper storeWrapper) {
    this.storeWrapper = storeWrapper;
    this.statusNotifier = bigSegmentStatusNotifier;
  }

  @Override
  public Status getStatus() {
    return storeWrapper == null ? new Status(false, false) : storeWrapper.getStatus();
  }

  @Override
  public void addStatusListener(StatusListener listener) {
    statusNotifier.register(listener);
  }

  @Override
  public void removeStatusListener(StatusListener listener) {
    statusNotifier.unregister(listener);
  }
}
