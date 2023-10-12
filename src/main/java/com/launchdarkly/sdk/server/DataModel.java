package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.JsonAdapter;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModelPreprocessing.ClausePreprocessed;
import com.launchdarkly.sdk.server.DataModelPreprocessing.FlagPreprocessed;
import com.launchdarkly.sdk.server.DataModelPreprocessing.FlagRulePreprocessed;
import com.launchdarkly.sdk.server.DataModelPreprocessing.PrerequisitePreprocessed;
import com.launchdarkly.sdk.server.DataModelPreprocessing.TargetPreprocessed;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

// IMPLEMENTATION NOTES:
//
// - FeatureFlag, Segment, and all other data model classes contained within them, must be package-private.
// We don't want application code to see these types, because we need to be free to change their details without
// breaking the application.
//
// - We expose our DataKind instances publicly because application code may need to reference them if it is
// implementing a custom component such as a data store. But beyond the mere fact of there being these kinds of
// data, applications should not be considered with their structure.
//
// - For classes that can be deserialized from JSON, if we are relying on Gson's reflective behavior (i.e. if
// the class does not have a custom TypeAdapter), there must be an empty constructor, and the fields cannot
// be final. This is because of how Gson works: it creates an instance first, then sets the fields; that also
// means we cannot do any transformation/validation of the fields in the constructor. But if we have a custom
// deserializer, then we should use final fields.
//
// - In any case, there should be a constructor that takes all the fields; we should use that whenever we need
// to create these objects programmatically (so that if we are able at some point to make the fields final, that
// won't break anything).
//
// - For properties that have a collection type such as List, we should ensure that a null is always changed to
// an empty list (in the constructor, if the field can be made final; otherwise in the getter). Semantically
// there is no difference in the data model between an empty list and a null list, and in some languages
// (particularly Go) it is easy for an uninitialized list to be serialized to JSON as null.
//
// - Some classes have a "preprocessed" field containing types defined in DataModelPreprocessing. These fields
// must always be marked transient, so Gson will not serialize them. They are populated when we deserialize a
// FeatureFlag or Segment, because those types implement JsonHelpers.PostProcessingDeserializable (the
// afterDeserialized() method).

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
    private List<Target> contextTargets;
    private List<Rule> rules;
    private VariationOrRollout fallthrough;
    private Integer offVariation; //optional
    private List<LDValue> variations;
    private boolean clientSide;
    private boolean trackEvents;
    private boolean trackEventsFallthrough;
    private Long debugEventsUntilDate;
    private boolean deleted;
    private Long samplingRatio;
    private Migration migration;
    private boolean excludeFromSummaries;

    /**
     * Container for migration specific flag data.
     */
    static class Migration {
      Migration() {}

      Migration(Long checkRatio) {
        this.checkRatio = checkRatio;
      }
      private Long checkRatio;

      public Long getCheckRatio() {
        return checkRatio;
      }
    }

    transient FlagPreprocessed preprocessed;
    
    // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
    FeatureFlag() {}

    FeatureFlag(String key, int version, boolean on, List<Prerequisite> prerequisites, String salt, List<Target> targets,
        List<Target> contextTargets, List<Rule> rules, VariationOrRollout fallthrough, Integer offVariation,
        List<LDValue> variations, boolean clientSide, boolean trackEvents, boolean trackEventsFallthrough,
        Long debugEventsUntilDate, boolean deleted, Long samplingRatio, Migration migration, boolean excludeFromSummaries) {
      this.key = key;
      this.version = version;
      this.on = on;
      this.prerequisites = prerequisites;
      this.salt = salt;
      this.targets = targets;
      this.contextTargets = contextTargets;
      this.rules = rules;
      this.fallthrough = fallthrough;
      this.offVariation = offVariation;
      this.variations = variations;
      this.clientSide = clientSide;
      this.trackEvents = trackEvents;
      this.trackEventsFallthrough = trackEventsFallthrough;
      this.debugEventsUntilDate = debugEventsUntilDate;
      this.deleted = deleted;
      this.samplingRatio = samplingRatio;
      this.migration = migration;
      this.excludeFromSummaries = excludeFromSummaries;
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
    List<Target> getContextTargets() {
      return contextTargets == null ? emptyList() : contextTargets;
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

    Long getSamplingRatio() { return samplingRatio; }

    Migration getMigration() { return migration; }

    boolean isExcludeFromSummaries() {
      return excludeFromSummaries;
    }

    public void afterDeserialized() {
      DataModelPreprocessing.preprocessFlag(this);
    }
  }

  static final class Prerequisite {
    private String key;
    private int variation;

    transient PrerequisitePreprocessed preprocessed;

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
    private ContextKind contextKind;
    private Set<String> values;
    private int variation;
  
    transient TargetPreprocessed preprocessed;
    
    Target() {}
  
    Target(ContextKind contextKind, Set<String> values, int variation) {
      this.contextKind = contextKind;
      this.values = values;
      this.variation = variation;
    }
  
    ContextKind getContextKind() {
      return contextKind;
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
    
    transient FlagRulePreprocessed preprocessed;
  
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
  }
  
  @JsonAdapter(DataModelSerialization.ClauseTypeAdapter.class)
  static final class Clause {
    private final ContextKind contextKind;
    private final AttributeRef attribute;
    private final Operator op;
    private final List<LDValue> values; //interpreted as an OR of values
    private final boolean negate;
    
    transient ClausePreprocessed preprocessed;
    
    Clause(ContextKind contextKind, AttributeRef attribute, Operator op, List<LDValue> values, boolean negate) {
      this.contextKind = contextKind;
      this.attribute = attribute;
      this.op = op;
      this.values = values == null ? emptyList() : values;
      this.negate = negate;
    }
  
    ContextKind getContextKind() {
      return contextKind;
    }
    
    AttributeRef getAttribute() {
      return attribute;
    }
    
    Operator getOp() {
      return op;
    }
    
    // Guaranteed non-null
    List<LDValue> getValues() {
      return values;
    }
    
    boolean isNegate() {
      return negate;
    }
  }

  @JsonAdapter(DataModelSerialization.RolloutTypeAdapter.class)
  static final class Rollout {
    private final ContextKind contextKind;
    private final List<WeightedVariation> variations;
    private final AttributeRef bucketBy;
    private final RolloutKind kind;
    private final Integer seed;
  
    Rollout(ContextKind contextKind, List<WeightedVariation> variations, AttributeRef bucketBy, RolloutKind kind, Integer seed) {
      this.contextKind = contextKind;
      this.variations = variations == null ? emptyList() : variations;
      this.bucketBy = bucketBy;
      this.kind = kind;
      this.seed = seed;
    }
    
    ContextKind getContextKind() {
      return contextKind;
    }
    
    // Guaranteed non-null
    List<WeightedVariation> getVariations() {
      return variations;
    }
    
    AttributeRef getBucketBy() {
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

    boolean isUntracked() {
      return untracked;
    }
  }
  
  @JsonAdapter(JsonHelpers.PostProcessingDeserializableTypeAdapterFactory.class)
  static final class Segment implements VersionedData, JsonHelpers.PostProcessingDeserializable {
    private String key;
    private Set<String> included;
    private Set<String> excluded;
    private List<SegmentTarget> includedContexts;
    private List<SegmentTarget> excludedContexts;
    private String salt;
    private List<SegmentRule> rules;
    private int version;
    private boolean deleted;
    private boolean unbounded;
    private ContextKind unboundedContextKind;
    private Integer generation;

    Segment() {}

    Segment(String key,
            Set<String> included,
            Set<String> excluded,
            List<SegmentTarget> includedContexts,
            List<SegmentTarget> excludedContexts,
            String salt,
            List<SegmentRule> rules,
            int version,
            boolean deleted,
            boolean unbounded,
            ContextKind unboundedContextKind,
            Integer generation) {
      this.key = key;
      this.included = included;
      this.excluded = excluded;
      this.includedContexts = includedContexts;
      this.excludedContexts = excludedContexts;
      this.salt = salt;
      this.rules = rules;
      this.version = version;
      this.deleted = deleted;
      this.unbounded = unbounded;
      this.unboundedContextKind = unboundedContextKind;
      this.generation = generation;
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
    
    // Guaranteed non-null
    List<SegmentTarget> getIncludedContexts() {
      return includedContexts == null ? emptyList() : includedContexts;
    }

    // Guaranteed non-null
    List<SegmentTarget> getExcludedContexts() {
      return excludedContexts == null ? emptyList() : excludedContexts;
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

    public boolean isUnbounded() {
      return unbounded;
    }

    public ContextKind getUnboundedContextKind() {
      return unboundedContextKind;
    }
    
    public Integer getGeneration() {
      return generation;
    }

    public void afterDeserialized() {
      DataModelPreprocessing.preprocessSegment(this);
    }
  }
  
  @JsonAdapter(DataModelSerialization.SegmentRuleTypeAdapter.class)
  static final class SegmentRule {
    private final List<Clause> clauses;
    private final Integer weight;
    private final ContextKind rolloutContextKind; 
    private final AttributeRef bucketBy;
    
    SegmentRule(List<Clause> clauses, Integer weight, ContextKind rolloutContextKind, AttributeRef bucketBy) {
      this.clauses = clauses == null ? emptyList() : clauses;
      this.weight = weight;
      this.rolloutContextKind = rolloutContextKind;
      this.bucketBy = bucketBy;
    }
    
    // Guaranteed non-null
    List<Clause> getClauses() {
      return clauses;
    }
    
    Integer getWeight() {
      return weight;
    }
    
    ContextKind getRolloutContextKind() {
      return rolloutContextKind;
    }
    
    AttributeRef getBucketBy() {
      return bucketBy;
    }
  }

  static class SegmentTarget {
    private ContextKind contextKind;
    private Set<String> values;
    
    SegmentTarget(ContextKind contextKind, Set<String> values) {
      this.contextKind = contextKind;
      this.values = values;
    }
    
    ContextKind getContextKind() {
      return contextKind;
    }
    
    Set<String> getValues() { // guaranteed non-null
      return values == null ? emptySet() : values;
    }
  }
  
  /**
   * This is an enum-like type rather than an enum because we don't want unrecognized operators to
   * cause parsing of the whole JSON environment to fail. The implementation of each operator is in
   * EvaluatorOperators.
   */
  static class Operator {
    private final String name;
    private final boolean builtin;
    private final int hashCode;
    
    private static final Map<String, Operator> builtins = new HashMap<>();
    
    private Operator(String name, boolean builtin) {
      this.name = name;
      this.builtin = builtin;
      
      // Precompute the hash code for fast map lookups - String.hashCode() does memoize this value,
      // sort of, but we shouldn't have to rely on that 
      this.hashCode = name.hashCode();
    }
    
    private static Operator builtin(String name) {
      Operator op = new Operator(name, true);
      builtins.put(name, op);
      return op;
    }
    
    static final Operator in = builtin("in");
    static final Operator startsWith = builtin("startsWith");
    static final Operator endsWith = builtin("endsWith");
    static final Operator matches = builtin("matches");
    static final Operator contains = builtin("contains");
    static final Operator lessThan = builtin("lessThan");
    static final Operator lessThanOrEqual = builtin("lessThanOrEqual");
    static final Operator greaterThan = builtin("greaterThan");
    static final Operator greaterThanOrEqual = builtin("greaterThanOrEqual");
    static final Operator before = builtin("before");
    static final Operator after = builtin("after");
    static final Operator semVerEqual = builtin("semVerEqual");
    static final Operator semVerLessThan = builtin("semVerLessThan");
    static final Operator semVerGreaterThan = builtin("semVerGreaterThan");
    static final Operator segmentMatch = builtin("segmentMatch");
    
    static Operator forName(String name) {
      // Normally we will only see names that are in the builtins map. Anything else is something
      // the SDK doesn't recognize, but we still need to allow it to exist rather than throwing
      // an error.
      Operator op = builtins.get(name);
      return op == null ? new Operator(name, false) : op;
    }
    
    static Iterable<Operator> getBuiltins() {
      return builtins.values();
    }

    String name() {
      return name;
    }
    
    @Override
    public String toString() {
      return name;
    }
    
    @Override
    public boolean equals(Object other) {
      if (this.builtin) {
        // reference equality is OK for the builtin ones, because we intern them
        return this == other;
      }
      return other instanceof Operator && ((Operator)other).name.equals(this.name);
    }
    
    @Override
    public int hashCode() {
      return hashCode;
    }
  }
  
  /**
   * This enum is all lowercase so that when it is automatically deserialized from JSON, 
   * the lowercase properties properly map to these enumerations.
   */
  static enum RolloutKind {
    rollout,
    experiment
  }
}
