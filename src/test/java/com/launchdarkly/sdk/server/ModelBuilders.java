package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Prerequisite;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;
import com.launchdarkly.sdk.server.DataModel.SegmentTarget;
import com.launchdarkly.sdk.server.DataModel.Target;
import com.launchdarkly.sdk.server.DataModel.VariationOrRollout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

@SuppressWarnings("javadoc")
public abstract class ModelBuilders {
  public static FlagBuilder flagBuilder(String key) {
    return new FlagBuilder(key);
  }
  
  public static FlagBuilder flagBuilder(DataModel.FeatureFlag fromFlag) {
    return new FlagBuilder(fromFlag);
  }
  
  public static FeatureFlag booleanFlagWithClauses(String key, DataModel.Clause... clauses) {
    DataModel.Rule rule = ruleBuilder().variation(1).clauses(clauses).build();
    return flagBuilder(key)
        .on(true)
        .rules(rule)
        .fallthrough(fallthroughVariation(0))
        .offVariation(0)
        .variations(LDValue.of(false), LDValue.of(true))
        .build();
  }

  public static FeatureFlag flagWithValue(String key, LDValue value) {
    return flagBuilder(key)
        .on(false)
        .offVariation(0)
        .variations(value)
        .build();
  }
  
  public static VariationOrRollout fallthroughVariation(int variation) {
    return new DataModel.VariationOrRollout(variation, null);
  }

  public static RuleBuilder ruleBuilder() {
    return new RuleBuilder();
  }

  public static Clause clause(
      ContextKind contextKind,
      AttributeRef attribute,
      Operator op,
      LDValue... values
      ) {
    return new Clause(contextKind, attribute, op, Arrays.asList(values), false);
  }

  public static Clause clause(ContextKind contextKind, String attributeName, DataModel.Operator op, LDValue... values) {
    return clause(contextKind, AttributeRef.fromLiteral(attributeName), op, values);
  }

  public static Clause clause(String attributeName, DataModel.Operator op, LDValue... values) {
    return clause(null, attributeName, op, values);
  }

  public static Clause clauseMatchingContext(LDContext context) {
    if (context.isMultiple()) {
      return clauseMatchingContext(context.getIndividualContext(0));
    }
    return clause(context.getKind(), AttributeRef.fromLiteral("key"), DataModel.Operator.in, LDValue.of(context.getKey()));
  }

  public static Clause clauseNotMatchingContext(LDContext context) {
    return negateClause(clauseMatchingContext(context));
  }

  public static Clause clauseMatchingSegment(String... segmentKeys) {
    LDValue[] values = new LDValue[segmentKeys.length];
    for (int i = 0; i < segmentKeys.length; i++) {
      values[i] = LDValue.of(segmentKeys[i]);
    }
    return clause(null, (AttributeRef)null, DataModel.Operator.segmentMatch, values);
  }
  
  public static Clause clauseMatchingSegment(Segment segment) {
    return clauseMatchingSegment(segment.getKey());
  }
  
  public static Clause negateClause(Clause clause) {
    return new Clause(clause.getContextKind(), clause.getAttribute(), clause.getOp(), clause.getValues(), !clause.isNegate());
  }

  public static Target target(ContextKind contextKind, int variation, String... userKeys) {
    return new Target(contextKind, ImmutableSet.copyOf(userKeys), variation);
  }
  
  public static Target target(int variation, String... userKeys) {
    return target(null, variation, userKeys);
  }
  
  public static Prerequisite prerequisite(String key, int variation) {
    return new DataModel.Prerequisite(key, variation);
  }
  
  public static Rollout emptyRollout() {
    return new DataModel.Rollout(null, ImmutableList.<DataModel.WeightedVariation>of(), null, RolloutKind.rollout, null);
  }
  
  public static SegmentBuilder segmentBuilder(String key) {
    return new SegmentBuilder(key);
  }

