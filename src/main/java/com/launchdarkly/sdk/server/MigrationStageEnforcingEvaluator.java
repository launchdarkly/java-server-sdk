package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;

/**
 * This class exists to enforce that migration variation results are stages from the {@link MigrationStage} enum.
 */
class MigrationStageEnforcingEvaluator implements EvaluatorInterface {

  private final EvaluatorInterface underlyingEvaluator;
  private final LDLogger logger;

  MigrationStageEnforcingEvaluator(EvaluatorInterface underlyingEvaluator, LDLogger logger) {
    this.underlyingEvaluator = underlyingEvaluator;
    this.logger = logger;
  }

  @Override
  public EvalResultAndFlag evalAndFlag(String method, String flagKey, LDContext context, LDValue defaultValue, LDValueType requireType, EvaluationOptions options) {
    EvalResultAndFlag res = underlyingEvaluator.evalAndFlag(method, flagKey, context, defaultValue, requireType, options);

    EvaluationDetail<String> resDetail = res.getResult().getAsString();
    String resStageString = resDetail.getValue();
    if (!MigrationStage.isStage(resStageString)) {
      logger.error("Unrecognized MigrationState for \"{}\"; returning default value.", flagKey);
      return new EvalResultAndFlag(EvalResult.error(EvaluationReason.ErrorKind.WRONG_TYPE, defaultValue), res.getFlag());
    }

    return res;
  }

  @Override
  public FeatureFlagsState allFlagsState(LDContext context, FlagsStateOption... options) {
    // this decorator is a pass through for the all flag state case
    return underlyingEvaluator.allFlagsState(context, options);
  }
}
