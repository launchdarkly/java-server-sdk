package com.launchdarkly.sdk.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.json.JsonSerialization;
import com.launchdarkly.sdk.json.LDJackson;
import com.launchdarkly.sdk.json.SerializationException;
import com.launchdarkly.testhelpers.TypeBehavior;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.EvaluationReason.ErrorKind.MALFORMED_FLAG;
import static com.launchdarkly.sdk.server.FlagsStateOption.DETAILS_ONLY_FOR_TRACKED_FLAGS;
import static com.launchdarkly.sdk.server.FlagsStateOption.WITH_REASONS;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.testhelpers.JsonAssertions.assertJsonEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class FeatureFlagsStateTest {
  @Test
  public void canGetFlagValue() {
    FeatureFlagsState state = FeatureFlagsState.builder()
        .add("key", LDValue.of("value"), 1, null, 10, false, null)
        .build();
    
    assertEquals(LDValue.of("value"), state.getFlagValue("key"));
  }
  
  @Test
  public void unknownFlagReturnsNullValue() {
    FeatureFlagsState state = FeatureFlagsState.builder().build();
    
    assertNull(state.getFlagValue("key"));
  }

  @Test
  public void canGetFlagReason() {
    FeatureFlagsState state = FeatureFlagsState.builder(WITH_REASONS)
        .add("key", LDValue.of("value"), 1, EvaluationReason.off(), 10, false, null)
        .build();
    
    assertEquals(EvaluationReason.off(), state.getFlagReason("key"));
  }

  @Test
  public void unknownFlagReturnsNullReason() {
    FeatureFlagsState state = FeatureFlagsState.builder().build();
    
    assertNull(state.getFlagReason("key"));
  }

  @Test
  public void reasonIsNullIfReasonsWereNotRecorded() {
    FeatureFlagsState state = FeatureFlagsState.builder()
        .add("key", LDValue.of("value"), 1, EvaluationReason.off(), 10, false, null)
        .build();
    
    assertNull(state.getFlagReason("key"));
  }

  @Test
  public void flagIsTreatedAsTrackedIfDebugEventsUntilDateIsInFuture() {
    FeatureFlagsState state = FeatureFlagsState.builder(WITH_REASONS, DETAILS_ONLY_FOR_TRACKED_FLAGS)
        .add("key", LDValue.of("value"), 1, EvaluationReason.off(), 10, false, System.currentTimeMillis() + 1000000)
        .build();
    
    assertNotNull(state.getFlagReason("key"));
  }

  @Test
  public void flagIsNotTreatedAsTrackedIfDebugEventsUntilDateIsInPast() {
    FeatureFlagsState state = FeatureFlagsState.builder(WITH_REASONS, DETAILS_ONLY_FOR_TRACKED_FLAGS)
        .add("key", LDValue.of("value"), 1, EvaluationReason.off(), 10, false, System.currentTimeMillis() - 1000000)
        .build();
    
    assertNull(state.getFlagReason("key"));
  }
  
  @Test
  public void flagCanHaveNullValue() {
    FeatureFlagsState state = FeatureFlagsState.builder()
        .add("key", LDValue.ofNull(), 1, null, 10, false, null)
        .build();
    
    assertEquals(LDValue.ofNull(), state.getFlagValue("key"));
  }

  @Test
  public void canConvertToValuesMap() {
    FeatureFlagsState state = FeatureFlagsState.builder()
        .add("key1", LDValue.of("value1"), 0, null, 10, false, null)
        .add("key2", LDValue.of("value2"), 1, null, 10, false, null)
        .build();
    
    ImmutableMap<String, LDValue> expected = ImmutableMap.<String, LDValue>of("key1", LDValue.of("value1"), "key2", LDValue.of("value2"));
    assertEquals(expected, state.toValuesMap());
  }
  
  @Test
  public void equalInstancesAreEqual() {
    FeatureFlagsState justOneFlag = FeatureFlagsState.builder(WITH_REASONS)
        .add("key1", LDValue.of("value1"), 0, EvaluationReason.off(), 10, false, null)
        .build();
    FeatureFlagsState sameFlagsDifferentInstances1 = FeatureFlagsState.builder(WITH_REASONS)
        .add("key1", LDValue.of("value1"), 0, EvaluationReason.off(), 10, false, null)
        .add("key2", LDValue.of("value2"), 1, EvaluationReason.fallthrough(), 10, false, null)
        .build();
    FeatureFlagsState sameFlagsDifferentInstances2 = FeatureFlagsState.builder(WITH_REASONS)
        .add("key1", LDValue.of("value1"), 0, EvaluationReason.off(), 10, false, null)
        .add("key2", LDValue.of("value2"), 1, EvaluationReason.fallthrough(), 10, false, null)
        .build();
    FeatureFlagsState sameFlagsDifferentMetadata = FeatureFlagsState.builder(WITH_REASONS)
        .add("key1", LDValue.of("value1"), 1, EvaluationReason.off(), 10, false, null)
        .add("key2", LDValue.of("value2"), 1, EvaluationReason.fallthrough(), 10, false, null)
        .build();
    FeatureFlagsState noFlagsButValid = FeatureFlagsState.builder(WITH_REASONS).build();
    FeatureFlagsState noFlagsAndNotValid = FeatureFlagsState.builder(WITH_REASONS).valid(false).build();
    
    assertEquals(sameFlagsDifferentInstances1, sameFlagsDifferentInstances2);
    assertEquals(sameFlagsDifferentInstances1.hashCode(), sameFlagsDifferentInstances2.hashCode());
    assertNotEquals(justOneFlag, sameFlagsDifferentInstances1);
    assertNotEquals(sameFlagsDifferentInstances1, sameFlagsDifferentMetadata);
    
    assertNotEquals(noFlagsButValid, noFlagsAndNotValid);
    assertNotEquals(noFlagsButValid, "");
  }
  
  @Test
  public void equalMetadataInstancesAreEqual() {
    // Testing this various cases is easier at a low level - equalInstancesAreEqual() above already
    // verifies that we test for metadata equality in general
    List<TypeBehavior.ValueFactory<FeatureFlagsState.FlagMetadata>> allPermutations = new ArrayList<>();
    for (LDValue value: new LDValue[] { LDValue.of(1), LDValue.of(2) }) {
      for (Integer variation: new Integer[] { null, 0, 1 }) {
        for (EvaluationReason reason: new EvaluationReason[] { null, EvaluationReason.off(), EvaluationReason.fallthrough() }) {
          for (Integer version: new Integer[] { null, 10, 11 }) {
            for (boolean trackEvents: new boolean[] { false, true }) {
              for (boolean trackReason: new boolean[] { false, true }) {
                for (Long debugEventsUntilDate: new Long[] { null, 1000L, 1001L }) {
                  allPermutations.add(() -> new FeatureFlagsState.FlagMetadata(
                      value, variation, reason, version, trackEvents, trackReason, debugEventsUntilDate));
                }
              }
            }
          }
        }
      }
    }
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
  
  @Test
  public void optionsHaveHumanReadableNames() {
    assertEquals("CLIENT_SIDE_ONLY", FlagsStateOption.CLIENT_SIDE_ONLY.toString());
    assertEquals("WITH_REASONS", FlagsStateOption.WITH_REASONS.toString());
    assertEquals("DETAILS_ONLY_FOR_TRACKED_FLAGS", FlagsStateOption.DETAILS_ONLY_FOR_TRACKED_FLAGS.toString());
  }
  
  @Test
  public void canConvertToJson() {
    String actualJsonString = JsonSerialization.serialize(makeInstanceForSerialization());
    assertJsonEquals(makeExpectedJsonSerialization(), actualJsonString);
  }
  
  @Test
  public void canConvertFromJson() throws SerializationException {
    FeatureFlagsState state = JsonSerialization.deserialize(makeExpectedJsonSerialization(), FeatureFlagsState.class);
    assertEquals(makeInstanceForSerialization(), state);
  }
  
  private static FeatureFlagsState makeInstanceForSerialization() {
    EvalResult eval1 = EvalResult.of(LDValue.of("value1"), 0, EvaluationReason.off());
    DataModel.FeatureFlag flag1 = flagBuilder("key1").version(100).trackEvents(false).build();
    EvalResult eval2 = EvalResult.of(LDValue.of("value2"), 1, EvaluationReason.fallthrough());
    DataModel.FeatureFlag flag2 = flagBuilder("key2").version(200).trackEvents(true).debugEventsUntilDate(1000L).build();
    EvalResult eval3 = EvalResult.of(LDValue.ofNull(), NO_VARIATION, EvaluationReason.error(MALFORMED_FLAG));
    DataModel.FeatureFlag flag3 = flagBuilder("key3").version(300).build();
    return FeatureFlagsState.builder(FlagsStateOption.WITH_REASONS)
        .addFlag(flag1, eval1).addFlag(flag2, eval2).addFlag(flag3, eval3).build();
  }
  
  private static String makeExpectedJsonSerialization() {
    return "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":null," +
        "\"$flagsState\":{" +
        "\"key1\":{" +
          "\"variation\":0,\"version\":100,\"reason\":{\"kind\":\"OFF\"}" +  // note, "trackEvents: false" is omitted
        "},\"key2\":{" +
          "\"variation\":1,\"version\":200,\"reason\":{\"kind\":\"FALLTHROUGH\"},\"trackEvents\":true,\"debugEventsUntilDate\":1000" +
        "},\"key3\":{" +
          "\"version\":300,\"reason\":{\"kind\":\"ERROR\",\"errorKind\":\"MALFORMED_FLAG\"}" +
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
    assertJsonEquals(makeExpectedJsonSerialization(), actualJsonString);
    
    FeatureFlagsState state = jacksonMapper.readValue(makeExpectedJsonSerialization(), FeatureFlagsState.class);
    assertEquals(makeInstanceForSerialization(), state);
  }
}
