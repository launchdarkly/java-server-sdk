package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.TestData;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;

import org.junit.Test;

import java.time.Duration;
import java.util.Map;

import static com.google.common.collect.Iterables.getFirst;
import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.Evaluator.EXPECTED_EXCEPTION_FROM_INVALID_FLAG;
import static com.launchdarkly.sdk.server.Evaluator.INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION;
import static com.launchdarkly.sdk.server.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingSegment;
import static com.launchdarkly.sdk.server.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestComponents.dataStoreThatThrowsException;
import static com.launchdarkly.sdk.server.TestComponents.failedDataSource;
import static com.launchdarkly.sdk.server.TestComponents.initedDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificComponent;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static com.launchdarkly.sdk.server.TestUtil.upsertSegment;
import static com.launchdarkly.testhelpers.JsonAssertions.assertJsonEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientEvaluationTest extends BaseTest {
  private static final LDContext context = LDContext.create("userkey");
  private static final LDContext invalidContext = LDContext.create(null);
  private static final Gson gson = new Gson();
  
  private DataStore dataStore = initedDataStore();

  private LDConfig config = baseConfig()
      .dataStore(specificComponent(dataStore))
      .build();
  private LDClientInterface client = new LDClient("SDK_KEY", config);

  public interface EvalMethod<ContextT, ValueT> {
    public ValueT apply(LDClientInterface client, String flagKey, ContextT contextOrUser, ValueT defaultVal);
  }

  public interface EvalDetailMethod<ContextT, ValueT> {
    public EvaluationDetail<ValueT> apply(LDClientInterface client, String flagKey, ContextT contextOrUser, ValueT defaultVal);
  }

  private <T> void doTypedVariationTests(
      EvalMethod<LDContext, T> variationMethod,
      EvalDetailMethod<LDContext, T> variationDetailMethod,
      T expectedValue,
      LDValue expectedLdValue,
      T defaultValue,
      LDValue wrongTypeLdValue
      )
  {
      String flagKey = "flagkey",
          wrongTypeFlagKey = "wrongtypekey",
          nullValueFlagKey = "nullvaluekey",
          unknownKey = "unknownkey";

      TestData testData = TestData.dataSource();
      testData.update(testData.flag(flagKey).on(true).variations(LDValue.ofNull(), expectedLdValue)
          .variationForUser(context.getKey(), 1));
      testData.update(testData.flag(nullValueFlagKey).on(true).variations(LDValue.ofNull())
          .variationForUser(context.getKey(), 0));
      testData.update(testData.flag(wrongTypeFlagKey).on(true).variations(LDValue.ofNull(), wrongTypeLdValue)
          .variationForUser(context.getKey(), 1));
      
      LDClientInterface client = new LDClient("SDK_KEY", baseConfig().dataSource(testData).build());

      assertEquals(expectedValue, variationMethod.apply(client, flagKey, context, defaultValue));

      assertEquals(EvaluationDetail.fromValue(expectedValue, 1, EvaluationReason.targetMatch()),
          variationDetailMethod.apply(client, flagKey, context, defaultValue));

      // unknown flag
      assertEquals(defaultValue, variationMethod.apply(client, unknownKey, context, defaultValue));
      assertEquals(EvaluationDetail.fromValue(defaultValue, -1, EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND)),
          variationDetailMethod.apply(client, unknownKey, context, defaultValue));

      // invalid/null context/user
      assertEquals(defaultValue, variationMethod.apply(client, flagKey, invalidContext, defaultValue));
      assertEquals(defaultValue, variationMethod.apply(client, flagKey, null, defaultValue));
      assertEquals(EvaluationDetail.fromValue(defaultValue, -1, EvaluationReason.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED)),
          variationDetailMethod.apply(client, flagKey, invalidContext, defaultValue));
      assertEquals(EvaluationDetail.fromValue(defaultValue, -1, EvaluationReason.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED)),
          variationDetailMethod.apply(client, flagKey, null, defaultValue));

      // wrong type
      if (wrongTypeLdValue != null)
      {
        assertEquals(defaultValue, variationMethod.apply(client, wrongTypeFlagKey, context, defaultValue));
        assertEquals(EvaluationDetail.fromValue(defaultValue, -1, EvaluationReason.error(EvaluationReason.ErrorKind.WRONG_TYPE)),
            variationDetailMethod.apply(client, wrongTypeFlagKey, context, defaultValue));
      }
  }

  @Test
  public void boolEvaluations() {
    doTypedVariationTests(
        (LDClientInterface c, String f, LDContext ctx, Boolean d) -> c.boolVariation(f, ctx, d),
        (LDClientInterface c, String f, LDContext ctx, Boolean d) -> c.boolVariationDetail(f, ctx, d),
        true,
        LDValue.of(true),
        false,
        LDValue.of("wrongtype")
        );
  }

  @Test
  public void intEvaluations() {
    doTypedVariationTests(
        (LDClientInterface c, String f, LDContext ctx, Integer d) -> c.intVariation(f, ctx, d),
        (LDClientInterface c, String f, LDContext ctx, Integer d) -> c.intVariationDetail(f, ctx, d),
        2,
        LDValue.of(2),
        1,
        LDValue.of("wrongtype")
        );
  }

  @Test
  public void doubleEvaluations() {
    doTypedVariationTests(
        (LDClientInterface c, String f, LDContext ctx, Double d) -> c.doubleVariation(f, ctx, d),
        (LDClientInterface c, String f, LDContext ctx, Double d) -> c.doubleVariationDetail(f, ctx, d),
        2.5d,
        LDValue.of(2.5d),
        1.5d,
        LDValue.of("wrongtype")
        );
  }

  @Test
  public void jsonEvaluations() {
    LDValue data = LDValue.buildObject().put("thing", "stuff").build();
    LDValue defaultValue = LDValue.of("default");
    doTypedVariationTests(
        (LDClientInterface c, String f, LDContext ctx, LDValue d) -> c.jsonValueVariation(f, ctx, d),
        (LDClientInterface c, String f, LDContext ctx, LDValue d) -> c.jsonValueVariationDetail(f, ctx, d),
        data,
        data,
        defaultValue,
        null
        );
  }
  
  @Test
  public void intVariationReturnsFlagValueEvenIfEncodedAsDouble() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(2.0)));

    assertEquals(2, client.intVariation("key", context, 1));
  }

  @Test
  public void intVariationFromDoubleRoundsTowardZero() throws Exception {
    upsertFlag(dataStore, flagWithValue("flag1", LDValue.of(2.25)));
    upsertFlag(dataStore, flagWithValue("flag2", LDValue.of(2.75)));
    upsertFlag(dataStore, flagWithValue("flag3", LDValue.of(-2.25)));
    upsertFlag(dataStore, flagWithValue("flag4", LDValue.of(-2.75)));

    assertEquals(2, client.intVariation("flag1", context, 1));
    assertEquals(2, client.intVariation("flag2", context, 1));
    assertEquals(-2, client.intVariation("flag3", context, 1));
    assertEquals(-2, client.intVariation("flag4", context, 1));
  }
  
  @Test
  public void doubleVariationReturnsFlagValueEvenIfEncodedAsInt() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(2)));

    assertEquals(2.0d, client.doubleVariation("key", context, 1.0d), 0d);
  }

  @Test
  public void doubleVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals(1.0d, client.doubleVariation("key", context, 1.0d), 0d);

    assertEquals(EvaluationDetail.fromValue(1.0d, NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND)),
        client.doubleVariationDetail("key", context, 1.0d));
  }

  @Test
  public void doubleVariationReturnsDefaultValueForWrongType() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("wrong")));

    assertEquals(1.0d, client.doubleVariation("key", context, 1.0d), 0d);
    
    assertEquals(EvaluationDetail.fromValue(1.0d, NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.WRONG_TYPE)),
        client.doubleVariationDetail("key", context, 1.0d));
  }
  
  @Test
  public void stringVariationWithNullDefaultReturnsFlagValue() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("b")));

    assertEquals("b", client.stringVariation("key", context, null));
  }

  @Test
  public void stringVariationWithNullDefaultReturnsDefaultValueForUnknownFlag() throws Exception {
    assertNull(client.stringVariation("key", context, null));

    assertEquals(EvaluationDetail.fromValue((String)null, NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND)),
        client.stringVariationDetail("key", context, null));
  }

  @Test
  public void stringVariationReturnsDefaultValueForWrongType() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    assertEquals("a", client.stringVariation("key", context, "a"));
    
    assertEquals(EvaluationDetail.fromValue("a", NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.WRONG_TYPE)),
        client.stringVariationDetail("key", context, "a"));
  }

  @Test
  public void stringVariationWithNullDefaultReturnsDefaultValueForWrongType() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    assertNull(client.stringVariation("key", context, null));
  
    assertEquals(EvaluationDetail.fromValue((String)null, NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.WRONG_TYPE)),
        client.stringVariationDetail("key", context, null));
  }
  
  @Test
  public void canMatchUserBySegment() throws Exception {
    // This is similar to EvaluatorSegmentMatchTest, but more end-to-end - we're verifying that
    // the client is forwarding the Evaluator's segment queries to the data store
    DataModel.Segment segment = segmentBuilder("segment1")
        .version(1)
        .included(context.getKey())
        .build();
    upsertSegment(dataStore, segment);
    
    DataModel.Clause clause = clauseMatchingSegment("segment1");
    DataModel.FeatureFlag feature = booleanFlagWithClauses("feature", clause);
    upsertFlag(dataStore, feature);
    
    assertTrue(client.boolVariation("feature", context, false));
  }

  @Test
  public void canTryToMatchUserBySegmentWhenSegmentIsNotFound() throws Exception {
    // This is similar to EvaluatorSegmentMatchTest, but more end-to-end - we're verifying that
    // the client is forwarding the Evaluator's segment queries to the data store, and that we
    // don't blow up if the segment is missing.
    DataModel.Clause clause = clauseMatchingSegment("segment1");
    DataModel.FeatureFlag feature = booleanFlagWithClauses("feature", clause);
    upsertFlag(dataStore, feature);
    
    assertFalse(client.boolVariation("feature", context, false));
  }

  @Test
  public void canGetDetailsForSuccessfulEvaluation() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(true,
        0, EvaluationReason.off());
    assertEquals(expectedResult, client.boolVariationDetail("key", context, false));
  }
  
  @Test
  public void jsonVariationReturnsNullIfFlagEvaluatesToNull() {
    DataModel.FeatureFlag flag = flagBuilder("key").on(false).offVariation(0).variations(LDValue.ofNull()).build();
    upsertFlag(dataStore, flag);
    
    assertEquals(LDValue.ofNull(), client.jsonValueVariation("key", context, LDValue.buildObject().build()));
  }

  @Test
  public void typedVariationReturnsZeroValueForTypeIfFlagEvaluatesToNull() {
    DataModel.FeatureFlag flag = flagBuilder("key").on(false).offVariation(0).variations(LDValue.ofNull()).build();
    upsertFlag(dataStore, flag);
    
    assertEquals(false, client.boolVariation("key", context, true));
    assertEquals(0, client.intVariation("key", context, 1));
    assertEquals(0d, client.doubleVariation("key", context, 1.0d), 0d);
  }
  
  @Test
  public void variationDetailReturnsDefaultIfFlagEvaluatesToNull() {
    DataModel.FeatureFlag flag = flagBuilder("key").on(false).offVariation(null).build();
    upsertFlag(dataStore, flag);
    
    EvaluationDetail<String> expected = EvaluationDetail.fromValue("default",
        NO_VARIATION, EvaluationReason.off());
    EvaluationDetail<String> actual = client.stringVariationDetail("key", context, "default");
    assertEquals(expected, actual);
    assertTrue(actual.isDefaultValue());
  }

  @Test
  public void deletedFlagPlaceholderIsTreatedAsUnknownFlag() {
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of("hello"));
    upsertFlag(dataStore, flag);
    dataStore.upsert(DataModel.FEATURES, flag.getKey(), ItemDescriptor.deletedItem(flag.getVersion() + 1));
    
    assertEquals("default", client.stringVariation(flag.getKey(), context, "default"));
  }
  
  @Test
  public void appropriateErrorIfClientNotInitialized() throws Exception {
    DataStore badDataStore = new InMemoryDataStore();
    LDConfig badConfig = baseConfig()
        .dataStore(specificComponent(badDataStore))
        .dataSource(specificComponent(failedDataSource()))
        .startWait(Duration.ZERO)
        .build();
    try (LDClientInterface badClient = new LDClient("SDK_KEY", badConfig)) {
      EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(false, NO_VARIATION,
          EvaluationReason.error(EvaluationReason.ErrorKind.CLIENT_NOT_READY));
      assertEquals(expectedResult, badClient.boolVariationDetail("key", context, false));
    }
  }
  
  @Test
  public void appropriateErrorIfFlagDoesNotExist() throws Exception {
    EvaluationDetail<String> expectedResult = EvaluationDetail.fromValue("default", NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND));
    assertEquals(expectedResult, client.stringVariationDetail("key", context, "default"));
  }

  @Test
  public void appropriateErrorIfContextIsNull() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<String> expectedResult = EvaluationDetail.fromValue("default", NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED));
    assertEquals(expectedResult, client.stringVariationDetail("key", null, "default"));
  }

  @Test
  public void appropriateErrorIfContextIsInvalid() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<String> expectedResult = EvaluationDetail.fromValue("default", NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED));
    assertEquals(expectedResult, client.stringVariationDetail("key", invalidContext, "default"));
  }
  
  @Test
  public void appropriateErrorIfValueWrongType() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of(true)));

    EvaluationDetail<Integer> expectedResult = EvaluationDetail.fromValue(3, NO_VARIATION,
        EvaluationReason.error(EvaluationReason.ErrorKind.WRONG_TYPE));
    assertEquals(expectedResult, client.intVariationDetail("key", context, 3));
  }
  
  @Test
  public void appropriateErrorForUnexpectedExceptionFromDataStore() throws Exception {
    RuntimeException exception = new RuntimeException("sorry");
    DataStore badDataStore = dataStoreThatThrowsException(exception);
    LDConfig badConfig = baseConfig()
        .dataStore(specificComponent(badDataStore))
        .build();
    try (LDClientInterface badClient = new LDClient("SDK_KEY", badConfig)) {
      EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(false, NO_VARIATION,
          EvaluationReason.exception(exception));
      assertEquals(expectedResult, badClient.boolVariationDetail("key", context, false));
    }
  }

  @Test
  public void appropriateErrorForUnexpectedExceptionFromFlagEvaluation() throws Exception {
    upsertFlag(dataStore, flagWithValue(INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION, LDValue.of(true)));
    
    EvaluationDetail<Boolean> expectedResult = EvaluationDetail.fromValue(false, NO_VARIATION,
        EvaluationReason.exception(EXPECTED_EXCEPTION_FROM_INVALID_FLAG));
    assertEquals(expectedResult, client.boolVariationDetail(INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION, context, false));
  }
  
  @Test
  public void evaluationUsesStoreIfStoreIsInitializedButClientIsNot() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("value")));
    LDConfig customConfig = baseConfig()
        .dataStore(specificComponent(dataStore))
        .dataSource(specificComponent(failedDataSource()))
        .startWait(Duration.ZERO)
        .build();

    try (LDClient client = new LDClient("SDK_KEY", customConfig)) {
      assertFalse(client.isInitialized());
      
      assertEquals("value", client.stringVariation("key", context, ""));
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
    DataModel.FeatureFlag flag3 = flagBuilder("key3")
        .version(300)
        .on(true)
        .fallthroughVariation(1)
        .variations(LDValue.of("x"), LDValue.of("value3"))
        .trackEvents(false)
        .trackEventsFallthrough(true)
        .build();
    upsertFlag(dataStore, flag1);
    upsertFlag(dataStore, flag2);
    upsertFlag(dataStore, flag3);

    FeatureFlagsState state = client.allFlagsState(context);
    assertTrue(state.isValid());
    
    String json = "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"," +
        "\"$flagsState\":{" +
          "\"key1\":{" +
            "\"variation\":0,\"version\":100" +
          "},\"key2\":{" +
            "\"variation\":1,\"version\":200,\"trackEvents\":true,\"debugEventsUntilDate\":1000" +
          "},\"key3\":{" +
            "\"variation\":1,\"version\":300,\"trackEvents\":true,\"trackReason\":true,\"reason\":{\"kind\":\"FALLTHROUGH\"}" +
          "}" +
        "}," +
        "\"$valid\":true" +
      "}";
    assertJsonEquals(json, gson.toJson(state));
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

    FeatureFlagsState state = client.allFlagsState(context, FlagsStateOption.CLIENT_SIDE_ONLY);
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

    FeatureFlagsState state = client.allFlagsState(context, FlagsStateOption.WITH_REASONS);
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
    assertJsonEquals(json, gson.toJson(state));
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

    FeatureFlagsState state = client.allFlagsState(context, FlagsStateOption.WITH_REASONS, FlagsStateOption.DETAILS_ONLY_FOR_TRACKED_FLAGS);
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
    assertJsonEquals(json, gson.toJson(state));    
  }
  
  @Test
  public void allFlagsStateFiltersOutDeletedFlags() throws Exception {
    DataModel.FeatureFlag flag1 = flagBuilder("key1").version(1).build();
    DataModel.FeatureFlag flag2 = flagBuilder("key2").version(1).build();
    upsertFlag(dataStore, flag1);
    upsertFlag(dataStore, flag2);
    dataStore.upsert(FEATURES, flag2.getKey(), ItemDescriptor.deletedItem(flag2.getVersion() + 1));

    FeatureFlagsState state = client.allFlagsState(context);
    assertTrue(state.isValid());
    
    Map<String, LDValue> valuesMap = state.toValuesMap();
    assertEquals(1, valuesMap.size());
    assertEquals(flag1.getKey(), getFirst(valuesMap.keySet(), null));
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateForNullContext() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("value")));

    FeatureFlagsState state = client.allFlagsState((LDContext)null);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateForInvalidContext() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("value")));

    FeatureFlagsState state = client.allFlagsState(invalidContext);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }

  @Test
  public void allFlagsStateReturnsEmptyStateIfDataStoreThrowsException() throws Exception {
    LDConfig customConfig = baseConfig()
        .dataStore(specificComponent(TestComponents.dataStoreThatThrowsException(new RuntimeException("sorry"))))
        .startWait(Duration.ZERO)
        .build();

    try (LDClient client = new LDClient("SDK_KEY", customConfig)) {
      FeatureFlagsState state = client.allFlagsState(context);
      assertFalse(state.isValid());
      assertEquals(0, state.toValuesMap().size());
    }
  }

  @Test
  public void allFlagsStateUsesNullValueForFlagIfEvaluationThrowsException() throws Exception {
    upsertFlag(dataStore, flagWithValue("goodkey", LDValue.of("value")));
    upsertFlag(dataStore, flagWithValue(INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION, LDValue.of("nope")));

    FeatureFlagsState state = client.allFlagsState(context);
    assertTrue(state.isValid());
    assertEquals(2, state.toValuesMap().size());
    assertEquals(LDValue.of("value"), state.getFlagValue("goodkey"));
    assertEquals(LDValue.ofNull(), state.getFlagValue(INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION));
  }
  
  @Test
  public void allFlagsStateUsesStoreDataIfStoreIsInitializedButClientIsNot() throws Exception {
    upsertFlag(dataStore, flagWithValue("key", LDValue.of("value")));
    LDConfig customConfig = baseConfig()
        .dataStore(specificComponent(dataStore))
        .dataSource(specificComponent(failedDataSource()))
        .startWait(Duration.ZERO)
        .build();
    
    try (LDClient client = new LDClient("SDK_KEY", customConfig)) {
      assertFalse(client.isInitialized());
      
      FeatureFlagsState state = client.allFlagsState(context);
      assertTrue(state.isValid());
      assertEquals(LDValue.of("value"), state.getFlagValue("key"));
    }
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateIfClientAndStoreAreNotInitialized() throws Exception {
    LDConfig customConfig = baseConfig()
        .dataSource(specificComponent(failedDataSource()))
        .startWait(Duration.ZERO)
        .build();
    
    try (LDClient client = new LDClient("SDK_KEY", customConfig)) {
      assertFalse(client.isInitialized());
      
      FeatureFlagsState state = client.allFlagsState(context);
      assertFalse(state.isValid());
    }
  }
}
