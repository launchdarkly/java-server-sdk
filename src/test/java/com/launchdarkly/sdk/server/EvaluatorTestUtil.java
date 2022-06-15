package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.server.BigSegmentStoreWrapper.BigSegmentsQueryResult;

import java.util.HashMap;

@SuppressWarnings("javadoc")
public abstract class EvaluatorTestUtil {
  public static Evaluator BASE_EVALUATOR = new EvaluatorBuilder().build();

  public static class EvaluatorBuilder {
    HashMap<String, DataModel.FeatureFlag> flagMap = new HashMap<>();
    HashMap<String, DataModel.Segment> segmentMap = new HashMap<>();
    HashMap<String, BigSegmentsQueryResult> bigSegmentMap = new HashMap<>();
    private final LDLogger logger;

    EvaluatorBuilder() {
      this(LDLogger.withAdapter(Logs.none(), ""));
    }
    
    EvaluatorBuilder(LDLogger logger) {
      this.logger = logger;
    }
    
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
    }, logger);
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
