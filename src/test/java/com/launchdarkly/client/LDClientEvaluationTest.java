package com.launchdarkly.client;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static com.launchdarkly.client.TestUtil.booleanFlagWithClauses;
import static com.launchdarkly.client.TestUtil.failedUpdateProcessor;
import static com.launchdarkly.client.TestUtil.fallthroughVariation;
import static com.launchdarkly.client.TestUtil.featureStoreThatThrowsException;
import static com.launchdarkly.client.TestUtil.flagWithValue;
import static com.launchdarkly.client.TestUtil.jbool;
import static com.launchdarkly.client.TestUtil.jdouble;
import static com.launchdarkly.client.TestUtil.jint;
import static com.launchdarkly.client.TestUtil.js;
import static com.launchdarkly.client.TestUtil.specificFeatureStore;
import static com.launchdarkly.client.TestUtil.specificUpdateProcessor;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    featureStore.upsert(FEATURES, flagWithValue("key", jbool(true)));

    assertTrue(client.boolVariation("key", user, false));
  }

  @Test
  public void boolVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertFalse(client.boolVariation("key", user, false));
  }
  
  @Test
  public void intVariationReturnsFlagValue() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", jint(2)));

    assertEquals(new Integer(2), client.intVariation("key", user, 1));
  }

  @Test
  public void intVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals(new Integer(1), client.intVariation("key", user, 1));
  }

  @Test
  public void doubleVariationReturnsFlagValue() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", jdouble(2.5d)));

    assertEquals(new Double(2.5d), client.doubleVariation("key", user, 1.0d));
  }

  @Test
  public void doubleVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals(new Double(1.0d), client.doubleVariation("key", user, 1.0d));
  }

  @Test
  public void stringVariationReturnsFlagValue() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", js("b")));

    assertEquals("b", client.stringVariation("key", user, "a"));
  }

  @Test
  public void stringVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals("a", client.stringVariation("key", user, "a"));
  }

  @Test
  public void jsonVariationReturnsFlagValue() throws Exception {
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    featureStore.upsert(FEATURES, flagWithValue("key", data));
    
    assertEquals(data, client.jsonVariation("key", user, jint(42)));
  }
  
  @Test
  public void jsonVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    JsonElement defaultVal = jint(42);
    assertEquals(defaultVal, client.jsonVariation("key", user, defaultVal));
  }
  
  @Test
  public void canMatchUserBySegment() throws Exception {
    // This is similar to one of the tests in FeatureFlagTest, but more end-to-end
    Segment segment = new Segment.Builder("segment1")
        .version(1)
        .included(Arrays.asList(user.getKeyAsString()))
        .build();
    featureStore.upsert(SEGMENTS, segment);
    
    Clause clause = new Clause("", Operator.segmentMatch, Arrays.asList(js("segment1")), false);
    FeatureFlag feature = booleanFlagWithClauses("feature", clause);
    featureStore.upsert(FEATURES, feature);
    
    assertTrue(client.boolVariation("feature", user, false));
  }
  
  @Test
  public void canGetDetailsForSuccessfulEvaluation() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", jbool(true)));

    EvaluationDetail<Boolean> expectedResult = new EvaluationDetail<>(EvaluationReason.off(), 0, true);
    assertEquals(expectedResult, client.boolVariationDetail("key", user, false));
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
      EvaluationDetail<Boolean> expectedResult = EvaluationDetail.error(EvaluationReason.ErrorKind.CLIENT_NOT_READY, false);
      assertEquals(expectedResult, badClient.boolVariationDetail("key", user, false));
    }
  }
  
  @Test
  public void appropriateErrorIfFlagDoesNotExist() throws Exception {
    EvaluationDetail<Boolean> expectedResult = EvaluationDetail.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND, false);
    assertEquals(expectedResult, client.boolVariationDetail("key", user, false));
  }
  
  @Test
  public void appropriateErrorIfUserNotSpecified() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", jbool(true)));

    EvaluationDetail<Boolean> expectedResult = EvaluationDetail.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED, false);
    assertEquals(expectedResult, client.boolVariationDetail("key", null, false));
  }
  
  @Test
  public void appropriateErrorIfValueWrongType() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", jbool(true)));

    EvaluationDetail<Integer> expectedResult = EvaluationDetail.error(EvaluationReason.ErrorKind.WRONG_TYPE, 3);
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
      EvaluationDetail<Boolean> expectedResult = EvaluationDetail.error(EvaluationReason.ErrorKind.EXCEPTION, false);
      assertEquals(expectedResult, badClient.boolVariationDetail("key", user, false));
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void allFlagsReturnsFlagValues() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key1", js("value1")));
    featureStore.upsert(FEATURES, flagWithValue("key2", js("value2")));
    
    Map<String, JsonElement> result = client.allFlags(user);
    assertEquals(ImmutableMap.<String, JsonElement>of("key1", js("value1"), "key2", js("value2")), result);
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void allFlagsReturnsNullForNullUser() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", js("value")));

    assertNull(client.allFlags(null));
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void allFlagsReturnsNullForNullUserKey() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", js("value")));

    assertNull(client.allFlags(userWithNullKey));
  }
  
  @Test
  public void allFlagsStateReturnsState() throws Exception {
    FeatureFlag flag1 = new FeatureFlagBuilder("key1")
        .version(100)
        .trackEvents(false)
        .on(false)
        .offVariation(0)
        .variations(js("value1"))
        .build();
    FeatureFlag flag2 = new FeatureFlagBuilder("key2")
        .version(200)
        .trackEvents(true)
        .debugEventsUntilDate(1000L)
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(js("off"), js("value2"))
        .build();
    featureStore.upsert(FEATURES, flag1);
    featureStore.upsert(FEATURES, flag2);

    FeatureFlagsState state = client.allFlagsState(user);
    assertTrue(state.isValid());
    
    String json = "{\"key1\":\"value1\",\"key2\":\"value2\"," +
        "\"$flagsState\":{" +
          "\"key1\":{" +
            "\"variation\":0,\"version\":100,\"trackEvents\":false" +
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
        .variations(js("value1")).offVariation(0).build();
    FeatureFlag flag4 = new FeatureFlagBuilder("client-side-2").clientSide(true)
        .variations(js("value2")).offVariation(0).build();
    featureStore.upsert(FEATURES, flag1);
    featureStore.upsert(FEATURES, flag2);
    featureStore.upsert(FEATURES, flag3);
    featureStore.upsert(FEATURES, flag4);

    FeatureFlagsState state = client.allFlagsState(user, FlagsStateOption.CLIENT_SIDE_ONLY);
    assertTrue(state.isValid());
    
    Map<String, JsonElement> allValues = state.toValuesMap();
    assertEquals(ImmutableMap.<String, JsonElement>of("client-side-1", js("value1"), "client-side-2", js("value2")), allValues);
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateForNullUser() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", js("value")));

    FeatureFlagsState state = client.allFlagsState(null);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateForNullUserKey() throws Exception {
    featureStore.upsert(FEATURES, flagWithValue("key", js("value")));

    FeatureFlagsState state = client.allFlagsState(userWithNullKey);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }
}
