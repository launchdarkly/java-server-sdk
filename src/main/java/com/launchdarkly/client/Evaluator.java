package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.launchdarkly.client.interfaces.Event;
import com.launchdarkly.client.value.LDValue;
import com.launchdarkly.client.value.LDValueType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the feature flag evaluation logic. The Evaluator has no knowledge of the rest of the SDK environment;
 * if it needs to retrieve flags or segments that are referenced by a flag, it does so through a read-only interface
 * that is provided in the constructor. It also produces feature requests as appropriate for any referenced prerequisite
 * flags, but does not send them.
 */
class Evaluator {
  private final static Logger logger = LoggerFactory.getLogger(Evaluator.class);
  
  private final Getters getters;
  
  /**
   * An abstraction of getting flags or segments by key. This ensures that Evaluator cannot modify the data store,
   * and simplifies testing.
   */
  static interface Getters {
    DataModel.FeatureFlag getFlag(String key);
    DataModel.Segment getSegment(String key);
  }

  /**
   * Internal container for the results of an evaluation. This consists of the same information that is in an
   * {@link EvaluationDetail}, plus a list of any feature request events generated by prerequisite flags.
   * 
   * Unlike all the other simple data containers in the SDK, this is mutable. The reason is that flag evaluations
   * may be done very frequently and we would like to minimize the amount of heap churn from intermediate objects,
   * and Java does not support multiple return values as Go does, or value types as C# does.
   * 
   * We never expose an EvalResult to application code and we never preserve a reference to it outside of a single
   * xxxVariation() or xxxVariationDetail() call, so the risks from mutability are minimal. The only setter method
   * that is accessible from outside of the Evaluator class is setValue(), which is exposed so that LDClient can
   * replace null values with default values,
   */
  static class EvalResult {
    private LDValue value = LDValue.ofNull();
    private Integer variationIndex = null;
    private EvaluationReason reason = null;
    private List<Event.FeatureRequest> prerequisiteEvents;
    
    public EvalResult(LDValue value, Integer variationIndex, EvaluationReason reason) {
      this.value = value;
      this.variationIndex = variationIndex;
      this.reason = reason;
    }
    
    public static EvalResult error(EvaluationReason.ErrorKind errorKind) {
      return new EvalResult(LDValue.ofNull(), null, EvaluationReason.error(errorKind));
    }
    
    LDValue getValue() {
      return LDValue.normalize(value);
    }
    
    void setValue(LDValue value) {
      this.value = value;
    }
    
    Integer getVariationIndex() {
      return variationIndex;
    }
    
    boolean isDefault() {
      return variationIndex == null;
    }
    
    EvaluationReason getReason() {
      return reason;
    }
    
    EvaluationDetail<LDValue> getDetails() {
      return EvaluationDetail.fromValue(LDValue.normalize(value), variationIndex, reason);
    }
    
    Iterable<Event.FeatureRequest> getPrerequisiteEvents() {
      return prerequisiteEvents == null ? ImmutableList.<Event.FeatureRequest>of() : prerequisiteEvents;
    }
    
    private void setPrerequisiteEvents(List<Event.FeatureRequest> prerequisiteEvents) {
      this.prerequisiteEvents = prerequisiteEvents;
    }
  }
  
  Evaluator(Getters getters) {
    this.getters = getters;
  }

  /**
   * The client's entry point for evaluating a flag. No other Evaluator methods should be exposed.
   * 
   * @param flag an existing feature flag; any other referenced flags or segments will be queried via {@link Getters}
   * @param user the user to evaluate against
   * @param eventFactory produces feature request events
   * @return an {@link EvalResult}
   */
  EvalResult evaluate(DataModel.FeatureFlag flag, LDUser user, EventFactory eventFactory) {
    if (user == null || user.getKey() == null) {
      // this should have been prevented by LDClient.evaluateInternal
      logger.warn("Null user or null user key when evaluating flag \"{}\"; returning null", flag.getKey());
      return new EvalResult(null, null, EvaluationReason.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED));
    }

