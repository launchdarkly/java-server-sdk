package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.Kind;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Prerequisite;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;
import com.launchdarkly.sdk.server.DataModel.Target;
import com.launchdarkly.sdk.server.DataModel.VariationOrRollout;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;
import com.launchdarkly.sdk.server.DataModelPreprocessing.ClausePreprocessed;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes;

import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

import static com.launchdarkly.sdk.server.EvaluatorBucketing.bucketUser;

/**
 * Encapsulates the feature flag evaluation logic. The Evaluator has no knowledge of the rest of the SDK environment;
 * if it needs to retrieve flags or segments that are referenced by a flag, it does so through a read-only interface
 * that is provided in the constructor. It also produces evaluation records (to be used in event data) as appropriate
 * for any referenced prerequisite flags.
 */
class Evaluator {
  //
  // IMPLEMENTATION NOTES ABOUT THIS FILE
  //
  // Flag evaluation is the hottest code path in the SDK; large applications may evaluate flags at a VERY high
  // volume, so every little bit of optimization we can achieve here could add up to quite a bit of overhead we
  // are not making the customer incur. Strategies that are used here for that purpose include:
  //
  // 1. Whenever possible, we are reusing precomputed instances of EvalResult; see DataModelPreprocessing and
  // EvaluatorHelpers.
  //
  // 2. If prerequisite evaluations happen as a side effect of an evaluation, rather than building and returning
  // a list of these, we deliver them one at a time via the PrerequisiteEvaluationSink callback mechanism.
  //
  // 3. If there's a piece of state that needs to be tracked across multiple methods during an evaluation, and
  // it's not feasible to just pass it as a method parameter, consider adding it as a field in the mutable
  // EvaluatorState object (which we will always have one of) rather than creating a new object to contain it.
  //
  // 4. Whenever possible, avoid using "for (variable: list)" here because it always creates an iterator object.
  // Instead, use the tedious old "get the size, iterate with a counter" approach.
  //
  // 5. Avoid using lambdas/closures here, because these generally cause a heap object to be allocated for
  // variables captured in the closure each time they are used.
  //
  
  private final static Logger logger = Loggers.EVALUATION;

  /**
   * This key cannot exist in LaunchDarkly because it contains invalid characters. We use it in tests as a way to
   * simulate an unexpected RuntimeException during flag evaluations. We check for it by reference equality, so
   * the tests must use this exact constant.
   */
  static final String INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION = "$ test error flag $";
  static final RuntimeException EXPECTED_EXCEPTION_FROM_INVALID_FLAG = new RuntimeException("deliberate test error");
  
  private final Getters getters;
  
  /**
   * An abstraction of getting flags or segments by key. This ensures that Evaluator cannot modify the data store,
   * and simplifies testing.
   */
  static interface Getters {
    FeatureFlag getFlag(String key);
    Segment getSegment(String key);
    BigSegmentStoreWrapper.BigSegmentsQueryResult getBigSegments(String key);
  }

  /**
   * An interface for the caller to receive information about prerequisite flags that were evaluated as a side
   * effect of evaluating a flag. Evaluator pushes information to this object to avoid the overhead of building
   * and returning lists of evaluation events.
   */
  static interface PrerequisiteEvaluationSink {
    void recordPrerequisiteEvaluation(
        FeatureFlag flag,
        FeatureFlag prereqOfFlag,
        LDUser user,
        EvalResult result
        );
  }
  
  /**
   * This object holds mutable state that Evaluator may need during an evaluation.
   */
  private static class EvaluatorState {
    private BigSegmentStoreTypes.Membership bigSegmentsMembership = null;
    private EvaluationReason.BigSegmentsStatus bigSegmentsStatus = null;
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
  EvalResult evaluate(FeatureFlag flag, LDUser user, PrerequisiteEvaluationSink prereqEvals) {
    if (flag.getKey() == INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION) {
      throw EXPECTED_EXCEPTION_FROM_INVALID_FLAG;
    }
    
    if (user == null || user.getKey() == null) {
      // this should have been prevented by LDClient.evaluateInternal
      logger.warn("Null user or null user key when evaluating flag \"{}\"; returning null", flag.getKey());
      return EvalResult.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED);
    }

