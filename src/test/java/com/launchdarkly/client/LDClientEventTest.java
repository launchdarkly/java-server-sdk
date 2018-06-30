package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.client.TestUtil.fallthroughVariation;
import static com.launchdarkly.client.TestUtil.flagWithValue;
import static com.launchdarkly.client.TestUtil.jbool;
import static com.launchdarkly.client.TestUtil.jdouble;
import static com.launchdarkly.client.TestUtil.jint;
import static com.launchdarkly.client.TestUtil.js;
import static com.launchdarkly.client.TestUtil.specificEventProcessor;
import static com.launchdarkly.client.TestUtil.specificFeatureStore;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LDClientEventTest {
  private static final LDUser user = new LDUser("userkey");
  
  private FeatureStore featureStore = TestUtil.initedFeatureStore();
  private TestUtil.TestEventProcessor eventSink = new TestUtil.TestEventProcessor();
  private LDConfig config = new LDConfig.Builder()
      .featureStoreFactory(specificFeatureStore(featureStore))
      .eventProcessorFactory(specificEventProcessor(eventSink))
      .updateProcessorFactory(Components.nullUpdateProcessor())
      .build();
  private LDClientInterface client = new LDClient("SDK_KEY", config);
  
  @Test
  public void identifySendsEvent() throws Exception {
    client.identify(user);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Identify.class, e.getClass());
    Event.Identify ie = (Event.Identify)e;
    assertEquals(user.getKey(), ie.user.getKey());
  }
  
  @Test
  public void trackSendsEventWithoutData() throws Exception {
    client.track("eventkey", user);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Custom.class, e.getClass());
    Event.Custom ce = (Event.Custom)e;
    assertEquals(user.getKey(), ce.user.getKey());
    assertEquals("eventkey", ce.key);
    assertNull(ce.data);
  }

  @Test
  public void trackSendsEventWithData() throws Exception {
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    client.track("eventkey", user, data);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Custom.class, e.getClass());
    Event.Custom ce = (Event.Custom)e;
    assertEquals(user.getKey(), ce.user.getKey());
    assertEquals("eventkey", ce.key);
    assertEquals(data, ce.data);
  }

  @Test
  public void boolVariationSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", jbool(true));
    featureStore.upsert(FEATURES, flag);

    client.boolVariation("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, jbool(true), jbool(false), null);
  }

  @Test
  public void boolVariationSendsEventForUnknownFlag() throws Exception {
    client.boolVariation("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", jbool(false), null);
  }

  @Test
  public void intVariationSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", jint(2));
    featureStore.upsert(FEATURES, flag);

    client.intVariation("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, jint(2), jint(1), null);
  }

  @Test
  public void intVariationSendsEventForUnknownFlag() throws Exception {
    client.intVariation("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", jint(1), null);
  }

  @Test
  public void doubleVariationSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", jdouble(2.5d));
    featureStore.upsert(FEATURES, flag);

    client.doubleVariation("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, jdouble(2.5d), jdouble(1.0d), null);
  }

  @Test
  public void doubleVariationSendsEventForUnknownFlag() throws Exception {
    client.doubleVariation("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", jdouble(1.0), null);
  }
  
  @Test
  public void stringVariationSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", js("b"));
    featureStore.upsert(FEATURES, flag);

    client.stringVariation("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, js("b"), js("a"), null);
  }

  @Test
  public void stringVariationSendsEventForUnknownFlag() throws Exception {
    client.stringVariation("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", js("a"), null);
  }
  
  @Test
  public void jsonVariationSendsEvent() throws Exception {
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    FeatureFlag flag = flagWithValue("key", data);
    featureStore.upsert(FEATURES, flag);
    JsonElement defaultVal = new JsonPrimitive(42);
    
    client.jsonVariation("key", user, defaultVal);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, data, defaultVal, null);
  }

  @Test
  public void jsonVariationSendsEventForUnknownFlag() throws Exception {
    JsonElement defaultVal = new JsonPrimitive(42);
    
    client.jsonVariation("key", user, defaultVal);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", defaultVal, null);
  }

  @Test
  public void eventIsSentForExistingPrererequisiteFlag() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(js("fall"), js("off"), js("on"))
        .version(1)
        .build();
    FeatureFlag f1 = new FeatureFlagBuilder("feature1")
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(js("nogo"), js("go"))
        .version(2)
        .build();
    featureStore.upsert(FEATURES, f0);
    featureStore.upsert(FEATURES, f1);
    
    client.stringVariation("feature0", user, "default");
    
    assertEquals(2, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), f1, js("go"), null, "feature0");
    checkFeatureEvent(eventSink.events.get(1), f0, js("fall"), js("default"), null);
  }

  @Test
  public void eventIsNotSentForUnknownPrererequisiteFlag() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(js("fall"), js("off"), js("on"))
        .version(1)
        .build();
    featureStore.upsert(FEATURES, f0);
    
    client.stringVariation("feature0", user, "default");
    
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), f0, js("off"), js("default"), null);
  }
  
  private void checkFeatureEvent(Event e, FeatureFlag flag, JsonElement value, JsonElement defaultVal,
      String prereqOf) {
    assertEquals(Event.FeatureRequest.class, e.getClass());
    Event.FeatureRequest fe = (Event.FeatureRequest)e;
    assertEquals(flag.getKey(), fe.key);
    assertEquals(user.getKey(), fe.user.getKey());
    assertEquals(new Integer(flag.getVersion()), fe.version);
    assertEquals(value, fe.value);
    assertEquals(defaultVal, fe.defaultVal);
    assertEquals(prereqOf, fe.prereqOf);
  }

  private void checkUnknownFeatureEvent(Event e, String key, JsonElement defaultVal, String prereqOf) {
    assertEquals(Event.FeatureRequest.class, e.getClass());
    Event.FeatureRequest fe = (Event.FeatureRequest)e;
    assertEquals(key, fe.key);
    assertEquals(user.getKey(), fe.user.getKey());
    assertNull(fe.version);
    assertEquals(defaultVal, fe.value);
    assertEquals(defaultVal, fe.defaultVal);
    assertEquals(prereqOf, fe.prereqOf);
  }
}
