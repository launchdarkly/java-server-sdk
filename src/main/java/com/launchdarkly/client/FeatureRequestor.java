package com.launchdarkly.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

interface FeatureRequestor extends Closeable {
  FeatureFlag getFlag(String featureKey) throws IOException, HttpErrorException;

  Segment getSegment(String segmentKey) throws IOException, HttpErrorException;

  AllData getAllData() throws IOException, HttpErrorException;

  static class AllData {
    final Map<String, FeatureFlag> flags;
    final Map<String, Segment> segments;
    
    AllData(Map<String, FeatureFlag> flags, Map<String, Segment> segments) {
      this.flags = flags;
      this.segments = segments;
    }
  }
}
