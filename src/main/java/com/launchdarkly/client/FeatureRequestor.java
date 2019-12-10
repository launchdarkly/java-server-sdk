package com.launchdarkly.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

interface FeatureRequestor extends Closeable {
  FlagModel.FeatureFlag getFlag(String featureKey) throws IOException, HttpErrorException;

  FlagModel.Segment getSegment(String segmentKey) throws IOException, HttpErrorException;

  AllData getAllData() throws IOException, HttpErrorException;

  static class AllData {
    final Map<String, FlagModel.FeatureFlag> flags;
    final Map<String, FlagModel.Segment> segments;
    
    AllData(Map<String, FlagModel.FeatureFlag> flags, Map<String, FlagModel.Segment> segments) {
      this.flags = flags;
      this.segments = segments;
    }
  }  
}
