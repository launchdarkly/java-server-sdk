package com.launchdarkly.client;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static com.launchdarkly.client.TestUtil.booleanFlagWithClauses;
import static com.launchdarkly.client.TestUtil.failedUpdateProcessor;
import static com.launchdarkly.client.TestUtil.fallthroughVariation;
import static com.launchdarkly.client.TestUtil.featureStoreThatThrowsException;
import static com.launchdarkly.client.TestUtil.flagWithValue;
import static com.launchdarkly.client.TestUtil.specificFeatureStore;
import static com.launchdarkly.client.TestUtil.specificUpdateProcessor;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientEvaluationTest {
  private static final LDUser user = new LDUser("userkey");
  private static final LDUser userWithNullKey = new LDUser.Builder((String)null).build();
  private static final Gson gson = new Gson();
  
  private FeatureStore featureStore = TestUtil.initedFeatureStore();

  private LDConfig config = new LDConfig.Builder()
      .featureStoreFactory(specificFeatureStore(featureStore))
      .eventProcessorFactory(Components.nullEventProcessor())
      .updateProcessorFactory(Components.nullUpdateProcessor())
      .build();
  private LDClientInterface client = new LDClient("SDK_KEY", config);
  
  @Test
  public void boolVariationReturnsFlagValue() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(true)));

    assertTrue(client.boolVariation("key", user, false));
  }

  @Test
  public void boolVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertFalse(client.boolVariation("key", user, false));
  }
  
  @Test
  public void boolVariationReturnsDefaultValueForWrongType() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of("wrong")));

    assertFalse(client.boolVariation("key", user, false));
  }
  
  @Test
  public void intVariationReturnsFlagValue() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(2)));

    assertEquals(new Integer(2), client.intVariation("key", user, 1));
  }

  @Test
  public void intVariationReturnsFlagValueEvenIfEncodedAsDouble() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(2.0)));

    assertEquals(new Integer(2), client.intVariation("key", user, 1));
  }

  @Test
  public void intVariationFromDoubleRoundsTowardZero() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("flag1", LDValue.of(2.25)));
    featureStore.upsert(FEATURES, flagWithValue("flag2", LDValue.of(2.75)));
    featureStore.upsert(FEATURES, flagWithValue("flag3", LDValue.of(-2.25)));
    featureStore.upsert(FEATURES, flagWithValue("flag4", LDValue.of(-2.75)));

    assertEquals(new Integer(2), client.intVariation("flag1", user, 1));
    assertEquals(new Integer(2), client.intVariation("flag2", user, 1));
    assertEquals(new Integer(-2), client.intVariation("flag3", user, 1));
    assertEquals(new Integer(-2), client.intVariation("flag4", user, 1));
  }
  
  @Test
  public void intVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals(new Integer(1), client.intVariation("key", user, 1));
  }

  @Test
  public void intVariationReturnsDefaultValueForWrongType() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of("wrong")));

    assertEquals(new Integer(1), client.intVariation("key", user, 1));
  }
  
  @Test
  public void doubleVariationReturnsFlagValue() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(2.5d)));

    assertEquals(new Double(2.5d), client.doubleVariation("key", user, 1.0d));
  }

  @Test
  public void doubleVariationReturnsFlagValueEvenIfEncodedAsInt() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(2)));

    assertEquals(new Double(2.0d), client.doubleVariation("key", user, 1.0d));
  }

  @Test
  public void doubleVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals(new Double(1.0d), client.doubleVariation("key", user, 1.0d));
  }

  @Test
  public void doubleVariationReturnsDefaultValueForWrongType() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of("wrong")));

    assertEquals(new Double(1.0d), client.doubleVariation("key", user, 1.0d));
  }
  
  @Test
  public void stringVariationReturnsFlagValue() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of("b")));

    assertEquals("b", client.stringVariation("key", user, "a"));
  }

  @Test
  public void stringVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals("a", client.stringVariation("key", user, "a"));
  }

  @Test
  public void stringVariationReturnsDefaultValueForWrongType() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(true)));

    assertEquals("a", client.stringVariation("key", user, "a"));
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void deprecatedJsonVariationReturnsFlagValue() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    featureStore.upsert(FEATURES, flagWithValue("key", data));
    
    assertEquals(data.asJsonElement(), client.jsonVariation("key", user, new JsonPrimitive(42)));
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void deprecatedJsonVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    JsonElement defaultVal = new JsonPrimitive(42);
    assertEquals(defaultVal, client.jsonVariation("key", user, defaultVal));
  }

  @Test
  public void jsonValueVariationReturnsFlagValue() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    featureStore.upsert(FEATURES, flagWithValue("key", data));
    
    assertEquals(data, client.jsonValueVariation("key", user, LDValue.of(42)));
  }
  
  @Test
  public void jsonValueVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    LDValue defaultVal = LDValue.of(42);
    assertEquals(defaultVal, client.jsonValueVariation("key", user, defaultVal));
  }
  
  @Test
  public void canMatchUserBySegment() throws Exception {
    // This is similar to one of the tests in FeatureFlagTest, but more end-to-end
    Segment segment = new Segment.Builder("segment1")
        .version(1)
        .included(Arrays.asList(user.getKeyAsString()))
        .build();
    featureStore.upsert(SEGMENTS, segment);
    
    Clause clause = new Clause("", Operator.segmentMatch, Arrays.asList(LDValue.of("segment1")), false);
    FeatureFlag feature = booleanFlagWithClauses("feature", clause);
    featureStore.upsert(FEATURES, feature);
    
    assertTrue(client.boolVariation("feature", user, false));
  }
  
  @Test
  public void canGetDetailsForSuccessfulEvaluation() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(true,
        0, EvaluationReason.off());
    assertEquals(expectedResult, client.boolVariationDetail("key", user, false));
  }
  
  @Test
  public void variationReturnsDefaultIfFlagEvaluatesToNull() {
    FeatureFlag flag = new FeatureFlagBuilder("key").on(false).offVariation(null).build();
    featureStore.upsert(FEATURES, flag);
    
    assertEquals("default", client.stringVariation("key", user, "default"));
  }
  
  @Test
  public void variationDetailReturnsDefaultIfFlagEvaluatesToNull() {
    FeatureFlag flag = new FeatureFlagBuilder("key").on(false).offVariation(null).build();
    featureStore.upsert(FEATURES, flag);
    
    EvaluationDetail<String> expected = EvaluationDetail.fromValue("default",
        null, EvaluationReason.off());
    EvaluationDetail<String> actual = client.stringVariationDetail("key", user, "default");
    assertEquals(expected, actual);
    assertTrue(actual.isDefaultValue());
  }
  
  @Test
  public void appropriateErrorIfClientNotInitialized() throws Exception {
    FeatureStore badFeatureStore = new InMemoryFeatureStore();
    LDConfig badConfig = new LDConfig.Builder()
        .featureStoreFactory(specificFeatureStore(badFeatureStore))
        .eventProcessorFactory(Components.nullEventProcessor())
        .updateProcessorFactory(specificUpdateProcessor(failedUpdateProcessor()))
        .startWaitMillis(0)
        .build();
    try (LDClientInterface badClient = new LDClient("SDK_KEY", badConfig)) {
      EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(false, null,
          EvaluationReason.error(EvaluationReason.ErrorKind.CLIENT_NOT_READY));
      assertEquals(expectedResult, badClient.boolVariationDetail("key", user, false));
    }
  }
  
  @Test
  public void appropriateErrorIfFlagDoesNotExist() throws Exception {
    EvaluationDetail<String> expectedResult = EvaluationDetail.fromValue("default", null,
        EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND));
    assertEquals(expectedResult, client.stringVariationDetail("key", user, "default"));
  }
  
  @Test
  public void appropriateErrorIfUserNotSpecified() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<String> expectedResult = EvaluationDetail.fromValue("default", null,
        EvaluationReason.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED));
    assertEquals(expectedResult, client.stringVariationDetail("key", null, "default"));
  }
  
  @Test
  public void appropriateErrorIfValueWrongType() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<Integer> expectedResult = EvaluationDetail.fromValue(3, null,
        EvaluationReason.error(EvaluationReason.ErrorKind.WRONG_TYPE));
    assertEquals(expectedResult, client.intVariationDetail("key", user, 3));
  }
  
  @Test
  public void appropriateErrorForUnexpectedException() throws Exception {
    FeatureStore badFeatureStore = featureStoreThatThrowsException(new RuntimeException("sorry"));
    LDConfig badConfig = new LDConfig.Builder()
        .featureStoreFactory(specificFeatureStore(badFeatureStore))
        .eventProcessorFactory(Components.nullEventProcessor())
        .updateProcessorFactory(Components.nullUpdateProcessor())
        .build();
    try (LDClientInterface badClient = new LDClient("SDK_KEY", badConfig)) {
      EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(false, null,
          EvaluationReason.error(EvaluationReason.ErrorKind.EXCEPTION));
      assertEquals(expectedResult, badClient.boolVariationDetail("key", user, false));
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void allFlagsReturnsFlagValues() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key1", LDValue.of("value1")));
    featureStore.upsert(FEATURES, flagWithValue("key2", LDValue.of("value2")));
    
    Map<String, JsonElement> result = client.allFlags(user);
    assertEquals(ImmutableMap.<String, JsonElement>of("key1", new JsonPrimitive("value1"), "key2", new JsonPrimitive("value2")), result);
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void allFlagsReturnsNullForNullUser() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of("value")));

    assertNull(client.allFlags(null));
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void allFlagsReturnsNullForNullUserKey() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of("value")));

    assertNull(client.allFlags(userWithNullKey));
  }
  
  @Test
  public void allFlagsStateReturnsState() throws Exception {
    FeatureFlag flag1 = new FeatureFlagBuilder("key1")
        .version(100)
        .trackEvents(false)
        .on(false)
        .offVariation(0)
        .variations(LDValue.of("value1"))
        .build();
    FeatureFlag flag2 = new FeatureFlagBuilder("key2")
        .version(200)
        .trackEvents(true)
        .debugEventsUntilDate(1000L)
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("off"), LDValue.of("value2"))
        .build();
    featureStore.upsert(FEATURES, flag1);
    featureStore.upsert(FEATURES, flag2);

    FeatureFlagsState state = client.allFlagsState(user);
    assertTrue(state.isValid());
    
    String json = "{\"key1\":\"value1\",\"key2\":\"value2\"," +
        "\"$flagsState\":{" +
          "\"key1\":{" +
            "\"variation\":0,\"version\":100" +
          "},\"key2\":{" +
            "\"variation\":1,\"version\":200,\"trackEvents\":true,\"debugEventsUntilDate\":1000" +
          "}" +
        "}," +
        "\"$valid\":true" +
      "}";
    JsonElement expected = gson.fromJson(json, JsonElement.class);
    assertEquals(expected, gson.toJsonTree(state));
  }

  @Test
  public void allFlagsStateCanFilterForOnlyClientSideFlags() {
    FeatureFlag flag1 = new FeatureFlagBuilder("server-side-1").build();
    FeatureFlag flag2 = new FeatureFlagBuilder("server-side-2").build();
    FeatureFlag flag3 = new FeatureFlagBuilder("client-side-1").clientSide(true)
        .variations(LDValue.of("value1")).offVariation(0).build();
    FeatureFlag flag4 = new FeatureFlagBuilder("client-side-2").clientSide(true)
        .variations(LDValue.of("value2")).offVariation(0).build();
    featureStore.upsert(FEATURES, flag1);
    featureStore.upsert(FEATURES, flag2);
    featureStore.upsert(FEATURES, flag3);
    featureStore.upsert(FEATURES, flag4);

    FeatureFlagsState state = client.allFlagsState(user, FlagsStateOption.CLIENT_SIDE_ONLY);
    assertTrue(state.isValid());
    
    Map<String, JsonElement> allValues = state.toValuesMap();
    assertEquals(ImmutableMap.<String, JsonElement>of("client-side-1", new JsonPrimitive("value1"), "client-side-2", new JsonPrimitive("value2")), allValues);
  }
  
  @Test
  public void allFlagsStateReturnsStateWithReasons() {
    FeatureFlag flag1 = new FeatureFlagBuilder("key1")
        .version(100)
        .trackEvents(false)
        .on(false)
        .offVariation(0)
        .variations(LDValue.of("value1"))
        .build();
    FeatureFlag flag2 = new FeatureFlagBuilder("key2")
        .version(200)
        .trackEvents(true)
        .debugEventsUntilDate(1000L)
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("off"), LDValue.of("value2"))
        .build();
    featureStore.upsert(FEATURES, flag1);
    featureStore.upsert(FEATURES, flag2);

    FeatureFlagsState state = client.allFlagsState(user, FlagsStateOption.WITH_REASONS);
    assertTrue(state.isValid());
    
    String json = "{\"key1\":\"value1\",\"key2\":\"value2\"," +
        "\"$flagsState\":{" +
          "\"key1\":{" +
            "\"variation\":0,\"version\":100,\"reason\":{\"kind\":\"OFF\"}" +
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
  public void allFlagsStateCanOmitDetailsForUntrackedFlags() {
    long futureTime = System.currentTimeMillis() + 1000000;
    FeatureFlag flag1 = new FeatureFlagBuilder("key1")
        .version(100)
        .trackEvents(false)
        .on(false)
        .offVariation(0)
        .variations(LDValue.of("value1"))
        .build();
    FeatureFlag flag2 = new FeatureFlagBuilder("key2")
        .version(200)
        .trackEvents(true)
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("off"), LDValue.of("value2"))
        .build();
    FeatureFlag flag3 = new FeatureFlagBuilder("key3")
        .version(300)
        .trackEvents(false)
        .debugEventsUntilDate(futureTime)  // event tracking is turned on temporarily even though trackEvents is false 
        .on(false)
        .offVariation(0)
        .variations(LDValue.of("value3"))
        .build();
    featureStore.upsert(FEATURES, flag1);
    featureStore.upsert(FEATURES, flag2);
    featureStore.upsert(FEATURES, flag3);

    FeatureFlagsState state = client.allFlagsState(user, FlagsStateOption.WITH_REASONS, FlagsStateOption.DETAILS_ONLY_FOR_TRACKED_FLAGS);
    assertTrue(state.isValid());
    
    String json = "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"," +
        "\"$flagsState\":{" +
          "\"key1\":{" +
            "\"variation\":0" +  // note, version and reason are omitted, and so is trackEvents: false
          "},\"key2\":{" +
            "\"variation\":1,\"version\":200,\"reason\":{\"kind\":\"FALLTHROUGH\"},\"trackEvents\":true" +
          "},\"key3\":{" +
            "\"variation\":0,\"version\":300,\"reason\":{\"kind\":\"OFF\"},\"debugEventsUntilDate\":" + futureTime +
          "}" +
        "}," +
        "\"$valid\":true" +
      "}";
    JsonElement expected = gson.fromJson(json, JsonElement.class);
    assertEquals(expected, gson.toJsonTree(state));    
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateForNullUser() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of("value")));

    FeatureFlagsState state = client.allFlagsState(null);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateForNullUserKey() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", LDValue.of("value")));

    FeatureFlagsState state = client.allFlagsState(userWithNullKey);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }
}
