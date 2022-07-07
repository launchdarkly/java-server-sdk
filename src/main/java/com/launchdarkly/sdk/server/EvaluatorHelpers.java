package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;

abstract class EvaluatorHelpers {
  static EvaluationDetail<LDValue> resultForVariation(int variation, FeatureFlag flag, EvaluationReason reason) {
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
