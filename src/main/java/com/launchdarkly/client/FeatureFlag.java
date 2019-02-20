package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;

class FeatureFlag implements VersionedData {
  private final static Logger logger = LoggerFactory.getLogger(FeatureFlag.class);

  private static final Type mapType = new TypeToken<Map<String, FeatureFlag>>() {
  }.getType();

  private String key;
  private int version;
  private boolean on;
  private List<Prerequisite> prerequisites;
  private String salt;
  private List<Target> targets;
  private List<Rule> rules;
  private VariationOrRollout fallthrough;
  private Integer offVariation; //optional
  private List<JsonElement> variations;
  private boolean clientSide;
  private boolean trackEvents;
  private boolean trackEventsFallthrough;
  private Long debugEventsUntilDate;
  private boolean deleted;

  static FeatureFlag fromJson(LDConfig config, String json) {
    return config.gson.fromJson(json, FeatureFlag.class);
  }

  static Map<String, FeatureFlag> fromJsonMap(LDConfig config, String json) {
    return config.gson.fromJson(json, mapType);
  }

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  FeatureFlag() {}

  FeatureFlag(String key, int version, boolean on, List<Prerequisite> prerequisites, String salt, List<Target> targets,
      List<Rule> rules, VariationOrRollout fallthrough, Integer offVariation, List<JsonElement> variations,
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
      return new EvalResult(EvaluationDetail.<JsonElement>error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED, null), prereqEvents);
    }

    EvaluationDetail<JsonElement> details = evaluate(user, featureStore, prereqEvents, eventFactory);
    return new EvalResult(details, prereqEvents);    
  }

  private EvaluationDetail<JsonElement> evaluate(LDUser user, FeatureStore featureStore, List<Event.FeatureRequest> events,
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
        for (String v : target.getValues()) {
          if (v.equals(user.getKey().getAsString())) {
            return getVariation(target.getVariation(), EvaluationReason.targetMatch());
          }
        }
      }
    }
    // Now walk through the rules and see if any match
    if (rules != null) {
      for (int i = 0; i < rules.size(); i++) {
        Rule rule = rules.get(i);
        if (rule.matchesUser(featureStore, user)) {
          return getValueForVariationOrRollout(rule, user, EvaluationReason.ruleMatch(i, rule.getId()));
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
        EvaluationDetail<JsonElement> prereqEvalResult = prereqFeatureFlag.evaluate(user, featureStore, events, eventFactory);
        // Note that if the prerequisite flag is off, we don't consider it a match no matter what its
        // off variation was. But we still need to evaluate it in order to generate an event.
        if (!prereqFeatureFlag.isOn() || prereqEvalResult == null || prereqEvalResult.getVariationIndex() != prereq.getVariation()) {
          prereqOk = false;
        }
        events.add(eventFactory.newPrerequisiteFeatureRequestEvent(prereqFeatureFlag, user, prereqEvalResult, this));
      }
      if (!prereqOk) {
        return EvaluationReason.prerequisiteFailed(prereq.getKey());
      }
    }
    return null;
  }

  private EvaluationDetail<JsonElement> getVariation(int variation, EvaluationReason reason) {
    if (variation < 0 || variation >= variations.size()) {
      logger.error("Data inconsistency in feature flag \"{}\": invalid variation index", key);
      return EvaluationDetail.<JsonElement>error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null);
    }
    return new EvaluationDetail<JsonElement>(reason, variation, variations.get(variation));
  }

  private EvaluationDetail<JsonElement> getOffValue(EvaluationReason reason) {
    if (offVariation == null) { // off variation unspecified - return default value
      return new EvaluationDetail<JsonElement>(reason, null, null);
    }
    return getVariation(offVariation, reason);
  }
  
  private EvaluationDetail<JsonElement> getValueForVariationOrRollout(VariationOrRollout vr, LDUser user, EvaluationReason reason) {
    Integer index = vr.variationIndexForUser(user, key, salt);
    if (index == null) {
      logger.error("Data inconsistency in feature flag \"{}\": variation/rollout object with no variation or rollout", key);
      return EvaluationDetail.<JsonElement>error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null); 
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

  List<JsonElement> getVariations() {
    return variations;
  }

  Integer getOffVariation() {
    return offVariation;
  }

  boolean isClientSide() {
    return clientSide;
  }
    
  static class EvalResult {
    private final EvaluationDetail<JsonElement> details;
    private final List<Event.FeatureRequest> prerequisiteEvents;

    private EvalResult(EvaluationDetail<JsonElement> details, List<Event.FeatureRequest> prerequisiteEvents) {
      checkNotNull(details);
      checkNotNull(prerequisiteEvents);
      this.details = details;
      this.prerequisiteEvents = prerequisiteEvents;
    }

    EvaluationDetail<JsonElement> getDetails() {
      return details;
    }

    List<Event.FeatureRequest> getPrerequisiteEvents() {
      return prerequisiteEvents;
    }
  }
}
