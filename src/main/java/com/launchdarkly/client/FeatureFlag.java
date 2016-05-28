package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;

public class FeatureFlag {
  private final static Logger logger = LoggerFactory.getLogger(FeatureFlag.class);

  private static final Gson gson = new Gson();
  private static final Type mapType = new TypeToken<Map<String, FeatureFlag>>() {
  }.getType();

  private String key;
  private int version;
  private boolean on;
  private List<Prerequisite> prerequisites;
  private String salt;
  private String sel;
  private List<Target> targets;
  private List<Rule> rules;
  private Rule fallthrough;
  private Integer offVariation; //optional
  private List<JsonElement> variations;
  private boolean deleted;

  public static FeatureFlag fromJson(String json) {
    return gson.fromJson(json, FeatureFlag.class);
  }

  public static Map<String, FeatureFlag> fromJsonMap(String json) {
    return gson.fromJson(json, mapType);
  }

  public FeatureFlag(String key, int version, boolean on, List<Prerequisite> prerequisites, String salt, String sel, List<Target> targets, List<Rule> rules, Rule fallthrough, Integer offVariation, List<JsonElement> variations, boolean deleted) {
    this.key = key;
    this.version = version;
    this.on = on;
    this.prerequisites = prerequisites;
    this.salt = salt;
    this.sel = sel;
    this.targets = targets;
    this.rules = rules;
    this.fallthrough = fallthrough;
    this.offVariation = offVariation;
    this.variations = variations;
    this.deleted = deleted;
  }

  JsonElement getOffVariation() {
    if (offVariation != null && offVariation < variations.size()) {
      return variations.get(offVariation);
    }
    return null;
  }

  EvalResult evaluate(LDUser user, FeatureStore featureStore) {
    if (user == null || user.getKey() == null) {
      return null;
    }
    Set<FeatureRequestEvent> prereqEvents = new HashSet<>();
    Set<String> visited = new HashSet<>();
    return evaluate(user, featureStore, prereqEvents, visited);
  }

  private EvalResult evaluate(LDUser user, FeatureStore featureStore, Set<FeatureRequestEvent> events, Set<String> visited) {
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
      } else {
        prereqValue = prereqFeatureFlag.getOffVariation();
      }
      events.add(new FeatureRequestEvent(prereqFeatureFlag.getKey(), user, prereqValue, null));
      if (prereqValue == null || !prereqValue.equals(prereqFeatureFlag.getVariation(prereq.getVariation()))) {
        return new EvalResult(null, events, visited);
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

  public int getVersion() {
    return version;
  }

  void setVersion(int version) {
    this.version = version;
  }

  public String getKey() {
    return key;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted() {
    deleted = true;
  }

  public boolean isOn() {
    return on;
  }

  public List<Prerequisite> getPrerequisites() {
    return prerequisites;
  }

  public String getSalt() {
    return salt;
  }

  public String getSel() {
    return sel;
  }

  public List<Target> getTargets() {
    return targets;
  }

  public List<Rule> getRules() {
    return rules;
  }

  public Rule getFallthrough() {
    return fallthrough;
  }

  public List<JsonElement> getVariations() {
    return variations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FeatureFlag that = (FeatureFlag) o;

    if (version != that.version) return false;
    if (on != that.on) return false;
    if (deleted != that.deleted) return false;
    if (key != null ? !key.equals(that.key) : that.key != null) return false;
    if (prerequisites != null ? !prerequisites.equals(that.prerequisites) : that.prerequisites != null) return false;
    if (salt != null ? !salt.equals(that.salt) : that.salt != null) return false;
    if (sel != null ? !sel.equals(that.sel) : that.sel != null) return false;
    if (targets != null ? !targets.equals(that.targets) : that.targets != null) return false;
    if (rules != null ? !rules.equals(that.rules) : that.rules != null) return false;
    if (fallthrough != null ? !fallthrough.equals(that.fallthrough) : that.fallthrough != null) return false;
    if (offVariation != null ? !offVariation.equals(that.offVariation) : that.offVariation != null) return false;
    return variations != null ? variations.equals(that.variations) : that.variations == null;

  }

  @Override
  public int hashCode() {
    int result = key != null ? key.hashCode() : 0;
    result = 31 * result + version;
    result = 31 * result + (on ? 1 : 0);
    result = 31 * result + (prerequisites != null ? prerequisites.hashCode() : 0);
    result = 31 * result + (salt != null ? salt.hashCode() : 0);
    result = 31 * result + (sel != null ? sel.hashCode() : 0);
    result = 31 * result + (targets != null ? targets.hashCode() : 0);
    result = 31 * result + (rules != null ? rules.hashCode() : 0);
    result = 31 * result + (fallthrough != null ? fallthrough.hashCode() : 0);
    result = 31 * result + (offVariation != null ? offVariation.hashCode() : 0);
    result = 31 * result + (variations != null ? variations.hashCode() : 0);
    result = 31 * result + (deleted ? 1 : 0);
    return result;
  }

  static class EvalResult {
    private JsonElement value;
    private Set<FeatureRequestEvent> prerequisiteEvents;
    private Set<String> visitedFeatureKeys;

    private EvalResult(JsonElement value, Set<FeatureRequestEvent> prerequisiteEvents, Set<String> visitedFeatureKeys) {
      this.value = value;
      this.prerequisiteEvents = prerequisiteEvents;
      this.visitedFeatureKeys = visitedFeatureKeys;
    }

    JsonElement getValue() {
      return value;
    }

    Set<FeatureRequestEvent> getPrerequisiteEvents() {
      return prerequisiteEvents;
    }
  }
}