    EvaluatorState state = new EvaluatorState();
    
    EvalResult result = evaluateInternal(flag, user, prereqEvals, state);

    if (state.bigSegmentsStatus != null) {
      return result.withReason(
          result.getReason().withBigSegmentsStatus(state.bigSegmentsStatus)
          );
    }
    return result;
  }

  private EvalResult evaluateInternal(FeatureFlag flag, LDUser user,
      PrerequisiteEvaluationSink prereqEvals, EvaluatorState state) {
    if (!flag.isOn()) {
      return EvaluatorHelpers.offResult(flag);
    }
    
    EvalResult prereqFailureResult = checkPrerequisites(flag, user, prereqEvals, state);
    if (prereqFailureResult != null) {
      return prereqFailureResult;
    }
    
    // Check to see if targets match
    List<Target> targets = flag.getTargets(); // guaranteed non-null
    int nTargets = targets.size();
    for (int i = 0; i < nTargets; i++) {
      Target target = targets.get(i);
      if (target.getValues().contains(user.getKey())) { // getValues() is guaranteed non-null
        return EvaluatorHelpers.targetMatchResult(flag, target);
      }
    }
    
    // Now walk through the rules and see if any match
    List<Rule> rules = flag.getRules(); // guaranteed non-null
    int nRules = rules.size();
    for (int i = 0; i < nRules; i++) {
      Rule rule = rules.get(i);
      if (ruleMatchesUser(flag, rule, user, state)) {
        return computeRuleMatch(flag, user, rule, i);
      }
    }
    // Walk through the fallthrough and see if it matches
    return getValueForVariationOrRollout(flag, flag.getFallthrough(), user,
        flag.preprocessed == null ? null : flag.preprocessed.fallthroughResults,
        EvaluationReason.fallthrough());
  }

  // Checks prerequisites if any; returns null if successful, or an EvalResult if we have to
  // short-circuit due to a prerequisite failure.
  private EvalResult checkPrerequisites(FeatureFlag flag, LDUser user,
      PrerequisiteEvaluationSink prereqEvals, EvaluatorState state) {
    List<Prerequisite> prerequisites = flag.getPrerequisites(); // guaranteed non-null
    int nPrerequisites = prerequisites.size();
    for (int i = 0; i < nPrerequisites; i++) {
      Prerequisite prereq = prerequisites.get(i);
      boolean prereqOk = true;
      FeatureFlag prereqFeatureFlag = getters.getFlag(prereq.getKey());
      if (prereqFeatureFlag == null) {
        logger.error("Could not retrieve prerequisite flag \"{}\" when evaluating \"{}\"", prereq.getKey(), flag.getKey());
        prereqOk = false;
      } else {
        EvalResult prereqEvalResult = evaluateInternal(prereqFeatureFlag, user, prereqEvals, state);
        // Note that if the prerequisite flag is off, we don't consider it a match no matter what its
        // off variation was. But we still need to evaluate it in order to generate an event.
        if (!prereqFeatureFlag.isOn() || prereqEvalResult.getVariationIndex() != prereq.getVariation()) {
          prereqOk = false;
        }
        if (prereqEvals != null) {
          prereqEvals.recordPrerequisiteEvaluation(prereqFeatureFlag, flag, user, prereqEvalResult);
        }
      }
      if (!prereqOk) {
        return EvaluatorHelpers.prerequisiteFailedResult(flag, prereq);
      }
    }
    return null;
  }

