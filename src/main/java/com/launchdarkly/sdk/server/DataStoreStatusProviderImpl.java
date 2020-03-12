package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;

// Simple delegator to ensure that LDClient.getDataStoreStatusProvider() never returns null and that
// the application isn't given direct access to the store.
final class DataStoreStatusProviderImpl implements DataStoreStatusProvider {
  private final DataStoreStatusProvider delegateTo;

  DataStoreStatusProviderImpl(DataStore store) {
    delegateTo = store instanceof DataStoreStatusProvider ? (DataStoreStatusProvider)store : null;
  }
  
  @Override
  public Status getStoreStatus() {
    return delegateTo == null ? null : delegateTo.getStoreStatus();
  }

  @Override
  public void addStatusListener(StatusListener listener) {
    if (delegateTo != null) {
      delegateTo.addStatusListener(listener);
    }
  }

  @Override
  public void removeStatusListener(StatusListener listener) {
    if (delegateTo != null) {
      delegateTo.removeStatusListener(listener);
    }
  }

  @Override
  public CacheStats getCacheStats() {
    return delegateTo == null ? null : delegateTo.getCacheStats();
  }
}
