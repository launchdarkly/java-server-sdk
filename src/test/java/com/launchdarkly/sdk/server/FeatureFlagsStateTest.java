package com.launchdarkly.sdk.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.json.JsonSerialization;
import com.launchdarkly.sdk.json.LDJackson;
import com.launchdarkly.sdk.json.SerializationException;

import org.junit.Test;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class FeatureFlagsStateTest {
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
    String actualJsonString = JsonSerialization.serialize(makeInstanceForSerialization());
    assertEquals(LDValue.parse(makeExpectedJsonSerialization()), LDValue.parse(actualJsonString));
  }
  
  @Test
  public void canConvertFromJson() throws SerializationException {
    FeatureFlagsState state = JsonSerialization.deserialize(makeExpectedJsonSerialization(), FeatureFlagsState.class);
    assertEquals(makeInstanceForSerialization(), state);
  }
  
  private static FeatureFlagsState makeInstanceForSerialization() {
    Evaluator.EvalResult eval1 = new Evaluator.EvalResult(LDValue.of("value1"), 0, EvaluationReason.off());
    DataModel.FeatureFlag flag1 = flagBuilder("key1").version(100).trackEvents(false).build();
    Evaluator.EvalResult eval2 = new Evaluator.EvalResult(LDValue.of("value2"), 1, EvaluationReason.fallthrough());
    DataModel.FeatureFlag flag2 = flagBuilder("key2").version(200).trackEvents(true).debugEventsUntilDate(1000L).build();
    return new FeatureFlagsState.Builder(FlagsStateOption.WITH_REASONS)
        .addFlag(flag1, eval1).addFlag(flag2, eval2).build();
  }
  
  private static String makeExpectedJsonSerialization() {
    return "{\"key1\":\"value1\",\"key2\":\"value2\"," +
        "\"$flagsState\":{" +
        "\"key1\":{" +
          "\"variation\":0,\"version\":100,\"reason\":{\"kind\":\"OFF\"}" +  // note, "trackEvents: false" is omitted
        "},\"key2\":{" +
          "\"variation\":1,\"version\":200,\"reason\":{\"kind\":\"FALLTHROUGH\"},\"trackEvents\":true,\"debugEventsUntilDate\":1000" +
        "}" +
      "}," +
      "\"$valid\":true" +
    "}";
  }
  
  @Test
  public void canSerializeAndDeserializeWithJackson() throws Exception {
    // FeatureFlagsState, being a JsonSerializable, should get the same custom serialization/deserialization
    // support that is provided by java-sdk-common for Gson and Jackson. Our Gson interoperability just relies
    // on the same Gson annotations that we use internally, but the Jackson adapter will only work if the
    // java-server-sdk and java-sdk-common packages are configured together correctly. So we'll test that here.
    // If it fails, the symptom will be something like Jackson complaining that it doesn't know how to
    // instantiate the FeatureFlagsState class.
    
    ObjectMapper jacksonMapper = new ObjectMapper();
    jacksonMapper.registerModule(LDJackson.module());
    
    String actualJsonString = jacksonMapper.writeValueAsString(makeInstanceForSerialization());
    assertEquals(LDValue.parse(makeExpectedJsonSerialization()), LDValue.parse(actualJsonString));
    
    FeatureFlagsState state = jacksonMapper.readValue(makeExpectedJsonSerialization(), FeatureFlagsState.class);
    assertEquals(makeInstanceForSerialization(), state);
  }
}
