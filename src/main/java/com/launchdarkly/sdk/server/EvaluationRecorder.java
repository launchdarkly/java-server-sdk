package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;

/**
 * This interface exists to provide abstraction of event recording during the evaluation process so that it
 * can be customized by the {@link EvaluationOptions} provided at the time of evaluation.  This interface also
 * helps organize the structure of recording events and helps ensure consistency.
 */
interface EvaluationRecorder {
  default void recordEvaluation(DataModel.FeatureFlag flag, LDContext context, EvalResult result, LDValue defaultValue) {
    // default is no op
  }
  default void recordPrerequisiteEvaluation(DataModel.FeatureFlag flag, DataModel.FeatureFlag prereqOfFlag, LDContext context, EvalResult result) {
    // default is no op
  }
  default void recordEvaluationError(DataModel.FeatureFlag flag, LDContext context, LDValue defaultValue, EvaluationReason.ErrorKind errorKind) {
    // default is no op
  }
  default void recordEvaluationUnknownFlagError(String flagKey, LDContext context, LDValue defaultValue, EvaluationReason.ErrorKind errorKind) {
    // default is no op
  }
}