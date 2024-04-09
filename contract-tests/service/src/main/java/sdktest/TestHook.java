package sdktest;

import com.launchdarkly.sdk.server.integrations.Hook;
import com.launchdarkly.sdk.server.integrations.HookMetadata;
import com.launchdarkly.sdk.server.integrations.EvaluationSeriesContext;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;

import sdktest.Representations.EvaluationHookCallbackParams;
import sdktest.Representations.EvaluationSeriesContextParam;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

class TestHook extends Hook {

  private HookCallbackService callbackService;
  private Map<String, Object> beforeEvaluationData;
  private Map<String, Object> afterEvaluationData;
  private String beforeEvaluationError;
  private String afterEvaluationError;

  TestHook(String name, HookCallbackService callbackService, Map<String, Object> beforeEvaluationData, Map<String, Object> afterEvaluationData, String beforeEvaluationError, String afterEvaluationError) {
    super(name);
    this.callbackService = callbackService;
    this.beforeEvaluationData = beforeEvaluationData;
    this.afterEvaluationData = afterEvaluationData;
    this.beforeEvaluationError = beforeEvaluationError;
    this.afterEvaluationError = afterEvaluationError;
  }

  @Override
  public Map<String, Object> beforeEvaluation(EvaluationSeriesContext seriesContext, Map<String, Object> data) {

    if (this.beforeEvaluationError != null) {
      throw new RuntimeException(this.beforeEvaluationError);
    }

    EvaluationHookCallbackParams params = new EvaluationHookCallbackParams();

    EvaluationSeriesContextParam seriesContextParam = new EvaluationSeriesContextParam();
    seriesContextParam.flagKey = seriesContext.flagKey;
    seriesContextParam.context = seriesContext.context;
    seriesContextParam.defaultValue = seriesContext.defaultValue;
    seriesContextParam.method = seriesContext.method;
    params.evaluationSeriesContext = seriesContextParam;

    params.evaluationSeriesData = data;
    params.stage = "beforeEvaluation";
    callbackService.post(params);

    HashMap<String, Object> newData = new HashMap<>(data);
    if (beforeEvaluationData != null) {
      newData.putAll(beforeEvaluationData);
    }

    return Collections.unmodifiableMap(newData);
  }

  @Override
  public Map<String, Object> afterEvaluation(EvaluationSeriesContext seriesContext, Map<String, Object> data, EvaluationDetail<LDValue> evaluationDetail) {

    if (this.afterEvaluationError != null) {
      throw new RuntimeException(this.afterEvaluationError);
    }

    EvaluationHookCallbackParams params = new EvaluationHookCallbackParams();

    EvaluationSeriesContextParam seriesContextParam = new EvaluationSeriesContextParam();
    seriesContextParam.flagKey = seriesContext.flagKey;
    seriesContextParam.context = seriesContext.context;
    seriesContextParam.defaultValue = seriesContext.defaultValue;
    seriesContextParam.method = seriesContext.method;
    params.evaluationSeriesContext = seriesContextParam;

    params.evaluationSeriesData = data;
    params.evaluationDetail = evaluationDetail;
    params.stage = "afterEvaluation";
    callbackService.post(params);

    HashMap<String, Object> newData = new HashMap<>(data);
    if (afterEvaluationData != null) {
      newData.putAll(afterEvaluationData);
    }

    return Collections.unmodifiableMap(newData);
  }
}
