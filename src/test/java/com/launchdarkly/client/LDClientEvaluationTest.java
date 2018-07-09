package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.client.TestUtil.booleanFlagWithClauses;
import static com.launchdarkly.client.TestUtil.failedUpdateProcessor;
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
import static org.junit.Assert.assertTrue;

public class LDClientEvaluationTest {
  private static final LDUser user = new LDUser("userkey");
  
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
}
