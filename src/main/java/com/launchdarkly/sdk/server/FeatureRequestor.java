package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.internal.http.HttpErrors.HttpErrorException;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;

import java.io.Closeable;
import java.io.IOException;

/**
 * Internal abstraction for polling requests. Currently this is only used by PollingProcessor, and
 * the only implementation is DefaultFeatureRequestor, but using an interface allows us to mock out
 * the HTTP behavior and test the rest of PollingProcessor separately.
 */
interface FeatureRequestor extends Closeable {
  /**
   * Makes a request to the LaunchDarkly server-side SDK polling endpoint,
   * 
   * @param returnDataEvenIfCached true if the method should return non-nil data no matter what;
   *   false if it should return {@code null} when the latest data is already in the cache
   * @return the data, or {@code null} as above
   * @throws IOException for network errors
   * @throws HttpErrorException for HTTP error responses
   */
  FullDataSet<ItemDescriptor> getAllData(boolean returnDataEvenIfCached) throws IOException, HttpErrorException;
}
