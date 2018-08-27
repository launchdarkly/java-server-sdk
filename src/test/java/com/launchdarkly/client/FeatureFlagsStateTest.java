package com.launchdarkly.client;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.junit.Test;

import static com.launchdarkly.client.TestUtil.js;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FeatureFlagsStateTest {
  private static final Gson gson = new Gson();
  
  @Test
  public void canGetFlagValue() {
    EvaluationDetail<JsonElement> eval = new EvaluationDetail<JsonElement>(EvaluationReason.off(), 1, js("value"));
    FeatureFlag flag = new FeatureFlagBuilder("key").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder().addFlag(flag, eval).build();
    
    assertEquals(js("value"), state.getFlagValue("key"));
  }
  
  @Test
  public void unknownFlagReturnsNullValue() {
    FeatureFlagsState state = new FeatureFlagsState.Builder().build();
    
    assertNull(state.getFlagValue("key"));
  }

  @Test
  public void canGetFlagReason() {
    EvaluationDetail<JsonElement> eval = new EvaluationDetail<JsonElement>(EvaluationReason.off(), 1, js("value"));
    FeatureFlag flag = new FeatureFlagBuilder("key").build();
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
    EvaluationDetail<JsonElement> eval = new EvaluationDetail<JsonElement>(EvaluationReason.off(), 1, js("value"));
    FeatureFlag flag = new FeatureFlagBuilder("key").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder().addFlag(flag, eval).build();
    
    assertNull(state.getFlagReason("key"));
  }

  @Test
  public void flagCanHaveNullValue() {
    EvaluationDetail<JsonElement> eval = new EvaluationDetail<JsonElement>(null, 1, null);
    FeatureFlag flag = new FeatureFlagBuilder("key").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder().addFlag(flag, eval).build();
    
    assertNull(state.getFlagValue("key"));
  }

  @Test
  public void canConvertToValuesMap() {
    EvaluationDetail<JsonElement> eval1 = new EvaluationDetail<JsonElement>(EvaluationReason.off(), 0, js("value1"));
    FeatureFlag flag1 = new FeatureFlagBuilder("key1").build();
    EvaluationDetail<JsonElement> eval2 = new EvaluationDetail<JsonElement>(EvaluationReason.off(), 1, js("value2"));
    FeatureFlag flag2 = new FeatureFlagBuilder("key2").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder()
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    ImmutableMap<String, JsonElement> expected = ImmutableMap.<String, JsonElement>of("key1", js("value1"), "key2", js("value2"));
    assertEquals(expected, state.toValuesMap());
  }
  
  @Test
  public void canConvertToJson() {
    EvaluationDetail<JsonElement> eval1 = new EvaluationDetail<JsonElement>(EvaluationReason.off(), 0, js("value1"));
    FeatureFlag flag1 = new FeatureFlagBuilder("key1").version(100).trackEvents(false).build();
    EvaluationDetail<JsonElement> eval2 = new EvaluationDetail<JsonElement>(EvaluationReason.fallthrough(), 1, js("value2"));
    FeatureFlag flag2 = new FeatureFlagBuilder("key2").version(200).trackEvents(true).debugEventsUntilDate(1000L).build();
    FeatureFlagsState state = new FeatureFlagsState.Builder(FlagsStateOption.WITH_REASONS)
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    String json = "{\"key1\":\"value1\",\"key2\":\"value2\"," +
        "\"$flagsState\":{" +
          "\"key1\":{" +
            "\"variation\":0,\"version\":100,\"reason\":{\"kind\":\"OFF\"},\"trackEvents\":false" +
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
    EvaluationDetail<JsonElement> eval1 = new EvaluationDetail<JsonElement>(null, 0, js("value1"));
    FeatureFlag flag1 = new FeatureFlagBuilder("key1").version(100).trackEvents(false).build();
    EvaluationDetail<JsonElement> eval2 = new EvaluationDetail<JsonElement>(null, 1, js("value2"));
    FeatureFlag flag2 = new FeatureFlagBuilder("key2").version(200).trackEvents(true).debugEventsUntilDate(1000L).build();
    FeatureFlagsState state = new FeatureFlagsState.Builder()
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    String json = gson.toJson(state);
    FeatureFlagsState state1 = gson.fromJson(json, FeatureFlagsState.class);
    assertEquals(state, state1);
  }
}
