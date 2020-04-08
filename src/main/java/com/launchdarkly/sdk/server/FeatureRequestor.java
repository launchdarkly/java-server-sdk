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

interface FeatureRequestor extends Closeable {
  DataModel.FeatureFlag getFlag(String featureKey) throws IOException, HttpErrorException;

  DataModel.Segment getSegment(String segmentKey) throws IOException, HttpErrorException;

  AllData getAllData() throws IOException, HttpErrorException;

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