  private static EvalResult getValueForVariationOrRollout(
      FeatureFlag flag,
      VariationOrRollout vr,
      LDUser user,
      DataModelPreprocessing.EvalResultFactoryMultiVariations precomputedResults,
      EvaluationReason reason
      ) {
    int variation = -1;
    boolean inExperiment = false;
    Integer maybeVariation = vr.getVariation();
    if (maybeVariation != null) {
      variation = maybeVariation.intValue();
    } else {
      Rollout rollout = vr.getRollout();
      if (rollout != null && !rollout.getVariations().isEmpty()) {
        float bucket = bucketUser(rollout.getSeed(), user, flag.getKey(), rollout.getBucketBy(), flag.getSalt());
        float sum = 0F;
        List<WeightedVariation> variations = rollout.getVariations(); // guaranteed non-null
        int nVariations = variations.size();
        for (int i = 0; i < nVariations; i++) {
          WeightedVariation wv = variations.get(i);
          sum += (float) wv.getWeight() / 100000F;
          if (bucket < sum) {
            variation = wv.getVariation();
            inExperiment = vr.getRollout().isExperiment() && !wv.isUntracked();
            break;
          }
        }
        if (variation < 0) {
          // The user's bucket value was greater than or equal to the end of the last bucket. This could happen due
          // to a rounding error, or due to the fact that we are scaling to 100000 rather than 99999, or the flag
          // data could contain buckets that don't actually add up to 100000. Rather than returning an error in
          // this case (or changing the scaling, which would potentially change the results for *all* users), we
          // will simply put the user in the last bucket.
          WeightedVariation lastVariation = rollout.getVariations().get(rollout.getVariations().size() - 1);
          variation = lastVariation.getVariation();
          inExperiment = vr.getRollout().isExperiment() && !lastVariation.isUntracked();
        }
      }
    }
    
    if (variation < 0) {
      logger.error("Data inconsistency in feature flag \"{}\": variation/rollout object with no variation or rollout", flag.getKey());
      return EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG); 
    }
    // Normally, we will always have precomputedResults
    if (precomputedResults != null) {
      return precomputedResults.forVariation(variation, inExperiment);
    }
    // If for some reason we don't, synthesize an equivalent result
    return EvalResult.of(EvaluatorHelpers.evaluationDetailForVariation(
        flag, variation, inExperiment ? experimentize(reason) : reason));
  }

  private static EvaluationReason experimentize(EvaluationReason reason) {
    if (reason.getKind() == Kind.FALLTHROUGH) {
      return EvaluationReason.fallthrough(true);
    } else if (reason.getKind() == Kind.RULE_MATCH) {
     return EvaluationReason.ruleMatch(reason.getRuleIndex(), reason.getRuleId(), true);
    }
    return reason;
  }

  private boolean ruleMatchesUser(FeatureFlag flag, Rule rule, LDUser user, EvaluatorState state) {
    List<Clause> clauses = rule.getClauses(); // guaranteed non-null
    int nClauses = clauses.size();
    for (int i = 0; i < nClauses; i++) {
      Clause clause = clauses.get(i);
      if (!clauseMatchesUser(clause, user, state)) {
        return false;
      }
    }
    return true;
  }

  private boolean clauseMatchesUser(Clause clause, LDUser user, EvaluatorState state) {
    // In the case of a segment match operator, we check if the user is in any of the segments,
    // and possibly negate
    if (clause.getOp() == Operator.segmentMatch) {
      List<LDValue> values = clause.getValues(); // guaranteed non-null
      int nValues = values.size();
      for (int i = 0; i < nValues; i++) {
        LDValue clauseValue = values.get(i); 
        if (clauseValue.isString()) {
          Segment segment = getters.getSegment(clauseValue.stringValue());
          if (segment != null) {
            if (segmentMatchesUser(segment, user, state)) {
              return maybeNegate(clause, true);
            }
          }
        }
      }
      return maybeNegate(clause, false);
    }
    
    return clauseMatchesUserNoSegments(clause, user);
  }
  
  private boolean clauseMatchesUserNoSegments(Clause clause, LDUser user) {
    LDValue userValue = user.getAttribute(clause.getAttribute());
    if (userValue.isNull()) {
      return false;
    }

    if (userValue.getType() == LDValueType.ARRAY) {
      int nValues = userValue.size();
      for (int i = 0; i < nValues; i++) {
        LDValue value = userValue.get(i);
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
  
  static boolean clauseMatchAny(Clause clause, LDValue userValue) {
    Operator op = clause.getOp();
    if (op != null) {
      ClausePreprocessed preprocessed = clause.preprocessed;
      if (op == Operator.in) {
        // see if we have precomputed a Set for fast equality matching
        Set<LDValue> vs = preprocessed == null ? null : preprocessed.valuesSet;
        if (vs != null) {
          return vs.contains(userValue);
        }
      }
      List<LDValue> values = clause.getValues();
      List<ClausePreprocessed.ValueData> preprocessedValues =
          preprocessed == null ? null : preprocessed.valuesExtra;
      int n = values.size();
      for (int i = 0; i < n; i++) {
        // the preprocessed list, if present, will always have the same size as the values list
        ClausePreprocessed.ValueData p = preprocessedValues == null ? null : preprocessedValues.get(i);
        LDValue v = values.get(i);
        if (EvaluatorOperators.apply(op, userValue, v, p)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean maybeNegate(Clause clause, boolean b) {
    return clause.isNegate() ? !b : b;
  }
  
  private boolean segmentMatchesUser(Segment segment, LDUser user, EvaluatorState state) {
    String userKey = user.getKey(); // we've already verified that the key is non-null at the top of evaluate()
    if (segment.isUnbounded()) {
      if (segment.getGeneration() == null) {
        // Big Segment queries can only be done if the generation is known. If it's unset, that
        // probably means the data store was populated by an older SDK that doesn't know about the
        // generation property and therefore dropped it from the JSON data. We'll treat that as a
        // "not configured" condition.
        state.bigSegmentsStatus = EvaluationReason.BigSegmentsStatus.NOT_CONFIGURED;
        return false;
      }

      // Even if multiple Big Segments are referenced within a single flag evaluation, we only need
      // to do this query once, since it returns *all* of the user's segment memberships.
      if (state.bigSegmentsStatus == null) {
        BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = getters.getBigSegments(user.getKey());
        if (queryResult == null) {
          // The SDK hasn't been configured to be able to use big segments
          state.bigSegmentsStatus = EvaluationReason.BigSegmentsStatus.NOT_CONFIGURED;
        } else {
          state.bigSegmentsStatus = queryResult.status;
          state.bigSegmentsMembership = queryResult.membership;
        }
      }
      Boolean membership = state.bigSegmentsMembership == null ?
          null : state.bigSegmentsMembership.checkMembership(makeBigSegmentRef(segment));
      if (membership != null) {
        return membership;
      }
    } else {
      if (segment.getIncluded().contains(userKey)) { // getIncluded(), getExcluded(), and getRules() are guaranteed non-null
        return true;
      }
      if (segment.getExcluded().contains(userKey)) {
        return false;
      }
    }
    List<SegmentRule> rules = segment.getRules(); // guaranteed non-null
    int nRules = rules.size();
    for (int i = 0; i < nRules; i++) {
      SegmentRule rule = rules.get(i);
      if (segmentRuleMatchesUser(rule, user, segment.getKey(), segment.getSalt())) {
        return true;
      }
    }
    return false;
  }

  private boolean segmentRuleMatchesUser(SegmentRule segmentRule, LDUser user, String segmentKey, String salt) {
    List<Clause> clauses = segmentRule.getClauses(); // guaranteed non-null
    int nClauses = clauses.size();
    for (int i = 0; i < nClauses; i++) {
      Clause c = clauses.get(i);
      if (!clauseMatchesUserNoSegments(c, user)) {
        return false;
      }
    }
    
    // If the Weight is absent, this rule matches
    if (segmentRule.getWeight() == null) {
      return true;
    }
    
    // All of the clauses are met. See if the user buckets in
    double bucket = EvaluatorBucketing.bucketUser(null, user, segmentKey, segmentRule.getBucketBy(), salt);
    double weight = (double)segmentRule.getWeight() / 100000.0;
    return bucket < weight;
  }

  private static EvalResult computeRuleMatch(FeatureFlag flag, LDUser user, Rule rule, int ruleIndex) {
    if (rule.preprocessed != null) {
      return getValueForVariationOrRollout(flag, rule, user, rule.preprocessed.allPossibleResults, null);
    }
    EvaluationReason reason = EvaluationReason.ruleMatch(ruleIndex, rule.getId());
    return getValueForVariationOrRollout(flag, rule, user, null, reason);
  }
  
  static String makeBigSegmentRef(Segment segment) {
    return String.format("%s.g%d", segment.getKey(), segment.getGeneration());
  }
}