    // If the flag doesn't have any prerequisites (which most flags don't) then it cannot generate any feature
    // request events for prerequisites and we can skip allocating a List.
    List<Event.FeatureRequest> prerequisiteEvents = (flag.getPrerequisites() == null || flag.getPrerequisites().isEmpty()) ?
         null : new ArrayList<Event.FeatureRequest>();
    EvalResult result = evaluateInternal(flag, user, eventFactory, prerequisiteEvents);
    if (prerequisiteEvents != null) {
      result.setPrerequisiteEvents(prerequisiteEvents);
    }
    return result;
  }

  private EvalResult evaluateInternal(DataModel.FeatureFlag flag, LDUser user, EventFactory eventFactory,
      List<Event.FeatureRequest> eventsOut) {
    if (!flag.isOn()) {
      return getOffValue(flag, EvaluationReason.off());
    }
    
    EvaluationReason prereqFailureReason = checkPrerequisites(flag, user, eventFactory, eventsOut);
    if (prereqFailureReason != null) {
      return getOffValue(flag, prereqFailureReason);
    }
    
    // Check to see if targets match
    List<DataModel.Target> targets = flag.getTargets();
    if (targets != null) {
      for (DataModel.Target target: targets) {
        for (String v : target.getValues()) {
          if (v.equals(user.getKey().stringValue())) {
            return getVariation(flag, target.getVariation(), EvaluationReason.targetMatch());
          }
        }
      }
    }
    // Now walk through the rules and see if any match
    List<DataModel.Rule> rules = flag.getRules();
    if (rules != null) {
      for (int i = 0; i < rules.size(); i++) {
        DataModel.Rule rule = rules.get(i);
        if (ruleMatchesUser(flag, rule, user)) {
          EvaluationReason.RuleMatch precomputedReason = rule.getRuleMatchReason();
          EvaluationReason.RuleMatch reason = precomputedReason != null ? precomputedReason : EvaluationReason.ruleMatch(i, rule.getId());
          return getValueForVariationOrRollout(flag, rule, user, reason);
        }
      }
    }
    // Walk through the fallthrough and see if it matches
    return getValueForVariationOrRollout(flag, flag.getFallthrough(), user, EvaluationReason.fallthrough());
  }

  // Checks prerequisites if any; returns null if successful, or an EvaluationReason if we have to
  // short-circuit due to a prerequisite failure.
  private EvaluationReason checkPrerequisites(DataModel.FeatureFlag flag, LDUser user, EventFactory eventFactory,
      List<Event.FeatureRequest> eventsOut) {
    List<DataModel.Prerequisite> prerequisites = flag.getPrerequisites();
    if (prerequisites == null) {
      return null;
    }
    for (DataModel.Prerequisite prereq: prerequisites) {
      boolean prereqOk = true;
      DataModel.FeatureFlag prereqFeatureFlag = getters.getFlag(prereq.getKey());
      if (prereqFeatureFlag == null) {
        logger.error("Could not retrieve prerequisite flag \"{}\" when evaluating \"{}\"", prereq.getKey(), flag.getKey());
        prereqOk = false;
      } else {
        EvalResult prereqEvalResult = evaluateInternal(prereqFeatureFlag, user, eventFactory, eventsOut);
        // Note that if the prerequisite flag is off, we don't consider it a match no matter what its
        // off variation was. But we still need to evaluate it in order to generate an event.
        if (!prereqFeatureFlag.isOn() || prereqEvalResult == null || prereqEvalResult.getVariationIndex() != prereq.getVariation()) {
          prereqOk = false;
        }
        if (eventsOut != null) {
          eventsOut.add(eventFactory.newPrerequisiteFeatureRequestEvent(prereqFeatureFlag, user, prereqEvalResult, flag));
        }
      }
      if (!prereqOk) {
        EvaluationReason.PrerequisiteFailed precomputedReason = prereq.getPrerequisiteFailedReason();
        return precomputedReason != null ? precomputedReason : EvaluationReason.prerequisiteFailed(prereq.getKey());
      }
    }
    return null;
  }

  private EvalResult getVariation(DataModel.FeatureFlag flag, int variation, EvaluationReason reason) {
    List<LDValue> variations = flag.getVariations();
    if (variation < 0 || variation >= variations.size()) {
      logger.error("Data inconsistency in feature flag \"{}\": invalid variation index", flag.getKey());
      return EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG);
    } else {
      return new EvalResult(variations.get(variation), variation, reason);
    }
  }

  private EvalResult getOffValue(DataModel.FeatureFlag flag, EvaluationReason reason) {
    Integer offVariation = flag.getOffVariation();
    if (offVariation == null) { // off variation unspecified - return default value
      return new EvalResult(null, null, reason);
    } else {
      return getVariation(flag, offVariation, reason);
    }
  }
  
  private EvalResult getValueForVariationOrRollout(DataModel.FeatureFlag flag, DataModel.VariationOrRollout vr, LDUser user, EvaluationReason reason) {
    Integer index = EvaluatorBucketing.variationIndexForUser(vr, user, flag.getKey(), flag.getSalt());
    if (index == null) {
      logger.error("Data inconsistency in feature flag \"{}\": variation/rollout object with no variation or rollout", flag.getKey());
      return EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG); 
    } else {
      return getVariation(flag, index, reason);
    }
  }

  private boolean ruleMatchesUser(DataModel.FeatureFlag flag, DataModel.Rule rule, LDUser user) {
    Iterable<DataModel.Clause> clauses = rule.getClauses();
    if (clauses != null) {
      for (DataModel.Clause clause: clauses) {
        if (!clauseMatchesUser(clause, user)) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean clauseMatchesUser(DataModel.Clause clause, LDUser user) {
    // In the case of a segment match operator, we check if the user is in any of the segments,
    // and possibly negate
    if (clause.getOp() == DataModel.Operator.segmentMatch) {
      for (LDValue j: clause.getValues()) {
        if (j.isString()) {
          DataModel.Segment segment = getters.getSegment(j.stringValue());
          if (segment != null) {
            if (segmentMatchesUser(segment, user)) {
              return maybeNegate(clause, true);
            }
          }
        }
      }
      return maybeNegate(clause, false);
    }
    
    return clauseMatchesUserNoSegments(clause, user);
  }
  
  private boolean clauseMatchesUserNoSegments(DataModel.Clause clause, LDUser user) {
    LDValue userValue = user.getValueForEvaluation(clause.getAttribute());
    if (userValue.isNull()) {
      return false;
    }

    if (userValue.getType() == LDValueType.ARRAY) {
      for (LDValue value: userValue.values()) {
        if (value.getType() == LDValueType.ARRAY || value.getType() == LDValueType.OBJECT) {
          logger.error("Invalid custom attribute value in user object for user key \"{}\": {}", user.getKey(), value);
          return false;
        }
        if (clauseMatchAny(clause, value)) {
          return maybeNegate(clause, true);
        }
      }
      return maybeNegate(clause, false);
    } else if (userValue.getType() != LDValueType.OBJECT) {
      return maybeNegate(clause, clauseMatchAny(clause, userValue));
    }
    logger.warn("Got unexpected user attribute type \"{}\" for user key \"{}\" and attribute \"{}\"",
        userValue.getType(), user.getKey(), clause.getAttribute());
    return false;
  }
  
  private boolean clauseMatchAny(DataModel.Clause clause, LDValue userValue) {
    DataModel.Operator op = clause.getOp();
    if (op != null) {
      for (LDValue v : clause.getValues()) {
        if (EvaluatorOperators.apply(op, userValue, v)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean maybeNegate(DataModel.Clause clause, boolean b) {
    return clause.isNegate() ? !b : b;
  }
  
  private boolean segmentMatchesUser(DataModel.Segment segment, LDUser user) {
    String userKey = user.getKeyAsString();
    if (userKey == null) {
      return false;
    }
    if (Iterables.contains(segment.getIncluded(), userKey)) {
      return true;
    }
    if (Iterables.contains(segment.getExcluded(), userKey)) {
      return false;
    }
    for (DataModel.SegmentRule rule: segment.getRules()) {
      if (segmentRuleMatchesUser(rule, user, segment.getKey(), segment.getSalt())) {
        return true;
      }
    }
    return false;
  }

  private boolean segmentRuleMatchesUser(DataModel.SegmentRule segmentRule, LDUser user, String segmentKey, String salt) {
    for (DataModel.Clause c: segmentRule.getClauses()) {
      if (!clauseMatchesUserNoSegments(c, user)) {
        return false;
      }
    }
    
    // If the Weight is absent, this rule matches
    if (segmentRule.getWeight() == null) {
      return true;
    }
    
    // All of the clauses are met. See if the user buckets in
    double bucket = EvaluatorBucketing.bucketUser(user, segmentKey, segmentRule.getBucketBy(), salt);
    double weight = (double)segmentRule.getWeight() / 100000.0;
    return bucket < weight;
  }
}
