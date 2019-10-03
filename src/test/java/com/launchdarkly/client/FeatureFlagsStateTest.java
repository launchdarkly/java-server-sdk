package com.launchdarkly.client;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import static com.launchdarkly.client.EvaluationDetail.fromValue;
import static com.launchdarkly.client.TestUtil.js;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class FeatureFlagsStateTest {
  private static final Gson gson = new Gson();
  
  @Test
  public void canGetFlagValue() {
    EvaluationDetail<LDValue> eval = fromValue(LDValue.of("value"), 1, EvaluationReason.off());
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
    EvaluationDetail<LDValue> eval = fromValue(LDValue.of("value1"), 1, EvaluationReason.off());
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
    EvaluationDetail<LDValue> eval = fromValue(LDValue.of("value1"), 1, EvaluationReason.off());
    FeatureFlag flag = new FeatureFlagBuilder("key").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder().addFlag(flag, eval).build();
    
    assertNull(state.getFlagReason("key"));
  }

  @Test
  public void flagCanHaveNullValue() {
    EvaluationDetail<LDValue> eval = fromValue(LDValue.ofNull(), 1, null);
    FeatureFlag flag = new FeatureFlagBuilder("key").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder().addFlag(flag, eval).build();
    
    assertNull(state.getFlagValue("key"));
  }

  @Test
  public void canConvertToValuesMap() {
    EvaluationDetail<LDValue> eval1 = fromValue(LDValue.of("value1"), 0, EvaluationReason.off());
    FeatureFlag flag1 = new FeatureFlagBuilder("key1").build();
    EvaluationDetail<LDValue> eval2 = fromValue(LDValue.of("value2"), 1, EvaluationReason.fallthrough());
    FeatureFlag flag2 = new FeatureFlagBuilder("key2").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder()
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    ImmutableMap<String, JsonElement> expected = ImmutableMap.<String, JsonElement>of("key1", js("value1"), "key2", js("value2"));
    assertEquals(expected, state.toValuesMap());
  }
  
  @Test
  public void canConvertToJson() {
    EvaluationDetail<LDValue> eval1 = fromValue(LDValue.of("value1"), 0, EvaluationReason.off());
    FeatureFlag flag1 = new FeatureFlagBuilder("key1").version(100).trackEvents(false).build();
    EvaluationDetail<LDValue> eval2 = fromValue(LDValue.of("value2"), 1, EvaluationReason.fallthrough());
    FeatureFlag flag2 = new FeatureFlagBuilder("key2").version(200).trackEvents(true).debugEventsUntilDate(1000L).build();
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
    EvaluationDetail<LDValue> eval1 = fromValue(LDValue.of("value1"), 0, EvaluationReason.off());
    FeatureFlag flag1 = new FeatureFlagBuilder("key1").version(100).trackEvents(false).build();
    EvaluationDetail<LDValue> eval2 = fromValue(LDValue.of("value2"), 1, EvaluationReason.off());
    FeatureFlag flag2 = new FeatureFlagBuilder("key2").version(200).trackEvents(true).debugEventsUntilDate(1000L).build();
    FeatureFlagsState state = new FeatureFlagsState.Builder()
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    String json = gson.toJson(state);
    FeatureFlagsState state1 = gson.fromJson(json, FeatureFlagsState.class);
    assertEquals(state, state1);
  }
}
