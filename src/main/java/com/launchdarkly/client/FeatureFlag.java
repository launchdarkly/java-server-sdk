package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
  private boolean trackEvents;
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
      boolean trackEvents, Long debugEventsUntilDate, boolean deleted) {
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
    this.trackEvents = trackEvents;
    this.debugEventsUntilDate = debugEventsUntilDate;
    this.deleted = deleted;
  }

  EvalResult evaluate(LDUser user, FeatureStore featureStore, EventFactory eventFactory) throws EvaluationException {
    List<Event.FeatureRequest> prereqEvents = new ArrayList<>();

    if (user == null || user.getKey() == null) {
      logger.warn("Null user or null user key when evaluating flag: " + key + "; returning null");
      return new EvalResult(null, prereqEvents);
    }

    if (isOn()) {
      EvaluationDetails<JsonElement> details = evaluate(user, featureStore, prereqEvents, eventFactory);
      return new EvalResult(details, prereqEvents);
    }
    
    EvaluationDetails<JsonElement> details = new EvaluationDetails<>(EvaluationReason.off(), offVariation, getOffVariationValue());
    return new EvalResult(details, prereqEvents);
  }

  // Returning either a JsonElement or null indicating prereq failure/error.
  private EvaluationDetails<JsonElement> evaluate(LDUser user, FeatureStore featureStore, List<Event.FeatureRequest> events,
      EventFactory eventFactory) throws EvaluationException {
    EvaluationReason prereqFailureReason = checkPrerequisites(user, featureStore, events, eventFactory);
    if (prereqFailureReason != null) {
      return new EvaluationDetails<>(prereqFailureReason, offVariation, getOffVariationValue());
    }
    
    // Check to see if targets match
    if (targets != null) {
      for (int i = 0; i < targets.size(); i++) {
        Target target = targets.get(i);
        for (String v : target.getValues()) {
          if (v.equals(user.getKey().getAsString())) {
            return new EvaluationDetails<>(EvaluationReason.targetMatch(i),
                target.getVariation(), getVariation(target.getVariation()));
          }
        }
      }
    }
    // Now walk through the rules and see if any match
    if (rules != null) {
      for (int i = 0; i < rules.size(); i++) {
        Rule rule = rules.get(i);
        if (rule.matchesUser(featureStore, user)) {
          int index = rule.variationIndexForUser(user, key, salt);
          return new EvaluationDetails<>(EvaluationReason.ruleMatch(i, rule.getId()),
              index, getVariation(index));
        }
      }
    }
    // Walk through the fallthrough and see if it matches
    int index = fallthrough.variationIndexForUser(user, key, salt);
    return new EvaluationDetails<>(EvaluationReason.fallthrough(), index, getVariation(index));
  }

  // Checks prerequisites if any; returns null if successful, or an EvaluationReason if we have to
  // short-circuit due to a prerequisite failure.
  private EvaluationReason checkPrerequisites(LDUser user, FeatureStore featureStore, List<Event.FeatureRequest> events,
      EventFactory eventFactory) throws EvaluationException {
    if (prerequisites == null) {
      return null;
    }
    List<String> failedPrereqs = null;
    for (int i = 0; i < prerequisites.size(); i++) {
      boolean prereqOk = true;
      Prerequisite prereq = prerequisites.get(i);
      FeatureFlag prereqFeatureFlag = featureStore.get(FEATURES, prereq.getKey());
      EvaluationDetails<JsonElement> prereqEvalResult = null;
      if (prereqFeatureFlag == null) {
        logger.error("Could not retrieve prerequisite flag: " + prereq.getKey() + " when evaluating: " + key);
        prereqOk = false;
      } else if (prereqFeatureFlag.isOn()) {
        prereqEvalResult = prereqFeatureFlag.evaluate(user, featureStore, events, eventFactory);
        if (prereqEvalResult == null || prereqEvalResult.getVariationIndex() != prereq.getVariation()) {
          prereqOk = false;
        }
      } else {
        prereqOk = false;
      }
      // We continue to evaluate all prerequisites even if one failed.
      if (prereqFeatureFlag != null) {
        events.add(eventFactory.newPrerequisiteFeatureRequestEvent(prereqFeatureFlag, user, prereqEvalResult, this));
      }
      if (!prereqOk) {
        if (failedPrereqs == null) {
          failedPrereqs = new ArrayList<>();
        }
        failedPrereqs.add(prereq.getKey());
      }
    }
    if (failedPrereqs != null && !failedPrereqs.isEmpty()) {
      return EvaluationReason.prerequisitesFailed(failedPrereqs);
    }
    return null;
  }

  JsonElement getOffVariationValue() throws EvaluationException {
    if (offVariation == null) {
      return null;
    }

    if (offVariation >= variations.size()) {
      throw new EvaluationException("Invalid off variation index");
    }

    return variations.get(offVariation);
  }

  private JsonElement getVariation(Integer index) throws EvaluationException {
    // If the supplied index is null, then rules didn't match, and we want to return
    // the off variation
    if (index == null) {
      return null;
    }
    // If the index doesn't refer to a valid variation, that's an unexpected exception and we will
    // return the default variation
    else if (index >= variations.size()) {
      throw new EvaluationException("Invalid index");
    }
    else {
      return variations.get(index);
    }
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
  
  static class EvalResult {
    private final EvaluationDetails<JsonElement> details;
    private final List<Event.FeatureRequest> prerequisiteEvents;

    private EvalResult(EvaluationDetails<JsonElement> details, List<Event.FeatureRequest> prerequisiteEvents) {
      this.details = details;
      this.prerequisiteEvents = prerequisiteEvents;
    }

    EvaluationDetails<JsonElement> getDetails() {
      return details;
    }

    List<Event.FeatureRequest> getPrerequisiteEvents() {
      return prerequisiteEvents;
    }
  }
}
