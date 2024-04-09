package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.integrations.Hook;
import com.launchdarkly.sdk.server.integrations.HookMetadata;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EvaluatorWithHookTest {

  @Test
  public void beforeIsExecutedBeforeAfter() {
    EvalResultAndFlag evalResult = new EvalResultAndFlag(EvalResult.of(LDValue.of("aValue"), 0, EvaluationReason.fallthrough()), null);
    EvaluatorInterface mockEvaluator = mock(EvaluatorInterface.class);
    when(mockEvaluator.evalAndFlag(any(), any(), any(), any(), any(), any())).thenReturn(evalResult);

    Hook mockHook = mock(Hook.class);
    AtomicBoolean beforeCalled = new AtomicBoolean(false);
    when(mockHook.beforeEvaluation(any(), any())).thenAnswer((Answer<Map<String, Object>>) invocation -> {
      beforeCalled.set(true);
      return Collections.emptyMap();
    });

    when(mockHook.afterEvaluation(any(), any(), any())).thenAnswer((Answer<Map<String, Object>>) invocation -> {
      assertTrue(beforeCalled.get());
      return Collections.emptyMap();
    });
    EvaluatorWithHooks evaluatorUnderTest = new EvaluatorWithHooks(mockEvaluator, Collections.singletonList(mockHook), LDLogger.none());
    evaluatorUnderTest.evalAndFlag("aMethod", "aKey", LDContext.create("aKey"), LDValue.of("aDefault"), LDValueType.STRING, EvaluationOptions.NO_EVENTS);
  }

  @Test
  public void evaluationResultIsPassedToAfter() {
    EvalResultAndFlag evalResult = new EvalResultAndFlag(EvalResult.of(LDValue.of("aValue"), 0, EvaluationReason.fallthrough()), null);
    EvaluatorInterface mockEvaluator = mock(EvaluatorInterface.class);
    when(mockEvaluator.evalAndFlag(any(), any(), any(), any(), any(), any())).thenReturn(evalResult);

    Hook mockHook = mock(Hook.class);
    when(mockHook.beforeEvaluation(any(), any())).thenReturn(Collections.emptyMap());
    when(mockHook.afterEvaluation(any(), any(), any())).thenReturn(Collections.emptyMap());

    EvaluatorWithHooks evaluatorUnderTest = new EvaluatorWithHooks(mockEvaluator, Collections.singletonList(mockHook), LDLogger.none());
    evaluatorUnderTest.evalAndFlag("aMethod", "aKey", LDContext.create("aKey"), LDValue.of("aDefault"), LDValueType.STRING, EvaluationOptions.NO_EVENTS);

    verify(mockHook).afterEvaluation(any(), any(), eq(EvaluationDetail.fromValue(LDValue.of("aValue"), 0, EvaluationReason.fallthrough())));
  }

  @Test
  public void afterExecutesInReverseOrder() {
    EvalResultAndFlag evalResult = new EvalResultAndFlag(EvalResult.of(LDValue.of("aValue"), 0, EvaluationReason.fallthrough()), null);
    EvaluatorInterface mockEvaluator = mock(EvaluatorInterface.class);
    when(mockEvaluator.evalAndFlag(any(), any(), any(), any(), any(), any())).thenReturn(evalResult);

    List<String> calls = new ArrayList<>();

    Hook mockHookA = mock(Hook.class);
    when(mockHookA.beforeEvaluation(any(), any())).thenAnswer(invocation -> {
      calls.add("hookABefore");
      return Collections.emptyMap();
    });
    when(mockHookA.afterEvaluation(any(), any(), any())).thenAnswer(invocation -> {
      calls.add("hookAAfter");
      return Collections.emptyMap();
    });

    Hook mockHookB = mock(Hook.class);
    when(mockHookB.beforeEvaluation(any(), any())).thenAnswer(invocation -> {
      calls.add("hookBBefore");
      return Collections.emptyMap();
    });
    when(mockHookB.afterEvaluation(any(), any(), any())).thenAnswer(invocation -> {
      calls.add("hookBAfter");
      return Collections.emptyMap();
    });

    EvaluatorWithHooks evaluatorUnderTest = new EvaluatorWithHooks(mockEvaluator, Arrays.asList(mockHookA, mockHookB), LDLogger.none());
    evaluatorUnderTest.evalAndFlag("aMethod", "aKey", LDContext.create("aKey"), LDValue.of("aDefault"), LDValueType.STRING, EvaluationOptions.NO_EVENTS);
    assertEquals(calls, Arrays.asList("hookABefore", "hookBBefore", "hookBAfter", "hookAAfter"));
  }

  @Test
  public void beforeIsGivenEmptySeriesData() {
    EvalResultAndFlag evalResult = new EvalResultAndFlag(EvalResult.of(LDValue.of("aValue"), 0, EvaluationReason.fallthrough()), null);
    EvaluatorInterface mockEvaluator = mock(EvaluatorInterface.class);
    when(mockEvaluator.evalAndFlag(any(), any(), any(), any(), any(), any())).thenReturn(evalResult);

    Hook mockHook = mock(Hook.class);
    when(mockHook.beforeEvaluation(any(), any())).thenReturn(Collections.emptyMap());
    when(mockHook.afterEvaluation(any(), any(), eq(evalResult.getResult().getAnyType()))).thenReturn(Collections.emptyMap());

    EvaluatorWithHooks evaluatorUnderTest = new EvaluatorWithHooks(mockEvaluator, Collections.singletonList(mockHook), LDLogger.none());
    evaluatorUnderTest.evalAndFlag("aMethod", "aKey", LDContext.create("aKey"), LDValue.of("aDefault"), LDValueType.STRING, EvaluationOptions.NO_EVENTS);

    verify(mockHook).beforeEvaluation(any(), eq(Collections.emptyMap()));
  }

  @Test
  public void seriesDataFromBeforeIsPassedToAfter() {
    EvalResultAndFlag evalResult = new EvalResultAndFlag(EvalResult.of(LDValue.of("aValue"), 0, EvaluationReason.fallthrough()), null);
    EvaluatorInterface mockEvaluator = mock(EvaluatorInterface.class);
    when(mockEvaluator.evalAndFlag(any(), any(), any(), any(), any(), any())).thenReturn(evalResult);

    Hook mockHook = mock(Hook.class);
    Map<String, Object> mockData = new HashMap<>();
    mockData.put("dataKey", "dataValue");
    when(mockHook.beforeEvaluation(any(), any())).thenReturn(mockData);
    when(mockHook.afterEvaluation(any(), any(), any())).thenReturn(Collections.emptyMap());

    EvaluatorWithHooks evaluatorUnderTest = new EvaluatorWithHooks(mockEvaluator, Collections.singletonList(mockHook), LDLogger.none());
    evaluatorUnderTest.evalAndFlag("aMethod", "aKey", LDContext.create("aKey"), LDValue.of("aDefault"), LDValueType.STRING, EvaluationOptions.NO_EVENTS);

    verify(mockHook).afterEvaluation(any(), eq(mockData), any());
  }

  @Test
  public void beforeThrowingErrorLeadsToEmptySeriesDataPassedToAfter() {
    EvalResultAndFlag evalResult = new EvalResultAndFlag(EvalResult.of(LDValue.of("aValue"), 0, EvaluationReason.fallthrough()), null);
    EvaluatorInterface mockEvaluator = mock(EvaluatorInterface.class);
    when(mockEvaluator.evalAndFlag(any(), any(), any(), any(), any(), any())).thenReturn(evalResult);

    Hook mockHook = mock(Hook.class);
    when(mockHook.beforeEvaluation(any(), any())).thenAnswer(invocation -> {
      throw new Exception("Exceptions for everyone!");
    });
    when(mockHook.getMetadata()).thenReturn(new HookMetadata("mockHookName") {});
    when(mockHook.afterEvaluation(any(), any(), any())).thenReturn(Collections.emptyMap());

    EvaluatorWithHooks evaluatorUnderTest = new EvaluatorWithHooks(mockEvaluator, Collections.singletonList(mockHook), LDLogger.none());
    evaluatorUnderTest.evalAndFlag("aMethod", "aKey", LDContext.create("aKey"), LDValue.of("aDefault"), LDValueType.STRING, EvaluationOptions.NO_EVENTS);

    verify(mockHook, times(1)).getMetadata();
    verify(mockHook).afterEvaluation(any(), eq(Collections.emptyMap()), any());
  }

  @Test
  public void oneHookThrowingErrorDoesNotAffectOtherHooks() {
    EvalResultAndFlag evalResult = new EvalResultAndFlag(EvalResult.of(LDValue.of("aValue"), 0, EvaluationReason.fallthrough()), null);
    EvaluatorInterface mockEvaluator = mock(EvaluatorInterface.class);
    when(mockEvaluator.evalAndFlag(any(), any(), any(), any(), any(), any())).thenReturn(evalResult);

    List<String> calls = new ArrayList<>();

    Hook mockHookA = mock(Hook.class);
    when(mockHookA.beforeEvaluation(any(), any())).thenAnswer(invocation -> {
      calls.add("hookABefore");
      throw new Exception("Exceptions for everyone!");
    });
    // after will get an empty map which is the default series data when an exception occurs in before
    when(mockHookA.afterEvaluation(any(), any(), any())).thenAnswer(invocation -> {
      calls.add("hookAAfter");
      return Collections.emptyMap();
    });
    when(mockHookA.getMetadata()).thenReturn(new HookMetadata("mockHookA") {});

    Hook mockHookB = mock(Hook.class);
    Map<String, Object> mockData = new HashMap<>();
    mockData.put("dataKey", "dataValue");
    when(mockHookB.beforeEvaluation(any(), any())).thenAnswer(invocation -> {
      calls.add("hookBBefore");
      return mockData;
    });
    when(mockHookB.afterEvaluation(any(), any(), any())).thenAnswer(invocation -> {
      calls.add("hookBAfter");
      return Collections.emptyMap();
    });

    EvaluatorWithHooks evaluatorUnderTest = new EvaluatorWithHooks(mockEvaluator, Arrays.asList(mockHookA, mockHookB), LDLogger.none());
    evaluatorUnderTest.evalAndFlag("aMethod", "aKey", LDContext.create("aKey"), LDValue.of("aDefault"), LDValueType.STRING, EvaluationOptions.NO_EVENTS);
    assertEquals(calls, Arrays.asList("hookABefore", "hookBBefore", "hookBAfter", "hookAAfter"));

    verify(mockHookA).afterEvaluation(any(), eq(Collections.emptyMap()), any());
    verify(mockHookB).afterEvaluation(any(), eq(mockData), any());
  }
}
