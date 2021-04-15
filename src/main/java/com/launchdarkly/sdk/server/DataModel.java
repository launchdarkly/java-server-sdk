package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.JsonAdapter;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * Contains information about the internal data model for feature flags and user segments.
 * <p>
 * The details of the data model are not public to application code (although of course developers can easily
 * look at the code or the data) so that changes to LaunchDarkly SDK implementation details will not be breaking
 * changes to the application. Therefore, most of the members of this class are package-private. The public
 * members provide a high-level description of model objects so that custom integration code or test code can
 * store or serialize them.
 */
public abstract class DataModel {
  private DataModel() {}
  
  /**
   * The {@link DataKind} instance that describes feature flag data.
   * <p>
   * Applications should not need to reference this object directly. It is public so that custom integrations
   * and test code can serialize or deserialize data or inject it into a data store.
   */
  public static DataKind FEATURES = new DataKind("features",
    DataModel::serializeItem,
    s -> deserializeItem(s, FeatureFlag.class));
  
  /**
   * The {@link DataKind} instance that describes user segment data.
   * <p>
   * Applications should not need to reference this object directly. It is public so that custom integrations
   * and test code can serialize or deserialize data or inject it into a data store.
   */
  public static DataKind SEGMENTS = new DataKind("segments",
    DataModel::serializeItem,
    s -> deserializeItem(s, Segment.class));

  /**
   * An enumeration of all supported {@link DataKind} types.
   * <p>
   * Applications should not need to reference this object directly. It is public so that custom data store
   * implementations can determine ahead of time what kinds of model objects may need to be stored, if
   * necessary. 
   */
  public static Iterable<DataKind> ALL_DATA_KINDS = ImmutableList.of(FEATURES, SEGMENTS);
  
  private static ItemDescriptor deserializeItem(String s, Class<? extends VersionedData> itemClass) {
    VersionedData o = JsonHelpers.deserialize(s, itemClass);
    return o.isDeleted() ? ItemDescriptor.deletedItem(o.getVersion()) : new ItemDescriptor(o.getVersion(), o);
  }
  
  private static String serializeItem(ItemDescriptor item) {
    Object o = item.getItem();
    if (o != null) {
      return JsonHelpers.serialize(o);
    }
    return "{\"version\":" + item.getVersion() + ",\"deleted\":true}";
  }
  
  // All of these inner data model classes should have package-private scope. They should have only property
  // accessors; the evaluator logic is in Evaluator, EvaluatorBucketing, and EvaluatorOperators.