  public static SegmentRuleBuilder segmentRuleBuilder() {
    return new SegmentRuleBuilder();
  }
  
  public static class FlagBuilder {
    private String key;
    private int version;
    private boolean on;
    private List<Prerequisite> prerequisites = new ArrayList<>();
    private String salt;
    private List<Target> targets = new ArrayList<>();
    private List<Target> contextTargets = new ArrayList<>();
    private List<Rule> rules = new ArrayList<>();
    private VariationOrRollout fallthrough;
    private Integer offVariation;
    private List<LDValue> variations = new ArrayList<>();
    private boolean clientSide;
    private boolean trackEvents;
    private boolean trackEventsFallthrough;
    private Long debugEventsUntilDate;
    private boolean deleted;
    private Long samplingRatio;
    private FeatureFlag.Migration migration;
    private boolean excludeFromSummaries;

    private boolean disablePreprocessing = false;
    
    private FlagBuilder(String key) {
      this.key = key;
    }
  
    private FlagBuilder(DataModel.FeatureFlag f) {
      if (f != null) {
        this.key = f.getKey();
        this.version = f.getVersion();
        this.on = f.isOn();
        this.prerequisites = f.getPrerequisites();
        this.salt = f.getSalt();
        this.targets = f.getTargets();
        this.contextTargets = f.getContextTargets();
        this.rules = f.getRules();
        this.fallthrough = f.getFallthrough();
        this.offVariation = f.getOffVariation();
        this.variations = f.getVariations();
        this.clientSide = f.isClientSide();
        this.trackEvents = f.isTrackEvents();
        this.trackEventsFallthrough = f.isTrackEventsFallthrough();
        this.debugEventsUntilDate = f.getDebugEventsUntilDate();
        this.deleted = f.isDeleted();
        this.samplingRatio = f.getSamplingRatio();
        this.migration = f.getMigration();
        this.excludeFromSummaries = f.isExcludeFromSummaries();
      }
    }
  
    public FlagBuilder version(int version) {
      this.version = version;
      return this;
    }
  
    public FlagBuilder on(boolean on) {
      this.on = on;
      return this;
    }
  
    public FlagBuilder prerequisites(Prerequisite... prerequisites) {
      this.prerequisites = Arrays.asList(prerequisites);
      return this;
    }
  
    public FlagBuilder salt(String salt) {
      this.salt = salt;
      return this;
    }
  
    public FlagBuilder targets(Target... targets) {
      this.targets = Arrays.asList(targets);
      return this;
    }

    public FlagBuilder addTarget(int variation, String... values) {
      targets.add(target(variation, values));
      return this;
    }

    public FlagBuilder contextTargets(Target... contextTargets) {
      this.contextTargets = Arrays.asList(contextTargets);
      return this;
    }

    public FlagBuilder addContextTarget(ContextKind contextKind, int variation, String... values) {
      contextTargets.add(target(contextKind, variation, values));
      return this;
    }
    
    public FlagBuilder rules(Rule... rules) {
      this.rules = Arrays.asList(rules);
      return this;
    }
  
    public FlagBuilder addRule(Rule rule) {
      rules.add(rule);
      return this;
    }
    
    public FlagBuilder addRule(String id, int variation, String... clausesAsJson) {
      Clause[] clauses = new Clause[clausesAsJson.length];
      for (int i = 0; i < clausesAsJson.length; i++) {
        clauses[i] = JsonHelpers.deserialize(clausesAsJson[i], Clause.class);
      }
      return addRule(ruleBuilder().id(id).variation(variation).clauses(clauses).build());
    }
    
    public FlagBuilder fallthroughVariation(int fallthroughVariation) {
      this.fallthrough = new DataModel.VariationOrRollout(fallthroughVariation, null);
      return this;
    }
    
    public FlagBuilder fallthrough(Rollout rollout) {
      this.fallthrough = new DataModel.VariationOrRollout(null, rollout);
      return this;
    }
    
