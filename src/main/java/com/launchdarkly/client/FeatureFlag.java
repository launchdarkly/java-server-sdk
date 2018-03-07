package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    List<FeatureRequestEvent> prereqEvents = new ArrayList<>();

    if (user == null || user.getKey() == null) {
      logger.warn("Null user or null user key when evaluating flag: " + key + "; returning null");
      return new EvalResult(null, prereqEvents);
    }

    if (isOn()) {
      VariationAndValue result = evaluate(user, featureStore, prereqEvents, eventFactory);
      if (result != null) {
        return new EvalResult(result, prereqEvents);
      }
    }
    return new EvalResult(new VariationAndValue(offVariation, getOffVariationValue()), prereqEvents);
  }

  // Returning either a JsonElement or null indicating prereq failure/error.
  private VariationAndValue evaluate(LDUser user, FeatureStore featureStore, List<FeatureRequestEvent> events,
      EventFactory eventFactory) throws EvaluationException {
    boolean prereqOk = true;
    if (prerequisites != null) {
      for (Prerequisite prereq : prerequisites) {
        FeatureFlag prereqFeatureFlag = featureStore.get(FEATURES, prereq.getKey());
        VariationAndValue prereqEvalResult = null;
        if (prereqFeatureFlag == null) {
          logger.error("Could not retrieve prerequisite flag: " + prereq.getKey() + " when evaluating: " + key);
          return null;
        } else if (prereqFeatureFlag.isOn()) {
          prereqEvalResult = prereqFeatureFlag.evaluate(user, featureStore, events, eventFactory);
          if (prereqEvalResult == null || prereqEvalResult.getVariation() != prereq.getVariation()) {
            prereqOk = false;
          }
        } else {
          prereqOk = false;
        }
        //We don't short circuit and also send events for each prereq.
        events.add(eventFactory.newPrerequisiteFeatureRequestEvent(prereqFeatureFlag, user, prereqEvalResult, this));
      }
    }
    if (prereqOk) {
      Integer index = evaluateIndex(user, featureStore);
      return new VariationAndValue(index, getVariation(index));
    }
    return null;
  }

  private Integer evaluateIndex(LDUser user, FeatureStore store) {
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
        if (rule.matchesUser(store, user)) {
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

  Integer getOffVariation() { return offVariation; }

  static class VariationAndValue {
    private final Integer variation;
    private final JsonElement value;

    VariationAndValue(Integer variation, JsonElement value) {
      this.variation = variation;
      this.value = value;
    }
    
    Integer getVariation() {
      return variation;
    }
    
    JsonElement getValue() {
      return value;
    }
  }
  
  static class EvalResult {
    private final VariationAndValue result;
    private final List<FeatureRequestEvent> prerequisiteEvents;

    private EvalResult(VariationAndValue result, List<FeatureRequestEvent> prerequisiteEvents) {
      this.result = result;
      this.prerequisiteEvents = prerequisiteEvents;
    }

    VariationAndValue getResult() {
      return result;
    }

    List<FeatureRequestEvent> getPrerequisiteEvents() {
      return prerequisiteEvents;
    }
  }
}
