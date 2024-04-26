package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.EvaluationReason.Kind;
import com.launchdarkly.sdk.LDContext;
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
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.launchdarkly.sdk.server.EvaluatorBucketing.computeBucketValue;
import static com.launchdarkly.sdk.server.EvaluatorHelpers.contextKeyIsInTargetList;
import static com.launchdarkly.sdk.server.EvaluatorHelpers.contextKeyIsInTargetLists;
import static com.launchdarkly.sdk.server.EvaluatorHelpers.matchClauseByKind;
import static com.launchdarkly.sdk.server.EvaluatorHelpers.matchClauseWithoutSegments;
import static com.launchdarkly.sdk.server.EvaluatorHelpers.maybeNegate;

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

  /**
   * This key cannot exist in LaunchDarkly because it contains invalid characters. We use it in tests as a way to
   * simulate an unexpected RuntimeException during flag evaluations. We check for it by reference equality, so
   * the tests must use this exact constant.
   */
  static final String INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION = "$ test error flag $";
  static final RuntimeException EXPECTED_EXCEPTION_FROM_INVALID_FLAG = new RuntimeException("deliberate test error");

  private final Getters getters;
  private final LDLogger logger;

  /**
   * An abstraction of getting flags or segments by key. This ensures that Evaluator cannot modify the data store,
   * and simplifies testing.
   */
  static interface Getters {

    /**
     * @param key of the flag to get
     * @return the flag, or null if a flag with the given key doesn't exist
     */
    @Nullable
    FeatureFlag getFlag(String key);

    /**
     * @param key of the segment to get
     * @return the segment, or null if a segment with the given key doesn't exist.
     */
    @Nullable
    Segment getSegment(String key);

    BigSegmentStoreWrapper.BigSegmentsQueryResult getBigSegments(String key);
  }

  /**
   * Represents errors that should terminate evaluation, for situations where it's simpler to use throw/catch
   * than to return an error result back up a call chain.
   */
  @SuppressWarnings("serial")
  static class EvaluationException extends RuntimeException {
    final ErrorKind errorKind;

    EvaluationException(ErrorKind errorKind, String message) {
      this.errorKind = errorKind;
    }
  }

  /**
   * This object holds mutable state that Evaluator may need during an evaluation.
   */
  private static class EvaluatorState {
    private Map<String, BigSegmentStoreTypes.Membership> bigSegmentsMembership = null;
    private EvaluationReason.BigSegmentsStatus bigSegmentsStatus = null;
    private FeatureFlag originalFlag = null;
    private List<String> prerequisiteStack = null;
    private List<String> segmentStack = null;
  }

  Evaluator(Getters getters, LDLogger logger) {
    this.getters = getters;
    this.logger = logger;
  }

  /**
   * The client's entry point for evaluating a flag. No other Evaluator methods should be exposed.
   *
   * @param flag an existing feature flag; any other referenced flags or segments will be queried via {@link Getters}
   * @param context the evaluation context
   * @param recorder records information as evaluation runs
   * @return an {@link EvalResult} - guaranteed non-null
   */
  EvalResult evaluate(FeatureFlag flag, LDContext context, @Nonnull EvaluationRecorder recorder) {
    if (flag.getKey() == INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION) {
      throw EXPECTED_EXCEPTION_FROM_INVALID_FLAG;
    }

    EvaluatorState state = new EvaluatorState();
    state.originalFlag = flag;

    try {
      EvalResult result = evaluateInternal(flag, context, recorder, state);

      if (state.bigSegmentsStatus != null) {
        return result.withReason(
            result.getReason().withBigSegmentsStatus(state.bigSegmentsStatus)
        );
      }
      return result;
    } catch (EvaluationException e) {
      logger.error("Could not evaluate flag \"{}\": {}", flag.getKey(), e.getMessage());
      return EvalResult.error(e.errorKind);
    }
  }

  private EvalResult evaluateInternal(FeatureFlag flag, LDContext context, @Nonnull EvaluationRecorder recorder, EvaluatorState state) {
    if (!flag.isOn()) {
      return EvaluatorHelpers.offResult(flag);
    }

    EvalResult prereqFailureResult = checkPrerequisites(flag, context, recorder, state);
    if (prereqFailureResult != null) {
      return prereqFailureResult;
    }

    // Check to see if targets match
    EvalResult targetMatchResult = checkTargets(flag, context);
    if (targetMatchResult != null) {
      return targetMatchResult;
    }

    // Now walk through the rules and see if any match
    List<Rule> rules = flag.getRules(); // guaranteed non-null
    int nRules = rules.size();
    for (int i = 0; i < nRules; i++) {
      Rule rule = rules.get(i);
      if (ruleMatchesContext(flag, rule, context, state)) {
        return computeRuleMatch(flag, context, rule, i);
      }
    }
    // Walk through the fallthrough and see if it matches
    return getValueForVariationOrRollout(flag, flag.getFallthrough(), context,
        flag.preprocessed == null ? null : flag.preprocessed.fallthroughResults,
        EvaluationReason.fallthrough());
  }

  // Checks prerequisites if any; returns null if successful, or an EvalResult if we have to
  // short-circuit due to a prerequisite failure.
  private EvalResult checkPrerequisites(FeatureFlag flag, LDContext context, @Nonnull EvaluationRecorder recorder, EvaluatorState state) {
    List<Prerequisite> prerequisites = flag.getPrerequisites(); // guaranteed non-null
    int nPrerequisites = prerequisites.size();
    if (nPrerequisites == 0) {
      return null;
    }

    try {
      // We use the state object to guard against circular references in prerequisites. To avoid
      // the overhead of creating the state.prerequisiteStack list in the most common case where
      // there's only a single level prerequisites, we treat state.originalFlag as the first
      // element in the stack.
      if (flag != state.originalFlag) {
        if (state.prerequisiteStack == null) {
          state.prerequisiteStack = new ArrayList<>();
        }
        state.prerequisiteStack.add(flag.getKey());
      }

      for (int i = 0; i < nPrerequisites; i++) {
        Prerequisite prereq = prerequisites.get(i);
        String prereqKey = prereq.getKey();

        if (prereqKey.equals(state.originalFlag.getKey()) ||
            (flag != state.originalFlag && prereqKey.equals(flag.getKey())) ||
            (state.prerequisiteStack != null && state.prerequisiteStack.contains(prereqKey))) {
          throw new EvaluationException(ErrorKind.MALFORMED_FLAG,
              "prerequisite relationship to \"" + prereqKey + "\" caused a circular reference;" +
                  " this is probably a temporary condition due to an incomplete update");
        }

        boolean prereqOk = true;
        FeatureFlag prereqFeatureFlag = getters.getFlag(prereq.getKey());
        if (prereqFeatureFlag == null) {
          logger.error("Could not retrieve prerequisite flag \"{}\" when evaluating \"{}\"", prereq.getKey(), flag.getKey());
          prereqOk = false;
        } else {
          EvalResult prereqEvalResult = evaluateInternal(prereqFeatureFlag, context, recorder, state);
          // Note that if the prerequisite flag is off, we don't consider it a match no matter what its
          // off variation was. But we still need to evaluate it in order to generate an event.
          if (!prereqFeatureFlag.isOn() || prereqEvalResult.getVariationIndex() != prereq.getVariation()) {
            prereqOk = false;
          }
          recorder.recordPrerequisiteEvaluation(prereqFeatureFlag, flag, context, prereqEvalResult);
        }
        if (!prereqOk) {
          return EvaluatorHelpers.prerequisiteFailedResult(flag, prereq);
        }
      }
      return null; // all prerequisites were satisfied
    } finally {
      if (state.prerequisiteStack != null && !state.prerequisiteStack.isEmpty()) {
        state.prerequisiteStack.remove(state.prerequisiteStack.size() - 1);
      }
    }
  }

  private static EvalResult checkTargets(
      FeatureFlag flag,
      LDContext context
  ) {
    List<Target> contextTargets = flag.getContextTargets(); // guaranteed non-null
    List<Target> userTargets = flag.getTargets(); // guaranteed non-null
    int nContextTargets = contextTargets.size();
    int nUserTargets = userTargets.size();

    if (nContextTargets == 0) {
      // old-style data has only targets for users
      if (nUserTargets != 0) {
        LDContext userContext = context.getIndividualContext(ContextKind.DEFAULT);
        if (userContext != null) {
          for (int i = 0; i < nUserTargets; i++) {
            Target t = userTargets.get(i);
            if (t.getValues().contains(userContext.getKey())) { // getValues() is guaranteed non-null
              return EvaluatorHelpers.targetMatchResult(flag, t);
            }
          }
        }
      }
      return null;
    }

    // new-style data has ContextTargets, which may include placeholders for user targets that are in Targets
    for (int i = 0; i < nContextTargets; i++) {
      Target t = contextTargets.get(i);
      if (t.getContextKind() == null || t.getContextKind().isDefault()) {
        LDContext userContext = context.getIndividualContext(ContextKind.DEFAULT);
        if (userContext == null) {
          continue;
        }
        for (int j = 0; j < nUserTargets; j++) {
          Target ut = userTargets.get(j);
          if (ut.getVariation() == t.getVariation()) {
            if (ut.getValues().contains(userContext.getKey())) {
              return EvaluatorHelpers.targetMatchResult(flag, t);
            }
            break;
          }
        }
      } else {
        if (contextKeyIsInTargetList(context, t.getContextKind(), t.getValues())) {
          return EvaluatorHelpers.targetMatchResult(flag, t);
        }
      }
    }
    return null;
  }

  private EvalResult getValueForVariationOrRollout(
      FeatureFlag flag,
      VariationOrRollout vr,
      LDContext context,
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
        float bucket = computeBucketValue(
            rollout.isExperiment(),
            rollout.getSeed(),
            context,
            rollout.getContextKind(),
            flag.getKey(),
            rollout.getBucketBy(),
            flag.getSalt()
        );
        boolean contextWasFound = bucket >= 0; // see comment on computeBucketValue
        float sum = 0F;
        List<WeightedVariation> variations = rollout.getVariations(); // guaranteed non-null
        int nVariations = variations.size();
        for (int i = 0; i < nVariations; i++) {
          WeightedVariation wv = variations.get(i);
          sum += (float) wv.getWeight() / 100000F;
          if (bucket < sum) {
            variation = wv.getVariation();
            inExperiment = vr.getRollout().isExperiment() && !wv.isUntracked() && contextWasFound;
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

  private boolean ruleMatchesContext(FeatureFlag flag, Rule rule, LDContext context, EvaluatorState state) {
    List<Clause> clauses = rule.getClauses(); // guaranteed non-null
    int nClauses = clauses.size();
    for (int i = 0; i < nClauses; i++) {
      Clause clause = clauses.get(i);
      if (!clauseMatchesContext(clause, context, state)) {
        return false;
      }
    }
    return true;
  }

  private boolean clauseMatchesContext(Clause clause, LDContext context, EvaluatorState state) {
    if (clause.getOp() == Operator.segmentMatch) {
      return maybeNegate(clause, matchAnySegment(clause.getValues(), context, state));
    }
    AttributeRef attr = clause.getAttribute();
    if (attr == null) {
      throw new EvaluationException(ErrorKind.MALFORMED_FLAG, "rule clause did not specify an attribute");
    }
    if (!attr.isValid()) {
      throw new EvaluationException(ErrorKind.MALFORMED_FLAG,
          "invalid attribute reference \"" + attr.getError() + "\"");
    }
    if (attr.getDepth() == 1 && attr.getComponent(0).equals("kind")) {
      return maybeNegate(clause, matchClauseByKind(clause, context));
    }
    LDContext actualContext = context.getIndividualContext(clause.getContextKind());
    if (actualContext == null) {
      return false;
    }
    LDValue contextValue = actualContext.getValue(attr);
    if (contextValue.isNull()) {
      return false;
    }

    if (contextValue.getType() == LDValueType.ARRAY) {
      int nValues = contextValue.size();
      for (int i = 0; i < nValues; i++) {
        LDValue value = contextValue.get(i);
        if (matchClauseWithoutSegments(clause, value)) {
          return maybeNegate(clause, true);
        }
      }
      return maybeNegate(clause, false);
    } else if (contextValue.getType() != LDValueType.OBJECT) {
      return maybeNegate(clause, matchClauseWithoutSegments(clause, contextValue));
    }
    return false;
  }

  private boolean matchAnySegment(List<LDValue> values, LDContext context, EvaluatorState state) {
    // For the segmentMatch operator, the values list is really a list of segment keys. We
    // return a match if any of these segments matches the context.
    int nValues = values.size();
    for (int i = 0; i < nValues; i++) {
      LDValue clauseValue = values.get(i);
      if (!clauseValue.isString()) {
        continue;
      }
      String segmentKey = clauseValue.stringValue();
      if (state.segmentStack != null) {
        // Clauses within a segment can reference other segments, so we don't want to get stuck in a cycle.
        if (state.segmentStack.contains(segmentKey)) {
          throw new EvaluationException(ErrorKind.MALFORMED_FLAG,
              "segment rule referencing segment \"" + segmentKey + "\" caused a circular reference;" +
                  " this is probably a temporary condition due to an incomplete update");
        }
      }
      Segment segment = getters.getSegment(segmentKey);
      if (segment != null) {
        if (segmentMatchesContext(segment, context, state)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean segmentMatchesContext(Segment segment, LDContext context, EvaluatorState state) {
    if (segment.isUnbounded()) {
      if (segment.getGeneration() == null) {
        // Big Segment queries can only be done if the generation is known. If it's unset, that
        // probably means the data store was populated by an older SDK that doesn't know about the
        // generation property and therefore dropped it from the JSON data. We'll treat that as a
        // "not configured" condition.
        state.bigSegmentsStatus = EvaluationReason.BigSegmentsStatus.NOT_CONFIGURED;
        return false;
      }
      LDContext matchContext = context.getIndividualContext(segment.getUnboundedContextKind());
      if (matchContext == null) {
        return false;
      }
      String key = matchContext.getKey();
      BigSegmentStoreTypes.Membership membershipData =
          state.bigSegmentsMembership == null ? null : state.bigSegmentsMembership.get(key);
      if (membershipData == null) {
        BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = getters.getBigSegments(key);
        if (queryResult == null) {
          // The SDK hasn't been configured to be able to use big segments
          state.bigSegmentsStatus = EvaluationReason.BigSegmentsStatus.NOT_CONFIGURED;
        } else {
          membershipData = queryResult.membership;
          state.bigSegmentsStatus = queryResult.status;
          if (state.bigSegmentsMembership == null) {
            state.bigSegmentsMembership = new HashMap<>();
          }
          state.bigSegmentsMembership.put(key, membershipData);
        }
      }
      Boolean membershipResult = membershipData == null ? null :
          membershipData.checkMembership(makeBigSegmentRef(segment));
      if (membershipResult != null) {
        return membershipResult.booleanValue();
      }
    } else {
      if (contextKeyIsInTargetList(context, ContextKind.DEFAULT, segment.getIncluded())) {
        return true;
      }
      if (contextKeyIsInTargetLists(context, segment.getIncludedContexts())) {
        return true;
      }
      if (contextKeyIsInTargetList(context, ContextKind.DEFAULT, segment.getExcluded())) {
        return false;
      }
      if (contextKeyIsInTargetLists(context, segment.getExcludedContexts())) {
        return false;
      }
    }
    List<SegmentRule> rules = segment.getRules(); // guaranteed non-null
    if (!rules.isEmpty()) {
      // Evaluating rules means we might be doing recursive segment matches, so we'll push the current
      // segment key onto the stack for cycle detection.
      if (state.segmentStack == null) {
        state.segmentStack = new ArrayList<>();
      }
      state.segmentStack.add(segment.getKey());
      int nRules = rules.size();
      try {
        for (int i = 0; i < nRules; i++) {
          SegmentRule rule = rules.get(i);
          if (segmentRuleMatchesContext(rule, context, state, segment.getKey(), segment.getSalt())) {
            return true;
          }
        }
      } finally {
        state.segmentStack.remove(state.segmentStack.size() - 1);
      }
    }
    return false;
  }

  private boolean segmentRuleMatchesContext(
      SegmentRule segmentRule,
      LDContext context,
      EvaluatorState state,
      String segmentKey,
      String salt
  ) {
    List<Clause> clauses = segmentRule.getClauses(); // guaranteed non-null
    int nClauses = clauses.size();
    for (int i = 0; i < nClauses; i++) {
      Clause c = clauses.get(i);
      if (!clauseMatchesContext(c, context, state)) {
        return false;
      }
    }

    // If the Weight is absent, this rule matches
    if (segmentRule.getWeight() == null) {
      return true;
    }

    // All of the clauses are met. See if the context buckets in
    double bucket = computeBucketValue(
        false,
        null,
        context,
        segmentRule.getRolloutContextKind(),
        segmentKey,
        segmentRule.getBucketBy(),
        salt
    );
    double weight = (double) segmentRule.getWeight() / 100000.0;
    return bucket < weight;
  }

  private EvalResult computeRuleMatch(FeatureFlag flag, LDContext context, Rule rule, int ruleIndex) {
    if (rule.preprocessed != null) {
      return getValueForVariationOrRollout(flag, rule, context, rule.preprocessed.allPossibleResults, null);
    }
    EvaluationReason reason = EvaluationReason.ruleMatch(ruleIndex, rule.getId());
    return getValueForVariationOrRollout(flag, rule, context, null, reason);
  }

  static String makeBigSegmentRef(Segment segment) {
    return String.format("%s.g%d", segment.getKey(), segment.getGeneration());
  }
}
