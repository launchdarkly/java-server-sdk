package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;

/**
 * An Evaluator is able to calculate evaluation results for flags against the provided context.
 */
public interface EvaluatorInterface {

  /**
   * Evaluates the provided flag.
   *
   * @param method       the top level customer facing method that led to this invocation
   * @param flagKey      of the flag that will be evaluated
   * @param context      to use during the evaluation
   * @param defaultValue the value that will be returned in the result if an issue prevents the evaluator from
   *                     successfully calculating an evaluation result.
   * @param requireType  that will be asserted against the evaluator's result. If the assertion fails, the default
   *                     value is used in the returned result.
   * @param options      that are used to control more specific behavior of the evaluation
   * @return the evaluation result and flag object
   */
  EvalResultAndFlag evalAndFlag(String method, String flagKey, LDContext context, LDValue defaultValue,
                                LDValueType requireType, EvaluationOptions options);

  /**
   * Evaluates all flags.
   * <p>
   * It is up to each implementation whether events will be logged during evaluation.
   *
   * @param context to use during the evaluation
   * @param options optional {@link FlagsStateOption} values affecting how the state is computed
   * @return a {@link FeatureFlagsState} object (will never be null; see {@link FeatureFlagsState#isValid()}
   */
  FeatureFlagsState allFlagsState(LDContext context, FlagsStateOption... options);
}
