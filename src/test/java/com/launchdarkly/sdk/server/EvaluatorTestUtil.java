package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.BigSegmentStoreWrapper.BigSegmentsQueryResult;

import java.util.HashMap;

@SuppressWarnings("javadoc")
public abstract class EvaluatorTestUtil {
  public static Evaluator BASE_EVALUATOR = evaluatorBuilder().build();

  public static EvaluatorBuilder evaluatorBuilder() {
    return new EvaluatorBuilder();
  }
  
  public static class EvaluatorBuilder {
    HashMap<String, DataModel.FeatureFlag> flagMap = new HashMap<>();
    HashMap<String, DataModel.Segment> segmentMap = new HashMap<>();
    HashMap<String, BigSegmentsQueryResult> bigSegmentMap = new HashMap<>();

    public Evaluator build() {
      return new Evaluator(new Evaluator.Getters() {
        public DataModel.FeatureFlag getFlag(String key) {
          if (!flagMap.containsKey(key)) {
            throw new IllegalStateException("Evaluator unexpectedly tried to query flag: " + key);
          }
          return flagMap.get(key);
        }

        public DataModel.Segment getSegment(String key) {
          if (!segmentMap.containsKey(key)) {
            throw new IllegalStateException("Evaluator unexpectedly tried to query segment: " + key);
          }
          return segmentMap.get(key);
        }

        public BigSegmentsQueryResult getBigSegments(String key) {
          if (!bigSegmentMap.containsKey(key)) {
            throw new IllegalStateException("Evaluator unexpectedly tried to query Big Segment: " + key);
          }
          return bigSegmentMap.get(key);
        }
      });
    }
    
    public EvaluatorBuilder withStoredFlags(final DataModel.FeatureFlag... flags) {
      for (DataModel.FeatureFlag f: flags) {
        flagMap.put(f.getKey(), f);
      }
      return this;
    }
    
    public EvaluatorBuilder withNonexistentFlag(final String nonexistentFlagKey) {
      flagMap.put(nonexistentFlagKey, null);
      return this;
    }
    
    public EvaluatorBuilder withStoredSegments(final DataModel.Segment... segments) {
      for (DataModel.Segment s: segments) {
        segmentMap.put(s.getKey(), s);
      }
      return this;
    }
    
    public EvaluatorBuilder withNonexistentSegment(final String nonexistentSegmentKey) {
      segmentMap.put(nonexistentSegmentKey, null);
      return this;
    }

    public EvaluatorBuilder withBigSegmentQueryResult(final String userKey, BigSegmentsQueryResult queryResult) {
      bigSegmentMap.put(userKey, queryResult);
      return this;
    }
  }
}
