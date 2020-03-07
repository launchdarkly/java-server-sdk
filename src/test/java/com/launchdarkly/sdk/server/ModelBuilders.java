package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("javadoc")
public abstract class ModelBuilders {
  public static FlagBuilder flagBuilder(String key) {
    return new FlagBuilder(key);
  }
  
  public static FlagBuilder flagBuilder(DataModel.FeatureFlag fromFlag) {
    return new FlagBuilder(fromFlag);
  }
  
  public static DataModel.FeatureFlag booleanFlagWithClauses(String key, DataModel.Clause... clauses) {
    DataModel.Rule rule = ruleBuilder().variation(1).clauses(clauses).build();
    return flagBuilder(key)
        .on(true)
        .rules(rule)
        .fallthrough(fallthroughVariation(0))
        .offVariation(0)
        .variations(LDValue.of(false), LDValue.of(true))
        .build();
  }

  public static DataModel.FeatureFlag flagWithValue(String key, LDValue value) {
    return flagBuilder(key)
        .on(false)
        .offVariation(0)
        .variations(value)
        .build();
  }
  
  public static DataModel.VariationOrRollout fallthroughVariation(int variation) {
    return new DataModel.VariationOrRollout(variation, null);
  }

  public static RuleBuilder ruleBuilder() {
    return new RuleBuilder();
  }

  public static DataModel.Clause clause(UserAttribute attribute, DataModel.Operator op, boolean negate, LDValue... values) {
    return new DataModel.Clause(attribute, op, Arrays.asList(values), negate);
  }

  public static DataModel.Clause clause(UserAttribute attribute, DataModel.Operator op, LDValue... values) {
    return clause(attribute, op, false, values);
  }
  
  public static DataModel.Clause clauseMatchingUser(LDUser user) {
    return clause(UserAttribute.KEY, DataModel.Operator.in, user.getAttribute(UserAttribute.KEY));
  }

