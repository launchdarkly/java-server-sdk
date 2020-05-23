package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;

import org.junit.Test;

import java.time.Duration;
import java.util.Map;

import static com.google.common.collect.Iterables.getFirst;
import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestComponents.dataStoreThatThrowsException;
import static com.launchdarkly.sdk.server.TestComponents.failedDataSource;
import static com.launchdarkly.sdk.server.TestComponents.initedDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificDataSource;
import static com.launchdarkly.sdk.server.TestComponents.specificDataStore;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static com.launchdarkly.sdk.server.TestUtil.upsertSegment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientEvaluationTest {
  private static final LDUser user = new LDUser("userkey");
  private static final LDUser userWithNullKey = new LDUser.Builder((String)null).build();
  private static final Gson gson = new Gson();
  
  private DataStore dataStore = initedDataStore();

  private LDConfig config = new LDConfig.Builder()
      .dataStore(specificDataStore(dataStore))
      .events(Components.noEvents())
      .dataSource(Components.externalUpdatesOnly())
      .build();
  private LDClientInterface client = new LDClient("SDK_KEY", config);
  
  @Test
  public void boolVariationReturnsFlagValue() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    assertTrue(client.boolVariation("key", user, false));
  }

  @Test
  public void boolVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertFalse(client.boolVariation("key", user, false));
  }
  
  @Test
  public void boolVariationReturnsDefaultValueForWrongType() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("wrong")));

    assertFalse(client.boolVariation("key", user, false));
  }
  
  @Test
  public void intVariationReturnsFlagValue() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(2)));

    assertEquals(2, client.intVariation("key", user, 1));
  }

  @Test
  public void intVariationReturnsFlagValueEvenIfEncodedAsDouble() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(2.0)));

    assertEquals(2, client.intVariation("key", user, 1));
  }

  @Test
  public void intVariationFromDoubleRoundsTowardZero() throws Exception {
    upsertFlag(dataStore, flagWithValue("flag1", LDValue.of(2.25)));
    upsertFlag(dataStore, flagWithValue("flag2", LDValue.of(2.75)));
    upsertFlag(dataStore, flagWithValue("flag3", LDValue.of(-2.25)));
    upsertFlag(dataStore, flagWithValue("flag4", LDValue.of(-2.75)));

    assertEquals(2, client.intVariation("flag1", user, 1));
    assertEquals(2, client.intVariation("flag2", user, 1));
    assertEquals(-2, client.intVariation("flag3", user, 1));
    assertEquals(-2, client.intVariation("flag4", user, 1));
  }
  
  @Test
  public void intVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals(1, client.intVariation("key", user, 1));
  }

  @Test
  public void intVariationReturnsDefaultValueForWrongType() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("wrong")));

    assertEquals(1, client.intVariation("key", user, 1));
  }
  
  @Test
  public void doubleVariationReturnsFlagValue() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(2.5d)));

    assertEquals(2.5d, client.doubleVariation("key", user, 1.0d), 0d);
  }

  @Test
  public void doubleVariationReturnsFlagValueEvenIfEncodedAsInt() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(2)));

    assertEquals(2.0d, client.doubleVariation("key", user, 1.0d), 0d);
  }

  @Test
  public void doubleVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals(1.0d, client.doubleVariation("key", user, 1.0d), 0d);
  }

  @Test
  public void doubleVariationReturnsDefaultValueForWrongType() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("wrong")));

    assertEquals(1.0d, client.doubleVariation("key", user, 1.0d), 0d);
  }
  
  @Test
  public void stringVariationReturnsFlagValue() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("b")));

    assertEquals("b", client.stringVariation("key", user, "a"));
  }

  @Test
  public void stringVariationWithNullDefaultReturnsFlagValue() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("b")));

    assertEquals("b", client.stringVariation("key", user, null));
  }

  @Test
  public void stringVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals("a", client.stringVariation("key", user, "a"));
  }

  @Test
  public void stringVariationWithNullDefaultReturnsDefaultValueForUnknownFlag() throws Exception {
    assertNull(client.stringVariation("key", user, null));
  }

  @Test
  public void stringVariationReturnsDefaultValueForWrongType() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    assertEquals("a", client.stringVariation("key", user, "a"));
  }
  
  @Test
  public void jsonValueVariationReturnsFlagValue() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    upsertFlag(dataStore, flagWithValue("key", data));
    
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
    DataModel.Segment segment = segmentBuilder("segment1")
        .version(1)
        .included(user.getKey())
        .build();
    upsertSegment(dataStore, segment);
    
    DataModel.Clause clause = clause(null, DataModel.Operator.segmentMatch, LDValue.of("segment1"));
    DataModel.FeatureFlag feature = booleanFlagWithClauses("feature", clause);
    upsertFlag(dataStore, feature);
    
    assertTrue(client.boolVariation("feature", user, false));
  }
  
  @Test
  public void canGetDetailsForSuccessfulEvaluation() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(true,
        0, EvaluationReason.off());
    assertEquals(expectedResult, client.boolVariationDetail("key", user, false));
  }
  
  @Test
  public void variationReturnsDefaultIfFlagEvaluatesToNull() {
    DataModel.FeatureFlag flag = flagBuilder("key").on(false).offVariation(null).build();
    upsertFlag(dataStore, flag);
    
    assertEquals("default", client.stringVariation("key", user, "default"));
  }
  
  @Test
  public void variationDetailReturnsDefaultIfFlagEvaluatesToNull() {
    DataModel.FeatureFlag flag = flagBuilder("key").on(false).offVariation(null).build();
    upsertFlag(dataStore, flag);
    
    EvaluationDetail<String> expected = EvaluationDetail.fromValue("default",
        NO_VARIATION, EvaluationReason.off());
    EvaluationDetail<String> actual = client.stringVariationDetail("key", user, "default");
    assertEquals(expected, actual);
    assertTrue(actual.isDefaultValue());
  }
  
  @Test
  public void appropriateErrorIfClientNotInitialized() throws Exception {
    DataStore badDataStore = new InMemoryDataStore();
    LDConfig badConfig = new LDConfig.Builder()
        .dataStore(specificDataStore(badDataStore))
        .events(Components.noEvents())
        .dataSource(specificDataSource(failedDataSource()))
        .startWait(Duration.ZERO)
        .build();
    try (LDClientInterface badClient = new LDClient("SDK_KEY", badConfig)) {
      EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(false, NO_VARIATION,
          EvaluationReason.error(EvaluationReason.ErrorKind.CLIENT_NOT_READY));
      assertEquals(expectedResult, badClient.boolVariationDetail("key", user, false));
    }
  }
  
  @Test
  public void appropriateErrorIfFlagDoesNotExist() throws Exception {
    EvaluationDetail<String> expectedResult = EvaluationDetail.fromValue("default", NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND));
    assertEquals(expectedResult, client.stringVariationDetail("key", user, "default"));
  }
  
  @Test
  public void appropriateErrorIfUserNotSpecified() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<String> expectedResult = EvaluationDetail.fromValue("default", NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED));
    assertEquals(expectedResult, client.stringVariationDetail("key", null, "default"));
  }
  
  @Test
  public void appropriateErrorIfValueWrongType() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<Integer> expectedResult = EvaluationDetail.fromValue(3, NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.WRONG_TYPE));
    assertEquals(expectedResult, client.intVariationDetail("key", user, 3));
  }
  
  @Test
  public void appropriateErrorForUnexpectedException() throws Exception {
    RuntimeException exception = new RuntimeException("sorry");
    DataStore badDataStore = dataStoreThatThrowsException(exception);
    LDConfig badConfig = new LDConfig.Builder()
        .dataStore(specificDataStore(badDataStore))
        .events(Components.noEvents())
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClientInterface badClient = new LDClient("SDK_KEY", badConfig)) {
      EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(false, NO_VARIATION,
          EvaluationReason.exception(exception));
      assertEquals(expectedResult, badClient.boolVariationDetail("key", user, false));
    }
  }
  
  @Test
  public void allFlagsStateReturnsState() throws Exception {
    DataModel.FeatureFlag flag1 = flagBuilder("key1")
        .version(100)
        .trackEvents(false)
        .on(false)
        .offVariation(0)
        .variations(LDValue.of("value1"))
        .build();
    DataModel.FeatureFlag flag2 = flagBuilder("key2")
        .version(200)
        .trackEvents(true)
        .debugEventsUntilDate(1000L)
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("off"), LDValue.of("value2"))
        .build();
    upsertFlag(dataStore, flag1);
    upsertFlag(dataStore, flag2);

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
    DataModel.FeatureFlag flag1 = flagBuilder("server-side-1").build();
    DataModel.FeatureFlag flag2 = flagBuilder("server-side-2").build();
    DataModel.FeatureFlag flag3 = flagBuilder("client-side-1").clientSide(true)
        .variations(LDValue.of("value1")).offVariation(0).build();
    DataModel.FeatureFlag flag4 = flagBuilder("client-side-2").clientSide(true)
        .variations(LDValue.of("value2")).offVariation(0).build();
    upsertFlag(dataStore, flag1);
    upsertFlag(dataStore, flag2);
    upsertFlag(dataStore, flag3);
    upsertFlag(dataStore, flag4);

    FeatureFlagsState state = client.allFlagsState(user, FlagsStateOption.CLIENT_SIDE_ONLY);
    assertTrue(state.isValid());
    
    Map<String, LDValue> allValues = state.toValuesMap();
    assertEquals(ImmutableMap.<String, LDValue>of("client-side-1", LDValue.of("value1"), "client-side-2", LDValue.of("value2")), allValues);
  }
  
  @Test
  public void allFlagsStateReturnsStateWithReasons() {
    DataModel.FeatureFlag flag1 = flagBuilder("key1")
        .version(100)
        .trackEvents(false)
        .on(false)
        .offVariation(0)
        .variations(LDValue.of("value1"))
        .build();
    DataModel.FeatureFlag flag2 = flagBuilder("key2")
        .version(200)
        .trackEvents(true)
        .debugEventsUntilDate(1000L)
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("off"), LDValue.of("value2"))
        .build();
    upsertFlag(dataStore, flag1);
    upsertFlag(dataStore, flag2);

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
    DataModel.FeatureFlag flag1 = flagBuilder("key1")
        .version(100)
        .trackEvents(false)
        .on(false)
        .offVariation(0)
        .variations(LDValue.of("value1"))
        .build();
    DataModel.FeatureFlag flag2 = flagBuilder("key2")
        .version(200)
        .trackEvents(true)
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("off"), LDValue.of("value2"))
        .build();
    DataModel.FeatureFlag flag3 = flagBuilder("key3")
        .version(300)
        .trackEvents(false)
        .debugEventsUntilDate(futureTime)  // event tracking is turned on temporarily even though trackEvents is false 
        .on(false)
        .offVariation(0)
        .variations(LDValue.of("value3"))
        .build();
    upsertFlag(dataStore, flag1);
    upsertFlag(dataStore, flag2);
    upsertFlag(dataStore, flag3);

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
  public void allFlagsStateFiltersOutDeletedFlags() throws Exception {
    DataModel.FeatureFlag flag1 = flagBuilder("key1").version(1).build();
    DataModel.FeatureFlag flag2 = flagBuilder("key2").version(1).build();
    upsertFlag(dataStore, flag1);
    upsertFlag(dataStore, flag2);
    dataStore.upsert(FEATURES, flag2.getKey(), ItemDescriptor.deletedItem(flag2.getVersion() + 1));

    FeatureFlagsState state = client.allFlagsState(user);
    assertTrue(state.isValid());
    
    Map<String, LDValue> valuesMap = state.toValuesMap();
    assertEquals(1, valuesMap.size());
    assertEquals(flag1.getKey(), getFirst(valuesMap.keySet(), null));
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateForNullUser() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("value")));

    FeatureFlagsState state = client.allFlagsState(null);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateForNullUserKey() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("value")));

    FeatureFlagsState state = client.allFlagsState(userWithNullKey);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }
}
