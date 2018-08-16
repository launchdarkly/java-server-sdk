package com.launchdarkly.client;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static com.launchdarkly.client.TestUtil.fallthroughVariation;
import static com.launchdarkly.client.TestUtil.jint;
import static com.launchdarkly.client.TestUtil.js;
import static com.launchdarkly.client.TestUtil.specificFeatureStore;
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

  private TestFeatureStore featureStore = new TestFeatureStore();
  private LDConfig config = new LDConfig.Builder()
      .featureStoreFactory(specificFeatureStore(featureStore))
      .eventProcessorFactory(Components.nullEventProcessor())
      .updateProcessorFactory(Components.nullUpdateProcessor())
      .build();
  private LDClientInterface client = new LDClient("SDK_KEY", config);
  
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
  
  @SuppressWarnings("deprecation")
  @Test
  public void allFlagsReturnsFlagValues() throws Exception {
    featureStore.setStringValue("key1","value1");
    featureStore.setStringValue("key2", "value2");
    
    Map<String, JsonElement> result = client.allFlags(user);
    assertEquals(ImmutableMap.<String, JsonElement>of("key1", js("value1"), "key2", js("value2")), result);
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void allFlagsReturnsNullForNullUser() throws Exception {
    featureStore.setStringValue("key", "value");

    assertNull(client.allFlags(null));
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void allFlagsReturnsNullForNullUserKey() throws Exception {
    featureStore.setStringValue("key", "value");

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
        "}}";
    JsonElement expected = gson.fromJson(json, JsonElement.class);
    assertEquals(expected, gson.fromJson(state.toJsonString(), JsonElement.class));
  }

  @Test
  public void allFlagsStateReturnsEmptyStateForNullUser() throws Exception {
    featureStore.setStringValue("key", "value");

    FeatureFlagsState state = client.allFlagsState(null);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }
  
  @Test
  public void allFlagsStateReturnsEmptyStateForNullUserKey() throws Exception {
    featureStore.setStringValue("key", "value");

    FeatureFlagsState state = client.allFlagsState(userWithNullKey);
    assertFalse(state.isValid());
    assertEquals(0, state.toValuesMap().size());
  }
}
