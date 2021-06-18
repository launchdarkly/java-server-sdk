package com.launchdarkly.sdk.server;

@SuppressWarnings("javadoc")
public abstract class EvaluatorTestUtil {
  public static Evaluator BASE_EVALUATOR = evaluatorBuilder().build();

  public static EvaluatorBuilder evaluatorBuilder() {
    return new EvaluatorBuilder();
  }
  
  public static class EvaluatorBuilder {
    private Evaluator.Getters getters;
    
    EvaluatorBuilder() {
      getters = new Evaluator.Getters() {
        public DataModel.FeatureFlag getFlag(String key) {
          throw new IllegalStateException("Evaluator unexpectedly tried to query flag: " + key);
        }

        public DataModel.Segment getSegment(String key) {
          throw new IllegalStateException("Evaluator unexpectedly tried to query segment: " + key);
        }
      };
    }
    
    public Evaluator build() {
      return new Evaluator(getters);
    }
    
    public EvaluatorBuilder withStoredFlags(final DataModel.FeatureFlag... flags) {
      final Evaluator.Getters baseGetters = getters;
      getters = new Evaluator.Getters() {
        public DataModel.FeatureFlag getFlag(String key) {
          for (DataModel.FeatureFlag f: flags) {
            if (f.getKey().equals(key)) {
              return f;
            }
          }
          return baseGetters.getFlag(key);
        }
        
        public DataModel.Segment getSegment(String key) {
          return baseGetters.getSegment(key);
        }
      };
      return this;
    }
    
    public EvaluatorBuilder withNonexistentFlag(final String nonexistentFlagKey) {
      final Evaluator.Getters baseGetters = getters;
      getters = new Evaluator.Getters() {
        public DataModel.FeatureFlag getFlag(String key) {
          if (key.equals(nonexistentFlagKey)) {
            return null;
          }
          return baseGetters.getFlag(key);
        }
        
        public DataModel.Segment getSegment(String key) {
          return baseGetters.getSegment(key);
        }
      };
      return this;
    }
    
    public EvaluatorBuilder withStoredSegments(final DataModel.Segment... segments) {
      final Evaluator.Getters baseGetters = getters;
      getters = new Evaluator.Getters() {
        public DataModel.FeatureFlag getFlag(String key) {
          return baseGetters.getFlag(key);
        }
        
        public DataModel.Segment getSegment(String key) {
          for (DataModel.Segment s: segments) {
            if (s.getKey().equals(key)) {
              return s;
            }
          }
          return baseGetters.getSegment(key);
        }
      };
      return this;
    }
    
    public EvaluatorBuilder withNonexistentSegment(final String nonexistentSegmentKey) {
      final Evaluator.Getters baseGetters = getters;
      getters = new Evaluator.Getters() {
        public DataModel.FeatureFlag getFlag(String key) {
          return baseGetters.getFlag(key);
        }
        
        public DataModel.Segment getSegment(String key) {
          if (key.equals(nonexistentSegmentKey)) {
            return null;
          }
          return baseGetters.getSegment(key);
        }
      };
      return this;
    }
  }
}
