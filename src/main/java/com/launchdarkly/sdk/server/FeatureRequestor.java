package com.launchdarkly.sdk.server;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

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
  }
}
