package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.client.TestUtil.jint;
import static com.launchdarkly.client.TestUtil.js;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LDClientEvaluationTest extends EasyMockSupport {
  private static final LDUser user = new LDUser("userkey");
  
  private TestFeatureStore featureStore = new TestFeatureStore();
  private LDConfig config = new LDConfig.Builder().featureStore(featureStore).build();
  private LDClientInterface client = createTestClient(config);
  
  @Test
  public void boolVariationReturnsFlagValue() throws Exception {
    featureStore.setFeatureTrue("key");

    assertTrue(client.boolVariation("key", user, false));
  }

  @Test
  public void boolVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertFalse(client.boolVariation("key", user, false));
  }
  
  @Test
  public void intVariationReturnsFlagValue() throws Exception {
    featureStore.setIntegerValue("key", 2);

    assertEquals(new Integer(2), client.intVariation("key", user, 1));
  }

  @Test
  public void intVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals(new Integer(1), client.intVariation("key", user, 1));
  }

  @Test
  public void doubleVariationReturnsFlagValue() throws Exception {
    featureStore.setDoubleValue("key", 2.5d);

    assertEquals(new Double(2.5d), client.doubleVariation("key", user, 1.0d));
  }

  @Test
  public void doubleVariationReturnsDefaultValueForUnknownFlag() throws Exception {
    assertEquals(new Double(1.0d), client.doubleVariation("key", user, 1.0d));
  }

  @Test
  public void stringVariationReturnsFlagValue() throws Exception {
    featureStore.setStringValue("key", "b");

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
    featureStore.setJsonValue("key", data);
    
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
    Rule rule = new Rule(Arrays.asList(clause), 0, null);
    FeatureFlag feature = new FeatureFlagBuilder("test-feature")
        .version(1)
        .rules(Arrays.asList(rule))
        .variations(TestFeatureStore.TRUE_FALSE_VARIATIONS)
        .on(true)
        .fallthrough(new VariationOrRollout(1, null))
        .build();
    featureStore.upsert(FEATURES, feature);
    
    assertTrue(client.boolVariation("test-feature", user, false));
  }
  
  private LDClientInterface createTestClient(LDConfig config) {
    return new LDClient("SDK_KEY", config) {
      @Override
      protected UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config) {
        return new UpdateProcessor.NullUpdateProcessor();
      }

      @Override
      protected EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
        return new EventProcessor.NullEventProcessor();
      }
    };
  }
}
