package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import java.util.List;

/**
 * Defines the full data model for feature flags and user segments, in the format provided by the SDK endpoints of
 * the LaunchDarkly service. All sub-objects contained within flags and segments are also defined here as inner
 * classes.
 * 
 * These classes should all have package-private scope. They should not provide any logic other than standard
 * property getters; the evaluation logic is in Evaluator.
 */
abstract class FlagModel {
  static final class FeatureFlag implements VersionedData {
    private String key;
    private int version;
    private boolean on;
    private List<Prerequisite> prerequisites;
    private String salt;
    private List<Target> targets;
    private List<Rule> rules;
    private VariationOrRollout fallthrough;
    private Integer offVariation; //optional
    private List<LDValue> variations;
    private boolean clientSide;
    private boolean trackEvents;
    private boolean trackEventsFallthrough;
    private Long debugEventsUntilDate;
    private boolean deleted;

    // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
    FeatureFlag() {}

    FeatureFlag(String key, int version, boolean on, List<Prerequisite> prerequisites, String salt, List<Target> targets,
        List<Rule> rules, VariationOrRollout fallthrough, Integer offVariation, List<LDValue> variations,
        boolean clientSide, boolean trackEvents, boolean trackEventsFallthrough,
        Long debugEventsUntilDate, boolean deleted) {
      this.key = key;
      this.version = version;
      this.on = on;
      this.prerequisites = prerequisites;
      this.salt = salt;
      this.targets = targets;
      this.rules = rules;
      this.fallthrough = fallthrough;
      this.offVariation = offVariation;
      this.variations = variations;
      this.clientSide = clientSide;
      this.trackEvents = trackEvents;
      this.trackEventsFallthrough = trackEventsFallthrough;
      this.debugEventsUntilDate = debugEventsUntilDate;
      this.deleted = deleted;
    }

    public int getVersion() {
      return version;
    }

    public String getKey() {
      return key;
    }

    boolean isTrackEvents() {
      return trackEvents;
    }
    
    boolean isTrackEventsFallthrough() {
      return trackEventsFallthrough;
    }
    
    Long getDebugEventsUntilDate() {
      return debugEventsUntilDate;
    }
    
    public boolean isDeleted() {
      return deleted;
    }

    boolean isOn() {
      return on;
    }

    List<Prerequisite> getPrerequisites() {
      return prerequisites;
    }

    String getSalt() {
      return salt;
    }

    List<Target> getTargets() {
      return targets;
    }

    List<Rule> getRules() {
      return rules;
    }

    VariationOrRollout getFallthrough() {
      return fallthrough;
    }

    List<LDValue> getVariations() {
      return variations;
    }

    Integer getOffVariation() {
      return offVariation;
    }

    boolean isClientSide() {
      return clientSide;
    }
  }

  static final class Prerequisite {
    private String key;
    private int variation;
  
    Prerequisite() {}
  
    Prerequisite(String key, int variation) {
      this.key = key;
      this.variation = variation;
    }
  
    String getKey() {
      return key;
    }
  
    int getVariation() {
      return variation;
    }
  }

  static final class Target {
    private List<String> values;
    private int variation;
  
    Target() {}
  
    Target(List<String> values, int variation) {
      this.values = values;
      this.variation = variation;
    }
  
    List<String> getValues() {
      return values;
    }
  
    int getVariation() {
      return variation;
    }
  }

  /**
   * Expresses a set of AND-ed matching conditions for a user, along with either the fixed variation or percent rollout
   * to serve if the conditions match.
   * Invariant: one of the variation or rollout must be non-nil.
   */
  static final class Rule extends VariationOrRollout {
    private String id;
    private List<Clause> clauses;
    private boolean trackEvents;
  
    Rule() {
      super();
    }
  
    Rule(String id, List<Clause> clauses, Integer variation, Rollout rollout, boolean trackEvents) {
      super(variation, rollout);
      this.id = id;
      this.clauses = clauses;
      this.trackEvents = trackEvents;
    }
    
    Rule(String id, List<Clause> clauses, Integer variation, Rollout rollout) {
      this(id, clauses, variation, rollout, false);
    }
  
    String getId() {
      return id;
    }
    
    Iterable<Clause> getClauses() {
      return clauses;
    }
    
    boolean isTrackEvents() {
      return trackEvents;
    }
  }
  
  static class Clause {
    private String attribute;
    private Operator op;
    private List<LDValue> values; //interpreted as an OR of values
    private boolean negate;
  
    Clause() {
    }
    
    Clause(String attribute, Operator op, List<LDValue> values, boolean negate) {
      this.attribute = attribute;
      this.op = op;
      this.values = values;
      this.negate = negate;
    }
  
    String getAttribute() {
      return attribute;
    }
    
    Operator getOp() {
      return op;
    }
    
    List<LDValue> getValues() {
      return values;
    }
    
    boolean isNegate() {
      return negate;
    }
  }

  static final class Rollout {
    private List<WeightedVariation> variations;
    private String bucketBy;
  
    Rollout() {}
  
    Rollout(List<WeightedVariation> variations, String bucketBy) {
      this.variations = variations;
      this.bucketBy = bucketBy;
    }
    
    List<WeightedVariation> getVariations() {
      return variations;
    }
    
    String getBucketBy() {
      return bucketBy;
    }
  }

  /**
   * Contains either a fixed variation or percent rollout to serve.
   * Invariant: one of the variation or rollout must be non-nil.
   */
  static class VariationOrRollout {
    private Integer variation;
    private Rollout rollout;
  
    VariationOrRollout() {}
  
    VariationOrRollout(Integer variation, Rollout rollout) {
      this.variation = variation;
      this.rollout = rollout;
    }
  
    Integer getVariation() {
      return variation;
    }
    
    Rollout getRollout() {
      return rollout;
    }
  }

  static final class WeightedVariation {
    private int variation;
    private int weight;
  
    WeightedVariation() {}
  
    WeightedVariation(int variation, int weight) {
      this.variation = variation;
      this.weight = weight;
    }
    
    int getVariation() {
      return variation;
    }
    
    int getWeight() {
      return weight;
    }
  }
  
  static final class Segment implements VersionedData {
    private String key;
    private List<String> included;
    private List<String> excluded;
    private String salt;
    private List<SegmentRule> rules;
    private int version;
    private boolean deleted;

    Segment() {}

    Segment(String key, List<String> included, List<String> excluded, String salt, List<SegmentRule> rules, int version, boolean deleted) {
      this.key = key;
      this.included = included;
      this.excluded = excluded;
      this.salt = salt;
      this.rules = rules;
      this.version = version;
      this.deleted = deleted;
    }

    public String getKey() {
      return key;
    }
    
    Iterable<String> getIncluded() {
      return included;
    }
    
    Iterable<String> getExcluded() {
      return excluded;
    }
    
    String getSalt() {
      return salt;
    }
    
    Iterable<SegmentRule> getRules() {
      return rules;
    }
    
    public int getVersion() {
      return version;
    }
    
    public boolean isDeleted() {
      return deleted;
    }
  }
  
  static final class SegmentRule {
    private final List<Clause> clauses;
    private final Integer weight;
    private final String bucketBy;
    
    SegmentRule(List<Clause> clauses, Integer weight, String bucketBy) {
      this.clauses = clauses;
      this.weight = weight;
      this.bucketBy = bucketBy;
    }
    
    List<Clause> getClauses() {
      return clauses;
    }
    
    Integer getWeight() {
      return weight;
    }
    
    String getBucketBy() {
      return bucketBy;
    }
  }
}
