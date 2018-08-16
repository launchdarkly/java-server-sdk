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
    FeatureFlag.VariationAndValue eval = new FeatureFlag.VariationAndValue(1, js("value"));
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
  public void canConvertToValuesMap() {
    FeatureFlag.VariationAndValue eval1 = new FeatureFlag.VariationAndValue(0, js("value1"));
    FeatureFlag flag1 = new FeatureFlagBuilder("key1").build();
    FeatureFlag.VariationAndValue eval2 = new FeatureFlag.VariationAndValue(1, js("value2"));
    FeatureFlag flag2 = new FeatureFlagBuilder("key2").build();
    FeatureFlagsState state = new FeatureFlagsState.Builder()
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    ImmutableMap<String, JsonElement> expected = ImmutableMap.<String, JsonElement>of("key1", js("value1"), "key2", js("value2"));
    assertEquals(expected, state.toValuesMap());
  }
  
  @Test
  public void canConvertToJson() {
    FeatureFlag.VariationAndValue eval1 = new FeatureFlag.VariationAndValue(0, js("value1"));
    FeatureFlag flag1 = new FeatureFlagBuilder("key1").version(100).trackEvents(false).build();
    FeatureFlag.VariationAndValue eval2 = new FeatureFlag.VariationAndValue(1, js("value2"));
    FeatureFlag flag2 = new FeatureFlagBuilder("key2").version(200).trackEvents(true).debugEventsUntilDate(1000L).build();
    FeatureFlagsState state = new FeatureFlagsState.Builder()
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
    
    String json = "{\"key1\":\"value1\",\"key2\":\"value2\"," +
        "\"$flagsState\":{" +
          "\"key1\":{" +
            "\"variation\":0,\"version\":100,\"trackEvents\":false" +
          "},\"key2\":{" +
            "\"variation\":1,\"version\":200,\"trackEvents\":true,\"debugEventsUntilDate\":1000" +
          "}" +
        "}}";
    JsonElement expected = gson.fromJson(json, JsonElement.class);
    assertEquals(expected, gson.fromJson(state.toJsonString(), JsonElement.class));
  }
}
