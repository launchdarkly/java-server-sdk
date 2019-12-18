package com.launchdarkly.client;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.annotations.JsonAdapter;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;
import com.launchdarkly.client.value.LDValue;

import java.util.List;

import static com.google.common.collect.Iterables.transform;

/**
 * Defines the full data model for feature flags and user segments, in the format provided by the SDK endpoints of
 * the LaunchDarkly service.
 */
public abstract class DataModel {
  public static abstract class DataKinds {
    /**
     * The {@link VersionedDataKind} instance that describes feature flag data.
     */
    public static VersionedDataKind<DataModel.FeatureFlag> FEATURES = new DataKindImpl<DataModel.FeatureFlag>("features", DataModel.FeatureFlag.class, "/flags/", 1) {
      public DataModel.FeatureFlag makeDeletedItem(String key, int version) {
        return new DataModel.FeatureFlag(key, version, false, null, null, null, null, null, null, null, false, false, false, null, true);
      }
      
      public boolean isDependencyOrdered() {
        return true;
      }
      
      public Iterable<String> getDependencyKeys(VersionedData item) {
        DataModel.FeatureFlag flag = (DataModel.FeatureFlag)item;
        if (flag.getPrerequisites() == null || flag.getPrerequisites().isEmpty()) {
          return ImmutableList.of();
        }
        return transform(flag.getPrerequisites(), new Function<DataModel.Prerequisite, String>() {
          public String apply(DataModel.Prerequisite p) {
            return p.getKey();
          }
        });
      }
    };
    
    /**
     * The {@link VersionedDataKind} instance that describes user segment data.
     */
    public static VersionedDataKind<DataModel.Segment> SEGMENTS = new DataKindImpl<DataModel.Segment>("segments", DataModel.Segment.class, "/segments/", 0) {
      
      public DataModel.Segment makeDeletedItem(String key, int version) {
        return new DataModel.Segment(key, null, null, null, null, version, true);
      }
    };
    
    static abstract class DataKindImpl<T extends VersionedData> extends VersionedDataKind<T> {
      private static final Gson gson = new Gson();
      
      private final String namespace;
      private final Class<T> itemClass;
      private final String streamApiPath;
      private final int priority;
      
      DataKindImpl(String namespace, Class<T> itemClass, String streamApiPath, int priority) {
        this.namespace = namespace;
        this.itemClass = itemClass;
        this.streamApiPath = streamApiPath;
        this.priority = priority;
      }
      
      public String getNamespace() {
        return namespace;
      }
      
      public Class<T> getItemClass() {
        return itemClass;
      }
      
      public String getStreamApiPath() {
        return streamApiPath;
      }
      
      public int getPriority() {
        return priority;
      }

      public T deserialize(String serializedData) {
        return gson.fromJson(serializedData, itemClass);
      }
      
      /**
       * Used internally to match data URLs in the streaming API.
       * @param path path from an API message
       * @return the parsed key if the path refers to an object of this kind, otherwise null 
       */
      
    }
  }

  // All of these inner data model classes should have package-private scope. They should have only property
  // accessors; the evaluator logic is in Evaluator, EvaluatorBucketing, and EvaluatorOperators.

  @JsonAdapter(JsonHelpers.PostProcessingDeserializableTypeAdapterFactory.class)
  static final class FeatureFlag implements VersionedData, JsonHelpers.PostProcessingDeserializable {
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

    // Precompute some invariant values for improved efficiency during evaluations - called from JsonHelpers.PostProcessingDeserializableTypeAdapter
    public void afterDeserialized() {
      if (prerequisites != null) {
        for (Prerequisite p: prerequisites) {
          p.setPrerequisiteFailedReason(EvaluationReason.prerequisiteFailed(p.getKey()));
        }
      }
      if (rules != null) {
        for (int i = 0; i < rules.size(); i++) {
          Rule r = rules.get(i);
          r.setRuleMatchReason(EvaluationReason.ruleMatch(i, r.getId()));
        }
      }
    }
  }

  static final class Prerequisite {
    private String key;
    private int variation;

    private transient EvaluationReason.PrerequisiteFailed prerequisiteFailedReason;

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

    // This value is precomputed when we deserialize a FeatureFlag from JSON
    EvaluationReason.PrerequisiteFailed getPrerequisiteFailedReason() {
      return prerequisiteFailedReason;
    }

    void setPrerequisiteFailedReason(EvaluationReason.PrerequisiteFailed prerequisiteFailedReason) {
      this.prerequisiteFailedReason = prerequisiteFailedReason;
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
    
    private transient EvaluationReason.RuleMatch ruleMatchReason;
  
    Rule() {
      super();
    }
  
    Rule(String id, List<Clause> clauses, Integer variation, Rollout rollout, boolean trackEvents) {
      super(variation, rollout);
      this.id = id;
      this.clauses = clauses;
      this.trackEvents = trackEvents;
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
    
    // This value is precomputed when we deserialize a FeatureFlag from JSON
    EvaluationReason.RuleMatch getRuleMatchReason() {
      return ruleMatchReason;
    }

    void setRuleMatchReason(EvaluationReason.RuleMatch ruleMatchReason) {
      this.ruleMatchReason = ruleMatchReason;
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

  /**
   * This enum can be directly deserialized from JSON, avoiding the need for a mapping of strings to
   * operators. The implementation of each operator is in EvaluatorOperators.
   */
  static enum Operator {
    in,
    endsWith,
    startsWith,
    matches,
    contains,
    lessThan,
    lessThanOrEqual,
    greaterThan,
    greaterThanOrEqual,
    before,
    after,
    semVerEqual,
    semVerLessThan,
    semVerGreaterThan,
    segmentMatch
  }
}
