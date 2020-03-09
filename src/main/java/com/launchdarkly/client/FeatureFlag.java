package com.launchdarkly.client;

import com.google.gson.annotations.JsonAdapter;
import com.launchdarkly.client.value.LDValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;

@JsonAdapter(JsonHelpers.PostProcessingDeserializableTypeAdapterFactory.class)
class FeatureFlag implements VersionedData, JsonHelpers.PostProcessingDeserializable {
  private final static Logger logger = LoggerFactory.getLogger(FeatureFlag.class);

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

  EvalResult evaluate(LDUser user, FeatureStore featureStore, EventFactory eventFactory) {
    List<Event.FeatureRequest> prereqEvents = new ArrayList<>();

    if (user == null || user.getKey() == null) {
      // this should have been prevented by LDClient.evaluateInternal
      logger.warn("Null user or null user key when evaluating flag \"{}\"; returning null", key);
      return new EvalResult(EvaluationDetail.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED, LDValue.ofNull()), prereqEvents);
    }

    EvaluationDetail<LDValue> details = evaluate(user, featureStore, prereqEvents, eventFactory);
    return new EvalResult(details, prereqEvents);    
  }

  private EvaluationDetail<LDValue> evaluate(LDUser user, FeatureStore featureStore, List<Event.FeatureRequest> events,
      EventFactory eventFactory) {
    if (!isOn()) {
      return getOffValue(EvaluationReason.off());
    }
    
    EvaluationReason prereqFailureReason = checkPrerequisites(user, featureStore, events, eventFactory);
    if (prereqFailureReason != null) {
      return getOffValue(prereqFailureReason);
    }
    
    // Check to see if targets match
    if (targets != null) {
      for (Target target: targets) {
        if (target.getValues().contains(user.getKey().stringValue())) {
          return getVariation(target.getVariation(), EvaluationReason.targetMatch());
        }
      }
    }
    // Now walk through the rules and see if any match
    if (rules != null) {
      for (int i = 0; i < rules.size(); i++) {
        Rule rule = rules.get(i);
        if (rule.matchesUser(featureStore, user)) {
          EvaluationReason.RuleMatch precomputedReason = rule.getRuleMatchReason();
          EvaluationReason.RuleMatch reason = precomputedReason != null ? precomputedReason : EvaluationReason.ruleMatch(i, rule.getId());
          return getValueForVariationOrRollout(rule, user, reason);
        }
      }
    }
    // Walk through the fallthrough and see if it matches
    return getValueForVariationOrRollout(fallthrough, user, EvaluationReason.fallthrough());
  }

  // Checks prerequisites if any; returns null if successful, or an EvaluationReason if we have to
  // short-circuit due to a prerequisite failure.
  private EvaluationReason checkPrerequisites(LDUser user, FeatureStore featureStore, List<Event.FeatureRequest> events,
      EventFactory eventFactory) {
    if (prerequisites == null) {
      return null;
    }
    for (int i = 0; i < prerequisites.size(); i++) {
      boolean prereqOk = true;
      Prerequisite prereq = prerequisites.get(i);
      FeatureFlag prereqFeatureFlag = featureStore.get(FEATURES, prereq.getKey());
      if (prereqFeatureFlag == null) {
        logger.error("Could not retrieve prerequisite flag \"{}\" when evaluating \"{}\"", prereq.getKey(), key);
        prereqOk = false;
      } else {
        EvaluationDetail<LDValue> prereqEvalResult = prereqFeatureFlag.evaluate(user, featureStore, events, eventFactory);
        // Note that if the prerequisite flag is off, we don't consider it a match no matter what its
        // off variation was. But we still need to evaluate it in order to generate an event.
        if (!prereqFeatureFlag.isOn() || prereqEvalResult == null || prereqEvalResult.getVariationIndex() != prereq.getVariation()) {
          prereqOk = false;
        }
        events.add(eventFactory.newPrerequisiteFeatureRequestEvent(prereqFeatureFlag, user, prereqEvalResult, this));
      }
      if (!prereqOk) {
        EvaluationReason.PrerequisiteFailed precomputedReason = prereq.getPrerequisiteFailedReason();
        return precomputedReason != null ? precomputedReason : EvaluationReason.prerequisiteFailed(prereq.getKey());
      }
    }
    return null;
  }

  private EvaluationDetail<LDValue> getVariation(int variation, EvaluationReason reason) {
    if (variation < 0 || variation >= variations.size()) {
      logger.error("Data inconsistency in feature flag \"{}\": invalid variation index", key);
      return EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, LDValue.ofNull());
    }
    LDValue value = LDValue.normalize(variations.get(variation));
    // normalize() ensures that nulls become LDValue.ofNull() - Gson may give us nulls
    return EvaluationDetail.fromValue(value, variation, reason);
  }

  private EvaluationDetail<LDValue> getOffValue(EvaluationReason reason) {
    if (offVariation == null) { // off variation unspecified - return default value
      return EvaluationDetail.fromValue(LDValue.ofNull(), null, reason);
    }
    return getVariation(offVariation, reason);
  }
  
  private EvaluationDetail<LDValue> getValueForVariationOrRollout(VariationOrRollout vr, LDUser user, EvaluationReason reason) {
    Integer index = vr.variationIndexForUser(user, key, salt);
    if (index == null) {
      logger.error("Data inconsistency in feature flag \"{}\": variation/rollout object with no variation or rollout", key);
      return EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, LDValue.ofNull()); 
    }
    return getVariation(index, reason);
  }
  
  public int getVersion() {
    return version;
  }

  public String getKey() {
    return key;
  }

  public boolean isTrackEvents() {
    return trackEvents;
  }
  
  public boolean isTrackEventsFallthrough() {
    return trackEventsFallthrough;
  }
  
  public Long getDebugEventsUntilDate() {
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
  
  static class EvalResult {
    private final EvaluationDetail<LDValue> details;
    private final List<Event.FeatureRequest> prerequisiteEvents;

    private EvalResult(EvaluationDetail<LDValue> details, List<Event.FeatureRequest> prerequisiteEvents) {
      checkNotNull(details);
      checkNotNull(prerequisiteEvents);
      this.details = details;
      this.prerequisiteEvents = prerequisiteEvents;
    }

    EvaluationDetail<LDValue> getDetails() {
      return details;
    }

    List<Event.FeatureRequest> getPrerequisiteEvents() {
      return prerequisiteEvents;
    }
  }
}
