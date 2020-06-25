package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;

import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;

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
  AllData getAllData(boolean returnDataEvenIfCached) throws IOException, HttpErrorException;

  static class AllData {
    final Map<String, DataModel.FeatureFlag> flags;
    final Map<String, DataModel.Segment> segments;
    
    AllData(Map<String, DataModel.FeatureFlag> flags, Map<String, DataModel.Segment> segments) {
      this.flags = flags;
      this.segments = segments;
    }

    FullDataSet<ItemDescriptor> toFullDataSet() {
      return new FullDataSet<ItemDescriptor>(ImmutableMap.of(
          FEATURES, toKeyedItems(FEATURES, flags),
          SEGMENTS, toKeyedItems(SEGMENTS, segments)
          ).entrySet());
    }

    static KeyedItems<ItemDescriptor> toKeyedItems(DataKind kind, Map<String, ? extends VersionedData> itemsMap) {
      ImmutableList.Builder<Map.Entry<String, ItemDescriptor>> builder = ImmutableList.builder();
      if (itemsMap != null) {
        for (Map.Entry<String, ? extends VersionedData> e: itemsMap.entrySet()) {
          ItemDescriptor item = new ItemDescriptor(e.getValue().getVersion(), e.getValue());
          builder.add(new AbstractMap.SimpleEntry<>(e.getKey(), item));
        }
      }
      return new KeyedItems<>(builder.build());
    }
  }
}
