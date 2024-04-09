package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.integrations.EvaluationSeriesContext;
import com.launchdarkly.sdk.server.integrations.Hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An {@link EvaluatorInterface} that will invoke the evaluation series methods of the provided {@link Hook} when
 * evaluations are made.
 */
class EvaluatorWithHooks implements EvaluatorInterface {

  private final EvaluatorInterface underlyingEvaluator;
  private final List<Hook> hooks;
  private final LDLogger logger;

  /**
   * @param underlyingEvaluator that will do the actual flag evaluation
   * @param hooks               that will be invoked at various stages of the evaluation series
   * @param hooksLogger         that will be used to log
   */
  EvaluatorWithHooks(EvaluatorInterface underlyingEvaluator, List<Hook> hooks, LDLogger hooksLogger) {
    this.underlyingEvaluator = underlyingEvaluator;
    this.hooks = hooks;
    this.logger = hooksLogger;
  }

  @Override
  public EvalResultAndFlag evalAndFlag(String method, String featureKey, LDContext context, LDValue defaultValue, LDValueType requireType, EvaluationOptions options) {
    // Each hook will have an opportunity to provide series data to carry along to later stages.  This list
    // is to track that data.
    List<Map> seriesDataList = new ArrayList<>(hooks.size());

    EvaluationSeriesContext seriesContext = new EvaluationSeriesContext(method, featureKey, context, defaultValue);
    for (int i = 0; i < hooks.size(); i++) {
      Hook currentHook = hooks.get(i);
      try {
        Map<String, Object> seriesData = currentHook.beforeEvaluation(seriesContext, Collections.emptyMap());
        seriesDataList.add(Collections.unmodifiableMap(seriesData)); // make data immutable
      } catch (Exception e) {
        seriesDataList.add(Collections.emptyMap()); // since the provided hook failed to execute, we default the series data to an empty map in this case
        logger.error("During evaluation of flag \"{}\". Stage \"BeforeEvaluation\" of hook \"{}\" reported error: {}", featureKey, currentHook.getMetadata().getName(), e.toString());
      }
    }

    EvalResultAndFlag result = underlyingEvaluator.evalAndFlag(method, featureKey, context, defaultValue, requireType, options);

    // Invoke hooks in reverse order and give them back the series data they gave us.
    for (int i = hooks.size() - 1; i >= 0; i--) {
      Hook currentHook = hooks.get(i);
      try {
        currentHook.afterEvaluation(seriesContext, seriesDataList.get(i), result.getResult().getAnyType());
      } catch (Exception e) {
        logger.error("During evaluation of flag \"{}\". Stage \"AfterEvaluation\" of hook \"{}\" reported error: {}", featureKey, currentHook.getMetadata().getName(), e.toString());
      }
    }

    return result;
  }

  @Override
  public FeatureFlagsState allFlagsState(LDContext context, FlagsStateOption... options) {
    // We do not support hooks for when all flags are evaluated.  Perhaps in the future that will be added.
    return underlyingEvaluator.allFlagsState(context, options);
  }
}
