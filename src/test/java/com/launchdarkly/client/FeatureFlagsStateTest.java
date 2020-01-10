package com.launchdarkly.client;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import static com.launchdarkly.client.ModelBuilders.flagBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class FeatureFlagsStateTest {
  private static final Gson gson = new Gson();
  
  @Test
  public void canGetFlagValue() {
    Evaluator.EvalResult eval = new Evaluator.EvalResult(LDValue.of("value"), 1, EvaluationReason.off());
    DataModel.FeatureFlag flag = flagBuilder("key").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder().addFlag(flag, eval).build();
    
    assertEquals(LDValue.of("value"), state.getFlagValue("key"));
  }
  
  @Test
  public void unknownFlagReturnsNullValue() {
    FeatureFlagsState state = new FeatureFlagsState.Builder().build();
    
    assertNull(state.getFlagValue("key"));
  }

  @Test
  public void canGetFlagReason() {
    Evaluator.EvalResult eval = new Evaluator.EvalResult(LDValue.of("value1"), 1, EvaluationReason.off());
    DataModel.FeatureFlag flag = flagBuilder("key").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder(FlagsStateOption.WITH_REASONS)
        .addFlag(flag, eval).build();
    
    assertEquals(EvaluationReason.off(), state.getFlagReason("key"));
  }

  @Test
  public void unknownFlagReturnsNullReason() {
    FeatureFlagsState state = new FeatureFlagsState.Builder().build();
    
    assertNull(state.getFlagReason("key"));
  }

  @Test
  public void reasonIsNullIfReasonsWereNotRecorded() {
    Evaluator.EvalResult eval = new Evaluator.EvalResult(LDValue.of("value1"), 1, EvaluationReason.off());
    DataModel.FeatureFlag flag = flagBuilder("key").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder().addFlag(flag, eval).build();
    
    assertNull(state.getFlagReason("key"));
  }

  @Test
  public void flagCanHaveNullValue() {
    Evaluator.EvalResult eval = new Evaluator.EvalResult(LDValue.ofNull(), 1, null);
    DataModel.FeatureFlag flag = flagBuilder("key").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder().addFlag(flag, eval).build();
    
    assertEquals(LDValue.ofNull(), state.getFlagValue("key"));
  }

  @Test
  public void canConvertToValuesMap() {
    Evaluator.EvalResult eval1 = new Evaluator.EvalResult(LDValue.of("value1"), 0, EvaluationReason.off());
    DataModel.FeatureFlag flag1 = flagBuilder("key1").build();
    Evaluator.EvalResult eval2 = new Evaluator.EvalResult(LDValue.of("value2"), 1, EvaluationReason.fallthrough());
    DataModel.FeatureFlag flag2 = flagBuilder("key2").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder()
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    ImmutableMap<String, LDValue> expected = ImmutableMap.<String, LDValue>of("key1", LDValue.of("value1"), "key2", LDValue.of("value2"));
    assertEquals(expected, state.toValuesMap());
  }
  
  @Test
  public void canConvertToJson() {
    Evaluator.EvalResult eval1 = new Evaluator.EvalResult(LDValue.of("value1"), 0, EvaluationReason.off());
    DataModel.FeatureFlag flag1 = flagBuilder("key1").version(100).trackEvents(false).build();
    Evaluator.EvalResult eval2 = new Evaluator.EvalResult(LDValue.of("value2"), 1, EvaluationReason.fallthrough());
    DataModel.FeatureFlag flag2 = flagBuilder("key2").version(200).trackEvents(true).debugEventsUntilDate(1000L).build();
    FeatureFlagsState state = new FeatureFlagsState.Builder(FlagsStateOption.WITH_REASONS)
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    String json = "{\"key1\":\"value1\",\"key2\":\"value2\"," +
        "\"$flagsState\":{" +
          "\"key1\":{" +
            "\"variation\":0,\"version\":100,\"reason\":{\"kind\":\"OFF\"}" +  // note, "trackEvents: false" is omitted
          "},\"key2\":{" +
            "\"variation\":1,\"version\":200,\"reason\":{\"kind\":\"FALLTHROUGH\"},\"trackEvents\":true,\"debugEventsUntilDate\":1000" +
          "}" +
        "}," +
        "\"$valid\":true" +
      "}";
    JsonElement expected = gson.fromJson(json, JsonElement.class);
    assertEquals(expected, gson.toJsonTree(state));
  }
  
  @Test
  public void canConvertFromJson() {
    Evaluator.EvalResult eval1 = new Evaluator.EvalResult(LDValue.of("value1"), 0, EvaluationReason.off());
    DataModel.FeatureFlag flag1 = flagBuilder("key1").version(100).trackEvents(false).build();
    Evaluator.EvalResult eval2 = new Evaluator.EvalResult(LDValue.of("value2"), 1, EvaluationReason.off());
    DataModel.FeatureFlag flag2 = flagBuilder("key2").version(200).trackEvents(true).debugEventsUntilDate(1000L).build();
    FeatureFlagsState state = new FeatureFlagsState.Builder()
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    String json = gson.toJson(state);
    FeatureFlagsState state1 = gson.fromJson(json, FeatureFlagsState.class);
    assertEquals(state, state1);
  }
}
