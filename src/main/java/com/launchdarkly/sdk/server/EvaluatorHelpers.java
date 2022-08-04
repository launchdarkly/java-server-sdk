package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Prerequisite;
import com.launchdarkly.sdk.server.DataModel.SegmentTarget;
import com.launchdarkly.sdk.server.DataModel.Target;
import com.launchdarkly.sdk.server.DataModelPreprocessing.ClausePreprocessed;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.server.EvaluatorHelpers.contextKeyIsInTargetList;

/**
 * Low-level helpers for producing various kinds of evaluation results. We also put any
 * helpers here that are used by Evaluator if they are static, i.e. if they can be
 * implemented without reference to the Evaluator instance's own state, so as to keep the
 * Evaluator logic smaller and easier to follow. 
 * <p>
 * For all of the methods that return an {@link EvalResult}, the behavior is as follows:
 * First we check if the flag data contains a preprocessed value for this kind of result; if
 * so, we return that same EvalResult instance, for efficiency. That will normally always be
 * the case, because preprocessing happens as part of deserializing a flag. But if somehow
 * no preprocessed value is available, we construct one less efficiently on the fly. (The
 * reason we can't absolutely guarantee that the preprocessed data is available, by putting
 * it in a constructor, is because of how deserialization works: Gson doesn't pass values to
 * a constructor, it sets fields directly, so we have to run our preprocessing logic after.)
 */
abstract class EvaluatorHelpers {
  static EvalResult offResult(FeatureFlag flag) {
    if (flag.preprocessed != null) {
      return flag.preprocessed.offResult;
    }
    return EvalResult.of(evaluationDetailForOffVariation(flag, EvaluationReason.off()));
  }
  
  static EvalResult targetMatchResult(FeatureFlag flag, Target target) {
    if (target.preprocessed != null) {
      return target.preprocessed.targetMatchResult;
    }
    return EvalResult.of(evaluationDetailForVariation(
        flag, target.getVariation(), EvaluationReason.targetMatch()));
  }
  
  static EvalResult prerequisiteFailedResult(FeatureFlag flag, Prerequisite prereq) {
    if (prereq.preprocessed != null) {
      return prereq.preprocessed.prerequisiteFailedResult;
    }
    return EvalResult.of(evaluationDetailForOffVariation(
        flag, EvaluationReason.prerequisiteFailed(prereq.getKey())));
  }

  static EvaluationDetail<LDValue> evaluationDetailForOffVariation(FeatureFlag flag, EvaluationReason reason) {
    Integer offVariation = flag.getOffVariation();
    if (offVariation == null) { // off variation unspecified - return default value
      return EvaluationDetail.fromValue(LDValue.ofNull(), NO_VARIATION, reason);
    }
    return evaluationDetailForVariation(flag, offVariation, reason);
  }
  
  static EvaluationDetail<LDValue> evaluationDetailForVariation(FeatureFlag flag, int variation, EvaluationReason reason) {
    if (variation < 0 || variation >= flag.getVariations().size()) {
      return EvaluationDetail.fromValue(LDValue.ofNull(), NO_VARIATION,
          EvaluationReason.error(ErrorKind.MALFORMED_FLAG));
    }
    return EvaluationDetail.fromValue(
        LDValue.normalize(flag.getVariations().get(variation)),
        variation,
        reason);
  }

  static boolean maybeNegate(Clause clause, boolean b) {
    return clause.isNegate() ? !b : b;
  }

  // Performs an operator test between a single context value and all of the clause values, for any
  // operator except segmentMatch.
  static boolean matchClauseWithoutSegments(Clause clause, LDValue contextValue) {
    Operator op = clause.getOp();
    if (op != null) {
      ClausePreprocessed preprocessed = clause.preprocessed;
      if (op == Operator.in) {
        // see if we have precomputed a Set for fast equality matching
        Set<LDValue> vs = preprocessed == null ? null : preprocessed.valuesSet;
        if (vs != null) {
          return vs.contains(contextValue);
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
        if (EvaluatorOperators.apply(op, contextValue, v, p)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean matchClauseByKind(Clause clause, LDContext context) {
    // If attribute is "kind", then we treat operator and values as a match expression against a list
    // of all individual kinds in the context. That is, for a multi-kind context with kinds of "org"
    // and "user", it is a match if either of those strings is a match with Operator and Values.
    for (int i = 0; i < context.getIndividualContextCount(); i++) {
      if (matchClauseWithoutSegments(clause, LDValue.of(
          context.getIndividualContext(i).getKind().toString()))) {
        return true;
      }
    }
    return false;
  }
  
  static boolean contextKeyIsInTargetList(LDContext context, ContextKind contextKind, Collection<String> keys) {
    if (keys.isEmpty()) {
      return false;
    }
    LDContext matchContext = context.getIndividualContext(contextKind);
    return matchContext != null && keys.contains(matchContext.getKey());
  }

  static boolean contextKeyIsInTargetLists(LDContext context, List<SegmentTarget> targets) {
    int nTargets = targets.size();
    for (int i = 0; i < nTargets; i++) {
      SegmentTarget t = targets.get(i);
      if (contextKeyIsInTargetList(context, t.getContextKind(), t.getValues())) {
        return true;
      }
    }
    return false;
  }
}