    public FlagBuilder fallthrough(VariationOrRollout fallthrough) {
      this.fallthrough = fallthrough;
      return this;
    }
  
    public FlagBuilder offVariation(Integer offVariation) {
      this.offVariation = offVariation;
      return this;
    }
    
    public FlagBuilder variations(LDValue... variations) {
      this.variations = Arrays.asList(variations);
      return this;
    }
  
    public FlagBuilder variations(boolean... variations) {
      List<LDValue> values = new ArrayList<>();
      for (boolean v: variations) {
        values.add(LDValue.of(v));
      }
      this.variations = values;
      return this;
    }

    public FlagBuilder variations(String... variations) {
      List<LDValue> values = new ArrayList<>();
      for (String v: variations) {
        values.add(LDValue.of(v));
      }
      this.variations = values;
      return this;
    }
    
    public FlagBuilder generatedVariations(int numVariations) {
      variations.clear();
      for (int i = 0; i < numVariations; i++) {
        variations.add(LDValue.of(i));
      }
      return this;
    }
    
    public FlagBuilder clientSide(boolean clientSide) {
      this.clientSide = clientSide;
      return this;
    }
    
    public FlagBuilder trackEvents(boolean trackEvents) {
      this.trackEvents = trackEvents;
      return this;
    }
  
    public FlagBuilder trackEventsFallthrough(boolean trackEventsFallthrough) {
      this.trackEventsFallthrough = trackEventsFallthrough;
      return this;
    }
    
    public FlagBuilder debugEventsUntilDate(Long debugEventsUntilDate) {
      this.debugEventsUntilDate = debugEventsUntilDate;
      return this;
    }
    
    public FlagBuilder deleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }
  
    public FlagBuilder disablePreprocessing(boolean disable) {
      this.disablePreprocessing = disable;
      return this;
    }

    public FlagBuilder samplingRatio(long samplingRatio) {
      this.samplingRatio = samplingRatio;
      return this;
    }

    public FlagBuilder migration(FeatureFlag.Migration migration) {
      this.migration = migration;
      return this;
    }
    
