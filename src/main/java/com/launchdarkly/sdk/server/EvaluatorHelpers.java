package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Prerequisite;
import com.launchdarkly.sdk.server.DataModel.Target;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;

/**
 * Low-level helpers for producing various kinds of evaluation results.
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
}
