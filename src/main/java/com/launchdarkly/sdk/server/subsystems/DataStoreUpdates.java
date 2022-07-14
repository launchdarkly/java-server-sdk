package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;

/**
 * Interface that a data store implementation can use to report information back to the SDK.
 * <p>
 * The {@link DataStoreFactory} receives an implementation of this interface and can pass it to the
 * data store that it creates, if desired.
 * 
 * @since 5.0.0
 */
public interface DataStoreUpdates {
  /**
   * Reports a change in the data store's operational status.
   * <p>
   * This is what makes the status monitoring mechanisms in {@link DataStoreStatusProvider} work.
   * 
   * @param newStatus the updated status properties
   */
  void updateStatus(DataStoreStatusProvider.Status newStatus);
}
