package com.launchdarkly.sdk.server;

import com.google.gson.annotations.JsonAdapter;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.VersionedData;
import com.launchdarkly.sdk.server.interfaces.VersionedDataKind;

import java.util.List;

/**
 * Defines the full data model for feature flags and user segments, in the format provided by the SDK endpoints of
 * the LaunchDarkly service.
 * 
 * The details of the data model are not public to application code (although of course developers can easily
 * look at the code or the data) so that changes to LaunchDarkly SDK implementation details will not be breaking
 * changes to the application.
 */
public abstract class DataModel {
  /**
   * Contains standard instances of {@link VersionedDataKind} representing the main data model types.
   */
  public static abstract class DataKinds {
    /**
     * The {@link DataKind} instance that describes feature flag data.
     */
    public static DataKind FEATURES = new DataKind("features",
      DataKinds::serializeItem,
      s -> deserializeItem(s, FeatureFlag.class));
        
    /**
     * The {@link DataKind} instance that describes user segment data.
     */
    public static DataKind SEGMENTS = new DataKind("segments",
      DataKinds::serializeItem,
      s -> deserializeItem(s, Segment.class));
    
    private static String serializeItem(ItemDescriptor item) {
      Object o = item.getItem();
      if (o != null) {
        return JsonHelpers.gsonInstance().toJson(o);
      }
      return "{\"version\":" + item.getVersion() + ",\"deleted\":true}";
    }
    
    private static ItemDescriptor deserializeItem(String s, Class<? extends VersionedData> itemClass) {
      VersionedData o = JsonHelpers.gsonInstance().fromJson(s, itemClass);
      return o.isDeleted() ? ItemDescriptor.deletedItem(o.getVersion()) : new ItemDescriptor(o.getVersion(), o);
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
    private UserAttribute attribute;
    private Operator op;
    private List<LDValue> values; //interpreted as an OR of values
    private boolean negate;
  
    Clause() {
    }
    
    Clause(UserAttribute attribute, Operator op, List<LDValue> values, boolean negate) {
      this.attribute = attribute;
      this.op = op;
      this.values = values;
      this.negate = negate;
    }
  
    UserAttribute getAttribute() {
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
    private UserAttribute bucketBy;
  
    Rollout() {}
  
    Rollout(List<WeightedVariation> variations, UserAttribute bucketBy) {
      this.variations = variations;
      this.bucketBy = bucketBy;
    }
    
    List<WeightedVariation> getVariations() {
      return variations;
    }
    
    UserAttribute getBucketBy() {
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
    private final UserAttribute bucketBy;
    
    SegmentRule(List<Clause> clauses, Integer weight, UserAttribute bucketBy) {
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
    
    UserAttribute getBucketBy() {
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
