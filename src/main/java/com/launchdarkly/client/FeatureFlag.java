package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class FeatureFlag {
  private final static Logger logger = LoggerFactory.getLogger(FeatureFlag.class);

  private static final Gson gson = new Gson();
  private static final Type mapType = new TypeToken<Map<String, FeatureFlag>>() {
  }.getType();

  private final String key;
  private final int version;
  private final boolean on;
  private final List<Prerequisite> prerequisites;
  private final String salt;
  private final List<Target> targets;
  private final List<Rule> rules;
  private final VariationOrRollout fallthrough;
  private final Integer offVariation; //optional
  private final List<JsonElement> variations;
  private final boolean deleted;

  static FeatureFlag fromJson(String json) {
    return gson.fromJson(json, FeatureFlag.class);
  }

  static Map<String, FeatureFlag> fromJsonMap(String json) {
    return gson.fromJson(json, mapType);
  }

  FeatureFlag(String key, int version, boolean on, List<Prerequisite> prerequisites, String salt, List<Target> targets, List<Rule> rules, VariationOrRollout fallthrough, Integer offVariation, List<JsonElement> variations, boolean deleted) {
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
    this.deleted = deleted;
  }

  EvalResult evaluate(LDUser user, FeatureStore featureStore) throws EvaluationException {
    List<FeatureRequestEvent> prereqEvents = new ArrayList<>();
    JsonElement value = evaluate(user, featureStore, prereqEvents);
    return new EvalResult(value, prereqEvents);
  }

  // Returning either a JsonElement or null indicating prereq failure/error.
  private JsonElement evaluate(LDUser user, FeatureStore featureStore, List<FeatureRequestEvent> events) throws EvaluationException {
    boolean prereqOk = true;
    if (prerequisites != null) {
      for (Prerequisite prereq : prerequisites) {
        FeatureFlag prereqFeatureFlag = featureStore.get(prereq.getKey());
        JsonElement prereqEvalResult = null;
        if (prereqFeatureFlag == null) {
          logger.error("Could not retrieve prerequisite flag: " + prereq.getKey() + " when evaluating: " + key);
          return null;
        } else if (prereqFeatureFlag.isOn()) {
          prereqEvalResult = prereqFeatureFlag.evaluate(user, featureStore, events);
          try {
            JsonElement variation = prereqFeatureFlag.getVariation(prereq.getVariation());
            if (prereqEvalResult == null || variation == null || !prereqEvalResult.equals(variation)) {
              prereqOk = false;
            }
          } catch (EvaluationException err) {
            logger.warn("Error evaluating prerequisites: " + err.getMessage());
            prereqOk = false;
          }
        } else {
          prereqOk = false;
        }
        //We don't short circuit and also send events for each prereq.
        events.add(new FeatureRequestEvent(prereqFeatureFlag.getKey(), user, prereqEvalResult, null, prereqFeatureFlag.getVersion(), key));
      }
    }
    if (prereqOk) {
      return getVariation(evaluateIndex(user));
    }
    return null;
  }

  private Integer evaluateIndex(LDUser user) {
    // Check to see if targets match
    if (targets != null) {
      for (Target target : targets) {
        for (String v : target.getValues()) {
          if (v.equals(user.getKey().getAsString())) {
            return target.getVariation();
          }
        }
      }
    }
    // Now walk through the rules and see if any match
    if (rules != null) {
      for (Rule rule : rules) {
        if (rule.matchesUser(user)) {
          return rule.variationIndexForUser(user, key, salt);
        }
      }
    }
    // Walk through the fallthrough and see if it matches
    return fallthrough.variationIndexForUser(user, key, salt);
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

  int getVersion() {
    return version;
  }

  String getKey() {
    return key;
  }

  boolean isDeleted() {
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

  Integer getOffVariation() { return offVariation; }

  static class EvalResult {
    private final JsonElement value;
    private final List<FeatureRequestEvent> prerequisiteEvents;

    private EvalResult(JsonElement value, List<FeatureRequestEvent> prerequisiteEvents) {
      this.value = value;
      this.prerequisiteEvents = prerequisiteEvents;
    }

    JsonElement getValue() {
      return value;
    }

    List<FeatureRequestEvent> getPrerequisiteEvents() {
      return prerequisiteEvents;
    }
  }
}
