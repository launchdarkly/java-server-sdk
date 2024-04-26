package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.BigSegmentStoreWrapper.BigSegmentsQueryResult;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.ModelBuilders.FlagBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;

@SuppressWarnings("javadoc")
public abstract class EvaluatorTestUtil {
  public static final LDContext BASE_USER = LDContext.create("x");

  // These constants and flag builders define two kinds of flag: one with three variations-- allowing us to
  // distinguish between match, fallthrough, and off results--  and one with two.
  public static final int OFF_VARIATION = 0;
  public static final LDValue OFF_VALUE = LDValue.of("off");
  public static final int FALLTHROUGH_VARIATION = 1;
  public static final LDValue FALLTHROUGH_VALUE = LDValue.of("fall");
  public static final int MATCH_VARIATION = 2;
  public static final LDValue MATCH_VALUE = LDValue.of("match");
  public static final LDValue[] THREE_VARIATIONS = new LDValue[] { OFF_VALUE, FALLTHROUGH_VALUE, MATCH_VALUE };

  public static final int RED_VARIATION = 0;
  public static final LDValue RED_VALUE = LDValue.of("red");
  public static final int GREEN_VARIATION = 1;
  public static final LDValue GREEN_VALUE = LDValue.of("green");
  public static final LDValue[] RED_GREEN_VARIATIONS = new LDValue[] { RED_VALUE, GREEN_VALUE };
  
  public static FlagBuilder buildThreeWayFlag(String flagKey) {
    return flagBuilder(flagKey)
        .fallthroughVariation(FALLTHROUGH_VARIATION)
        .offVariation(OFF_VARIATION)
        .variations(THREE_VARIATIONS)
        .version(versionFromKey(flagKey));
  }
  
  public static FlagBuilder buildRedGreenFlag(String flagKey) {
    return flagBuilder(flagKey)
        .fallthroughVariation(GREEN_VARIATION)
        .offVariation(RED_VARIATION)
        .variations(RED_GREEN_VARIATIONS)
        .version(versionFromKey(flagKey));
  }

  public static int versionFromKey(String flagKey) {
    return Math.abs(flagKey.hashCode());
  }
  
  public static EvaluatorBuilder evaluatorBuilder() {
    return new EvaluatorBuilder();
  }
  
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

  public static EvaluationRecorder expectNoPrerequisiteEvals() {
    return new EvaluationRecorder() {
      @Override
      public void recordPrerequisiteEvaluation(FeatureFlag flag, FeatureFlag prereqOfFlag, LDContext context, EvalResult result) {
        throw new AssertionError("did not expect any prerequisite evaluations, but got one");
      }
    };
  }
  
  public static final class PrereqEval {
    public final FeatureFlag flag;
    public final FeatureFlag prereqOfFlag;
    public final LDContext context;
    public final EvalResult result;
    
    public PrereqEval(FeatureFlag flag, FeatureFlag prereqOfFlag, LDContext context, EvalResult result) {
      this.flag = flag;
      this.prereqOfFlag = prereqOfFlag;
      this.context = context;
      this.result = result;
    }
  }
  
  public static final class PrereqRecorder implements EvaluationRecorder {
    public final List<PrereqEval> evals = new ArrayList<>();

    @Override
    public void recordPrerequisiteEvaluation(FeatureFlag flag, FeatureFlag prereqOfFlag, LDContext context,
        EvalResult result) {
      evals.add(new PrereqEval(flag, prereqOfFlag, context, result));
    }
  }
}