  /**
   * Common interface for FeatureFlag and Segment, for convenience in accessing their common properties.
   * @since 3.0.0
   */
  interface VersionedData {
    String getKey();
    int getVersion();
    /**
     * True if this is a placeholder for a deleted item.
     * @return true if deleted
     */
    boolean isDeleted();
  }

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
      return prerequisites == null ? emptyList() : prerequisites;
    }

    String getSalt() {
      return salt;
    }

    // Guaranteed non-null
    List<Target> getTargets() {
      return targets == null ? emptyList() : targets;
    }

    // Guaranteed non-null
    List<Rule> getRules() {
      return rules == null ? emptyList() : rules;
    }

    VariationOrRollout getFallthrough() {
      return fallthrough;
    }

    // Guaranteed non-null
    List<LDValue> getVariations() {
      return variations == null ? emptyList() : variations;
    }

    Integer getOffVariation() {
      return offVariation;
    }

    boolean isClientSide() {
      return clientSide;
    }

    // Precompute some invariant values for improved efficiency during evaluations - called from JsonHelpers.PostProcessingDeserializableTypeAdapter
    public void afterDeserialized() {
      EvaluatorPreprocessing.preprocessFlag(this);
    }
  }

  static final class Prerequisite {
    private String key;
    private int variation;

    private transient EvaluationReason prerequisiteFailedReason;

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
    EvaluationReason getPrerequisiteFailedReason() {
      return prerequisiteFailedReason;
    }

    void setPrerequisiteFailedReason(EvaluationReason prerequisiteFailedReason) {
      this.prerequisiteFailedReason = prerequisiteFailedReason;
    }
  }

  static final class Target {
    private Set<String> values;
    private int variation;
  
    Target() {}
  
    Target(Set<String> values, int variation) {
      this.values = values;
      this.variation = variation;
    }
  
    // Guaranteed non-null
    Collection<String> getValues() {
      return values == null ? emptySet() : values;
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
    
    private transient EvaluationReason ruleMatchReason;
  
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
    
    // Guaranteed non-null
    List<Clause> getClauses() {
      return clauses == null ? emptyList() : clauses;
    }
    
    boolean isTrackEvents() {
      return trackEvents;
    }
    
    // This value is precomputed when we deserialize a FeatureFlag from JSON
    EvaluationReason getRuleMatchReason() {
      return ruleMatchReason;
    }

    void setRuleMatchReason(EvaluationReason ruleMatchReason) {
      this.ruleMatchReason = ruleMatchReason;
    }
  }
  
  static final class Clause {
    private UserAttribute attribute;
    private Operator op;
    private List<LDValue> values; //interpreted as an OR of values
    private boolean negate;
    
    // The following property is marked transient because it is not to be serialized or deserialized;
    // it is (if necessary) precomputed in FeatureFlag.afterDeserialized() to speed up evaluations. 
    transient EvaluatorPreprocessing.ClauseExtra preprocessed;
    
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
    
    // Guaranteed non-null
    List<LDValue> getValues() {
      return values == null ? emptyList() : values;
    }
    
    boolean isNegate() {
      return negate;
    }
    
    EvaluatorPreprocessing.ClauseExtra getPreprocessed() {
      return preprocessed;
    }
    
    void setPreprocessed(EvaluatorPreprocessing.ClauseExtra preprocessed) {
      this.preprocessed = preprocessed;
    }
  }

  static final class Rollout {
    private List<WeightedVariation> variations;
    private UserAttribute bucketBy;
    private RolloutKind kind;
    private Integer seed;
  
    Rollout() {}
  
    Rollout(List<WeightedVariation> variations, UserAttribute bucketBy, RolloutKind kind) {
      this.variations = variations;
      this.bucketBy = bucketBy;
      this.kind = kind;
      this.seed = null;
    }
    
    Rollout(List<WeightedVariation> variations, UserAttribute bucketBy, RolloutKind kind, Integer seed) {
      this.variations = variations;
      this.bucketBy = bucketBy;
      this.kind = kind;
      this.seed = seed;
    }
    
    // Guaranteed non-null
    List<WeightedVariation> getVariations() {
      return variations == null ? emptyList() : variations;
    }
    
    UserAttribute getBucketBy() {
      return bucketBy;
    }

    RolloutKind getKind() {
      return this.kind;
    }

    Integer getSeed() {
      return this.seed;
    }

    boolean isExperiment() {
      return kind == RolloutKind.experiment;
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
    private boolean untracked;
  
    WeightedVariation() {}
  
    WeightedVariation(int variation, int weight) {
      this.variation = variation;
      this.weight = weight;
      this.untracked = false;
    }
    
    WeightedVariation(int variation, int weight, boolean untracked) {
      this.variation = variation;
      this.weight = weight;
      this.untracked = untracked;
    }
    
    int getVariation() {
      return variation;
    }
    
    int getWeight() {
      return weight;
    }

    boolean isTracked() {
      return !untracked;
    }
  }
  
  @JsonAdapter(JsonHelpers.PostProcessingDeserializableTypeAdapterFactory.class)
  static final class Segment implements VersionedData, JsonHelpers.PostProcessingDeserializable {
    private String key;
    private Set<String> included;
    private Set<String> excluded;
    private String salt;
    private List<SegmentRule> rules;
    private int version;
    private boolean deleted;

    Segment() {}

    Segment(String key, Set<String> included, Set<String> excluded, String salt, List<SegmentRule> rules, int version, boolean deleted) {
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
    
    // Guaranteed non-null
    Collection<String> getIncluded() {
      return included == null ? emptySet() : included;
    }
    
    // Guaranteed non-null
    Collection<String> getExcluded() {
      return excluded == null ? emptySet() : excluded;
    }
    
    String getSalt() {
      return salt;
    }
    
    // Guaranteed non-null
    List<SegmentRule> getRules() {
      return rules == null ? emptyList() : rules;
    }
    
    public int getVersion() {
      return version;
    }
    
    public boolean isDeleted() {
      return deleted;
    }

    // Precompute some invariant values for improved efficiency during evaluations - called from JsonHelpers.PostProcessingDeserializableTypeAdapter
    public void afterDeserialized() {
      EvaluatorPreprocessing.preprocessSegment(this);
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
    
    // Guaranteed non-null
    List<Clause> getClauses() {
      return clauses == null ? emptyList() : clauses;
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

  static enum RolloutKind {
    rollout,
    experiment
  }
}