    public FeatureFlag build() {
      FeatureFlag flag = new DataModel.FeatureFlag(key, version, on, prerequisites, salt, targets,
          contextTargets, rules, fallthrough, offVariation, variations,
          clientSide, trackEvents, trackEventsFallthrough, debugEventsUntilDate, deleted,
          samplingRatio, migration, excludeFromSummaries);
      if (!disablePreprocessing) {
        flag.afterDeserialized();
      }
      return flag;
    }
  }

  public static class MigrationBuilder {
    private Long checkRatio;

    public MigrationBuilder checkRatio(long checkRatio) {
      this.checkRatio = checkRatio;
      return this;
    }

    public FeatureFlag.Migration build() {
      return new FeatureFlag.Migration(checkRatio);
    }
  }
  
  public static class RuleBuilder {
    private String id;
    private List<DataModel.Clause> clauses = new ArrayList<>();
    private Integer variation;
    private DataModel.Rollout rollout;
    private boolean trackEvents;

    private RuleBuilder() {
    }
    
    public DataModel.Rule build() {
      return new DataModel.Rule(id, clauses, variation, rollout, trackEvents);
    }
    
    public RuleBuilder id(String id) {
      this.id = id;
      return this;
    }
    
    public RuleBuilder clauses(DataModel.Clause... clauses) {
      this.clauses = ImmutableList.copyOf(clauses);
      return this;
    }
    
    public RuleBuilder variation(Integer variation) {
      this.variation = variation;
      return this;
    }
    
    public RuleBuilder rollout(DataModel.Rollout rollout) {
      this.rollout = rollout;
      return this;
    }
    
    public RuleBuilder trackEvents(boolean trackEvents) {
      this.trackEvents = trackEvents;
      return this;
    }
  }
  
  public static class SegmentBuilder {
    private String key;
    private Set<String> included = new HashSet<>();
    private Set<String> excluded = new HashSet<>();
    private List<SegmentTarget> includedContexts = new ArrayList<>();
    private List<SegmentTarget> excludedContexts = new ArrayList<>();
    private String salt = "";
    private List<SegmentRule> rules = new ArrayList<>();
    private int version = 0;
    private boolean deleted;
    private boolean unbounded;
    private ContextKind unboundedContextKind;
    private Integer generation;
    private boolean disablePreprocessing;

    private SegmentBuilder(String key) {
      this.key = key;
    }
    
    private SegmentBuilder(Segment from) {
      this.key = from.getKey();
      this.included = new HashSet<>(from.getIncluded());
      this.excluded = new HashSet<>(from.getExcluded());
      this.includedContexts = new ArrayList<>(from.getIncludedContexts());
      this.excludedContexts = new ArrayList<>(from.getIncludedContexts());
      this.salt = from.getSalt();
      this.rules = new ArrayList<>(from.getRules());
      this.version = from.getVersion();
      this.deleted = from.isDeleted();
    }
    
    public Segment build() {
      Segment s = new Segment(key, included, excluded, includedContexts, excludedContexts,
          salt, rules, version, deleted, unbounded, unboundedContextKind, generation);
      if (!disablePreprocessing) {
        s.afterDeserialized();
      }
      return s;
    }

    public SegmentBuilder disablePreprocessing(boolean disable) {
      this.disablePreprocessing = disable;
      return this;
    }
    
    public SegmentBuilder included(String... included) {
      this.included.addAll(asList(included));
      return this;
    }
    
    public SegmentBuilder excluded(String... excluded) {
      this.excluded.addAll(asList(excluded));
      return this;
    }
    
    public SegmentBuilder includedContexts(ContextKind contextKind, String... keys) {
      this.includedContexts.add(new SegmentTarget(contextKind, ImmutableSet.copyOf(keys)));
      return this;
    }

    public SegmentBuilder excludedContexts(ContextKind contextKind, String... keys) {
      this.excludedContexts.add(new SegmentTarget(contextKind, ImmutableSet.copyOf(keys)));
      return this;
    }

    public SegmentBuilder salt(String salt) {
      this.salt = salt;
      return this;
    }
    
    public SegmentBuilder rules(DataModel.SegmentRule... rules) {
      this.rules = Arrays.asList(rules);
      return this;
    }
    
    public SegmentBuilder version(int version) {
      this.version = version;
      return this;
    }
    
    public SegmentBuilder deleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public SegmentBuilder unbounded(boolean unbounded) {
      this.unbounded = unbounded;
      return this;
    }

    public SegmentBuilder unboundedContextKind(ContextKind unboundedContextKind) {
      this.unboundedContextKind = unboundedContextKind;
      return this;
    }
    
    public SegmentBuilder generation(Integer generation) {
      this.generation = generation;
      return this;
    }
  }

  public static class SegmentRuleBuilder {
    private List<DataModel.Clause> clauses = new ArrayList<>();
    private Integer weight;
    private ContextKind rolloutContextKind;
    private AttributeRef bucketBy;

    private SegmentRuleBuilder() {
    }
    
    public SegmentRule build() {
      return new SegmentRule(clauses, weight, rolloutContextKind, bucketBy);
    }
    
    public SegmentRuleBuilder clauses(DataModel.Clause... clauses) {
      this.clauses = ImmutableList.copyOf(clauses);
      return this;
    }
    
    public SegmentRuleBuilder weight(Integer weight) {
      this.weight = weight;
      return this;
    }
    
    public SegmentRuleBuilder rolloutContextKind(ContextKind rolloutContextKind) {
      this.rolloutContextKind = rolloutContextKind;
      return this;
    }
    
    public SegmentRuleBuilder bucketBy(AttributeRef bucketBy) {
      this.bucketBy = bucketBy;
      return this;
    }
  } 
}
