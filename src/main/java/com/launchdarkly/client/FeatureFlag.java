package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;

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
  private final Rule fallthrough;
  private final Integer offVariation; //optional
  private final List<JsonElement> variations;
  private final boolean deleted;

  static FeatureFlag fromJson(String json) {
    return gson.fromJson(json, FeatureFlag.class);
  }

  static Map<String, FeatureFlag> fromJsonMap(String json) {
    return gson.fromJson(json, mapType);
  }

  FeatureFlag(String key, int version, boolean on, List<Prerequisite> prerequisites, String salt, List<Target> targets, List<Rule> rules, Rule fallthrough, Integer offVariation, List<JsonElement> variations, boolean deleted) {
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

  Integer getOffVariation() {
    return this.offVariation;
  }

  JsonElement getOffVariationValue() {
    if (offVariation != null && offVariation < variations.size()) {
      return variations.get(offVariation);
    }
    return null;
  }

  EvalResult evaluate(LDUser user, FeatureStore featureStore) {
    if (user == null || user.getKey() == null) {
      return null;
    }
    List<FeatureRequestEvent> prereqEvents = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    return evaluate(user, featureStore, prereqEvents, visited);
  }

  private EvalResult evaluate(LDUser user, FeatureStore featureStore, List<FeatureRequestEvent> events, Set<String> visited) {
    for (Prerequisite prereq : prerequisites) {
      visited.add(key);
      if (visited.contains(prereq.getKey())) {
        logger.error("Prerequisite cycle detected when evaluating feature flag: " + key);
        return null;
      }
      FeatureFlag prereqFeatureFlag = featureStore.get(prereq.getKey());
      if (prereqFeatureFlag == null) {
        logger.error("Could not retrieve prerequisite flag: " + prereq.getKey() + " when evaluating: " + key);
        return null;
      }
      JsonElement prereqValue;
      if (prereqFeatureFlag.isOn()) {
        EvalResult prereqEvalResult = prereqFeatureFlag.evaluate(user, featureStore, events, visited);
        if (prereqEvalResult == null) {
          return null;
        }
        prereqValue = prereqEvalResult.value;
        visited = prereqEvalResult.visitedFeatureKeys;
        events = prereqEvalResult.prerequisiteEvents;
        events.add(new FeatureRequestEvent(prereqFeatureFlag.getKey(), user, prereqValue, null));
        if (prereqValue == null || !prereqValue.equals(prereqFeatureFlag.getVariation(prereq.getVariation()))) {
          return new EvalResult(null, events, visited);
        }
      } else {
        return null;
      }
    }
    return new EvalResult(getVariation(evaluateIndex(user)), events, visited);
  }

  private Integer evaluateIndex(LDUser user) {
    // Check to see if targets match
    for (Target target : targets) {
      for (String v : target.getValues()) {
        if (v.equals(user.getKey().getAsString())) {
          return target.getVariation();
        }
      }
    }

    // Now walk through the rules and see if any match
    for (Rule rule : rules) {
      if (rule.matchesUser(user)) {
        return rule.variationIndexForUser(user, key, salt);
      }
    }

    // Walk through the fallthrough and see if it matches
    return fallthrough.variationIndexForUser(user, key, salt);
  }

  private JsonElement getVariation(Integer index) {
    if (index == null || index >= variations.size()) {
      return null;
    } else {
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

  Rule getFallthrough() {
    return fallthrough;
  }

  List<JsonElement> getVariations() {
    return variations;
  }

  static class EvalResult {
    private JsonElement value;
    private List<FeatureRequestEvent> prerequisiteEvents;
    private Set<String> visitedFeatureKeys;

    private EvalResult(JsonElement value, List<FeatureRequestEvent> prerequisiteEvents, Set<String> visitedFeatureKeys) {
      this.value = value;
      this.prerequisiteEvents = prerequisiteEvents;
      this.visitedFeatureKeys = visitedFeatureKeys;
    }

    JsonElement getValue() {
      return value;
    }

    List<FeatureRequestEvent> getPrerequisiteEvents() {
      return prerequisiteEvents;
    }
  }
}