  public static DataModel.Clause clauseNotMatchingUser(LDUser user) {
    return clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("not-" + user.getKey()));
  }
  
  public static DataModel.Target target(int variation, String... userKeys) {
    return new DataModel.Target(Arrays.asList(userKeys), variation);
  }
  
  public static DataModel.Prerequisite prerequisite(String key, int variation) {
    return new DataModel.Prerequisite(key, variation);
  }
  
  public static DataModel.Rollout emptyRollout() {
    return new DataModel.Rollout(ImmutableList.<DataModel.WeightedVariation>of(), null);
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
    private List<DataModel.Prerequisite> prerequisites = new ArrayList<>();
    private String salt;
    private List<DataModel.Target> targets = new ArrayList<>();
    private List<DataModel.Rule> rules = new ArrayList<>();
    private DataModel.VariationOrRollout fallthrough;
    private Integer offVariation;
    private List<LDValue> variations = new ArrayList<>();
    private boolean clientSide;
    private boolean trackEvents;
    private boolean trackEventsFallthrough;
    private Long debugEventsUntilDate;
    private boolean deleted;
  
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
        this.rules = f.getRules();
        this.fallthrough = f.getFallthrough();
        this.offVariation = f.getOffVariation();
        this.variations = f.getVariations();
        this.clientSide = f.isClientSide();
        this.trackEvents = f.isTrackEvents();
        this.trackEventsFallthrough = f.isTrackEventsFallthrough();
        this.debugEventsUntilDate = f.getDebugEventsUntilDate();
        this.deleted = f.isDeleted();
      }
    }
  
    FlagBuilder version(int version) {
      this.version = version;
      return this;
    }
  
    FlagBuilder on(boolean on) {
      this.on = on;
      return this;
    }
  
    FlagBuilder prerequisites(DataModel.Prerequisite... prerequisites) {
      this.prerequisites = Arrays.asList(prerequisites);
      return this;
    }
  
    FlagBuilder salt(String salt) {
      this.salt = salt;
      return this;
    }
  
    FlagBuilder targets(DataModel.Target... targets) {
      this.targets = Arrays.asList(targets);
      return this;
    }

    FlagBuilder rules(DataModel.Rule... rules) {
      this.rules = Arrays.asList(rules);
      return this;
    }
  
    FlagBuilder fallthroughVariation(int fallthroughVariation) {
      this.fallthrough = new DataModel.VariationOrRollout(fallthroughVariation, null);
      return this;
    }
    
    FlagBuilder fallthrough(DataModel.VariationOrRollout fallthrough) {
      this.fallthrough = fallthrough;
      return this;
    }
  
    FlagBuilder offVariation(Integer offVariation) {
      this.offVariation = offVariation;
      return this;
    }
    
    FlagBuilder variations(LDValue... variations) {
      this.variations = Arrays.asList(variations);
      return this;
    }
  
    FlagBuilder variations(boolean... variations) {
      List<LDValue> values = new ArrayList<>();
      for (boolean v: variations) {
        values.add(LDValue.of(v));
      }
      this.variations = values;
      return this;
    }
    
    FlagBuilder clientSide(boolean clientSide) {
      this.clientSide = clientSide;
      return this;
    }
    
    FlagBuilder trackEvents(boolean trackEvents) {
      this.trackEvents = trackEvents;
      return this;
    }
  
    FlagBuilder trackEventsFallthrough(boolean trackEventsFallthrough) {
      this.trackEventsFallthrough = trackEventsFallthrough;
      return this;
    }
    
    FlagBuilder debugEventsUntilDate(Long debugEventsUntilDate) {
      this.debugEventsUntilDate = debugEventsUntilDate;
      return this;
    }
    
    FlagBuilder deleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }
  
    DataModel.FeatureFlag build() {
      FeatureFlag flag = new DataModel.FeatureFlag(key, version, on, prerequisites, salt, targets, rules, fallthrough, offVariation, variations,
          clientSide, trackEvents, trackEventsFallthrough, debugEventsUntilDate, deleted);
      flag.afterDeserialized();
      return flag;
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
    private List<String> included = new ArrayList<>();
    private List<String> excluded = new ArrayList<>();
    private String salt = "";
    private List<DataModel.SegmentRule> rules = new ArrayList<>();
    private int version = 0;
    private boolean deleted;

    private SegmentBuilder(String key) {
      this.key = key;
    }
    
    private SegmentBuilder(DataModel.Segment from) {
      this.key = from.getKey();
      this.included = ImmutableList.copyOf(from.getIncluded());
      this.excluded = ImmutableList.copyOf(from.getExcluded());
      this.salt = from.getSalt();
      this.rules = ImmutableList.copyOf(from.getRules());
      this.version = from.getVersion();
      this.deleted = from.isDeleted();
    }
    
    public DataModel.Segment build() {
      return new DataModel.Segment(key, included, excluded, salt, rules, version, deleted);
    }
    
    public SegmentBuilder included(String... included) {
      this.included = Arrays.asList(included);
      return this;
    }
    
    public SegmentBuilder excluded(String... excluded) {
      this.excluded = Arrays.asList(excluded);
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
  }

  public static class SegmentRuleBuilder {
    private List<DataModel.Clause> clauses = new ArrayList<>();
    private Integer weight;
    private UserAttribute bucketBy;

    private SegmentRuleBuilder() {
    }
    
    public DataModel.SegmentRule build() {
      return new DataModel.SegmentRule(clauses, weight, bucketBy);
    }
    
    public SegmentRuleBuilder clauses(DataModel.Clause... clauses) {
      this.clauses = ImmutableList.copyOf(clauses);
      return this;
    }
    
    public SegmentRuleBuilder weight(Integer weight) {
      this.weight = weight;
      return this;
    }
    
    public SegmentRuleBuilder bucketBy(UserAttribute bucketBy) {
      this.bucketBy = bucketBy;
      return this;
    }
  } 
}
