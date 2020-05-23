package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.interfaces.Event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;

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
    private int variationIndex = NO_VARIATION;
    private EvaluationReason reason = null;
    private List<Event.FeatureRequest> prerequisiteEvents;
    
    public EvalResult(LDValue value, int variationIndex, EvaluationReason reason) {
      this.value = value;
      this.variationIndex = variationIndex;
      this.reason = reason;
    }
    
    public static EvalResult error(EvaluationReason.ErrorKind errorKind) {
      return new EvalResult(LDValue.ofNull(), NO_VARIATION, EvaluationReason.error(errorKind));
    }
    
    LDValue getValue() {
      return LDValue.normalize(value);
    }
    
    void setValue(LDValue value) {
      this.value = value;
    }
    
    int getVariationIndex() {
      return variationIndex;
    }
    
    boolean isDefault() {
      return variationIndex < 0;
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
   * @return an {@link EvalResult} - guaranteed non-null
   */
  EvalResult evaluate(DataModel.FeatureFlag flag, LDUser user, EventFactory eventFactory) {
    if (user == null || user.getKey() == null) {
      // this should have been prevented by LDClient.evaluateInternal
      logger.warn("Null user or null user key when evaluating flag \"{}\"; returning null", flag.getKey());
      return new EvalResult(null, NO_VARIATION, EvaluationReason.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED));
    }

    // If the flag doesn't have any prerequisites (which most flags don't) then it cannot generate any feature
    // request events for prerequisites and we can skip allocating a List.
    List<Event.FeatureRequest> prerequisiteEvents = flag.getPrerequisites().isEmpty() ?
         null : new ArrayList<Event.FeatureRequest>(); // note, getPrerequisites() is guaranteed non-null
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
    for (DataModel.Target target: flag.getTargets()) { // getTargets() and getValues() are guaranteed non-null
      if (target.getValues().contains(user.getKey())) {
        return getVariation(flag, target.getVariation(), EvaluationReason.targetMatch());
      }
    }
    // Now walk through the rules and see if any match
    List<DataModel.Rule> rules = flag.getRules(); // guaranteed non-null
    for (int i = 0; i < rules.size(); i++) {
      DataModel.Rule rule = rules.get(i);
      if (ruleMatchesUser(flag, rule, user)) {
        EvaluationReason precomputedReason = rule.getRuleMatchReason();
        EvaluationReason reason = precomputedReason != null ? precomputedReason : EvaluationReason.ruleMatch(i, rule.getId());
        return getValueForVariationOrRollout(flag, rule, user, reason);
      }
    }
    // Walk through the fallthrough and see if it matches
    return getValueForVariationOrRollout(flag, flag.getFallthrough(), user, EvaluationReason.fallthrough());
  }

  // Checks prerequisites if any; returns null if successful, or an EvaluationReason if we have to
  // short-circuit due to a prerequisite failure.
  private EvaluationReason checkPrerequisites(DataModel.FeatureFlag flag, LDUser user, EventFactory eventFactory,
      List<Event.FeatureRequest> eventsOut) {
    for (DataModel.Prerequisite prereq: flag.getPrerequisites()) { // getPrerequisites() is guaranteed non-null
      boolean prereqOk = true;
      DataModel.FeatureFlag prereqFeatureFlag = getters.getFlag(prereq.getKey());
      if (prereqFeatureFlag == null) {
        logger.error("Could not retrieve prerequisite flag \"{}\" when evaluating \"{}\"", prereq.getKey(), flag.getKey());
        prereqOk = false;
      } else {
        EvalResult prereqEvalResult = evaluateInternal(prereqFeatureFlag, user, eventFactory, eventsOut);
        // Note that if the prerequisite flag is off, we don't consider it a match no matter what its
        // off variation was. But we still need to evaluate it in order to generate an event.
        if (!prereqFeatureFlag.isOn() || prereqEvalResult.getVariationIndex() != prereq.getVariation()) {
          prereqOk = false;
        }
        // COVERAGE: currently eventsOut is never null because we preallocate the list in evaluate() if there are any prereqs
        if (eventsOut != null) {
          eventsOut.add(eventFactory.newPrerequisiteFeatureRequestEvent(prereqFeatureFlag, user, prereqEvalResult, flag));
        }
      }
      if (!prereqOk) {
        EvaluationReason precomputedReason = prereq.getPrerequisiteFailedReason();
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
      return new EvalResult(null, NO_VARIATION, reason);
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
    for (DataModel.Clause clause: rule.getClauses()) { // getClauses() is guaranteed non-null
      if (!clauseMatchesUser(clause, user)) {
        return false;
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
    LDValue userValue = user.getAttribute(clause.getAttribute());
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
  
  static boolean clauseMatchAny(DataModel.Clause clause, LDValue userValue) {
    DataModel.Operator op = clause.getOp();
    if (op != null) {
      EvaluatorPreprocessing.ClauseExtra preprocessed = clause.getPreprocessed();
      if (op == DataModel.Operator.in) {
        // see if we have precomputed a Set for fast equality matching
        Set<LDValue> vs = preprocessed == null ? null : preprocessed.valuesSet;
        if (vs != null) {
          return vs.contains(userValue);
        }
      }
      List<LDValue> values = clause.getValues();
      List<EvaluatorPreprocessing.ClauseExtra.ValueExtra> preprocessedValues =
          preprocessed == null ? null : preprocessed.valuesExtra;
      int n = values.size();
      for (int i = 0; i < n; i++) {
        // the preprocessed list, if present, will always have the same size as the values list
        EvaluatorPreprocessing.ClauseExtra.ValueExtra p = preprocessedValues == null ? null : preprocessedValues.get(i);
        LDValue v = values.get(i);
        if (EvaluatorOperators.apply(op, userValue, v, p)) {
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
    String userKey = user.getKey(); // we've already verified that the key is non-null at the top of evaluate()
    if (segment.getIncluded().contains(userKey)) { // getIncluded(), getExcluded(), and getRules() are guaranteed non-null
      return true;
    }
    if (segment.getExcluded().contains(userKey)) {
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
