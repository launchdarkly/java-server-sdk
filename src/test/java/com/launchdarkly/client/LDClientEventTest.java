package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.EvaluationReason.ErrorKind;

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
  private static final LDUser userWithNullKey = new LDUser.Builder((String)null).build();
  
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
  public void identifyWithNullUserDoesNotSendEvent() {
    client.identify(null);
    assertEquals(0, eventSink.events.size());
  }

  @Test
  public void identifyWithUserWithNoKeyDoesNotSendEvent() {
    client.identify(userWithNullKey);
    assertEquals(0, eventSink.events.size());
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
  public void trackSendsEventWithDataAndMetricValue() throws Exception {
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    double metricValue = 1.5;
    client.track("eventkey", user, data, metricValue);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Custom.class, e.getClass());
    Event.Custom ce = (Event.Custom)e;
    assertEquals(user.getKey(), ce.user.getKey());
    assertEquals("eventkey", ce.key);
    assertEquals(data, ce.data);
    assertEquals(new Double(metricValue), ce.metricValue);
  }
  
  @Test
  public void trackWithNullUserDoesNotSendEvent() {
    client.track("eventkey", null);
    assertEquals(0, eventSink.events.size());
  }

  @Test
  public void trackWithUserWithNoKeyDoesNotSendEvent() {
    client.track("eventkey", userWithNullKey);
    assertEquals(0, eventSink.events.size());
  }

  @Test
  public void boolVariationSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", jbool(true));
    featureStore.upsert(FEATURES, flag);

    client.boolVariation("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, jbool(true), jbool(false), null, null);
  }

  @Test
  public void boolVariationSendsEventForUnknownFlag() throws Exception {
    client.boolVariation("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", jbool(false), null, null);
  }

  @Test
  public void boolVariationDetailSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", jbool(true));
    featureStore.upsert(FEATURES, flag);

    client.boolVariationDetail("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, jbool(true), jbool(false), null, EvaluationReason.off());
  }
  
  @Test
  public void boolVariationDetailSendsEventForUnknownFlag() throws Exception {
    client.boolVariationDetail("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", jbool(false), null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));    
  }
  
  @Test
  public void intVariationSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", jint(2));
    featureStore.upsert(FEATURES, flag);

    client.intVariation("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, jint(2), jint(1), null, null);
  }

  @Test
  public void intVariationSendsEventForUnknownFlag() throws Exception {
    client.intVariation("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", jint(1), null, null);
  }

  @Test
  public void intVariationDetailSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", jint(2));
    featureStore.upsert(FEATURES, flag);

    client.intVariationDetail("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, jint(2), jint(1), null, EvaluationReason.off());
  }

  @Test
  public void intVariationDetailSendsEventForUnknownFlag() throws Exception {
    client.intVariationDetail("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", jint(1), null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));
  }

  @Test
  public void doubleVariationSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", jdouble(2.5d));
    featureStore.upsert(FEATURES, flag);

    client.doubleVariation("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, jdouble(2.5d), jdouble(1.0d), null, null);
  }

  @Test
  public void doubleVariationSendsEventForUnknownFlag() throws Exception {
    client.doubleVariation("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", jdouble(1.0), null, null);
  }

  @Test
  public void doubleVariationDetailSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", jdouble(2.5d));
    featureStore.upsert(FEATURES, flag);

    client.doubleVariationDetail("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, jdouble(2.5d), jdouble(1.0d), null, EvaluationReason.off());
  }

  @Test
  public void doubleVariationDetailSendsEventForUnknownFlag() throws Exception {
    client.doubleVariationDetail("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", jdouble(1.0), null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));
  }

  @Test
  public void stringVariationSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", js("b"));
    featureStore.upsert(FEATURES, flag);

    client.stringVariation("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, js("b"), js("a"), null, null);
  }

  @Test
  public void stringVariationSendsEventForUnknownFlag() throws Exception {
    client.stringVariation("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", js("a"), null, null);
  }

  @Test
  public void stringVariationDetailSendsEvent() throws Exception {
    FeatureFlag flag = flagWithValue("key", js("b"));
    featureStore.upsert(FEATURES, flag);

    client.stringVariationDetail("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, js("b"), js("a"), null, EvaluationReason.off());
  }

  @Test
  public void stringVariationDetailSendsEventForUnknownFlag() throws Exception {
    client.stringVariationDetail("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", js("a"), null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));
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
    checkFeatureEvent(eventSink.events.get(0), flag, data, defaultVal, null, null);
  }

  @Test
  public void jsonVariationSendsEventForUnknownFlag() throws Exception {
    JsonElement defaultVal = new JsonPrimitive(42);
    
    client.jsonVariation("key", user, defaultVal);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", defaultVal, null, null);
  }

  @Test
  public void jsonVariationDetailSendsEvent() throws Exception {
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    FeatureFlag flag = flagWithValue("key", data);
    featureStore.upsert(FEATURES, flag);
    JsonElement defaultVal = new JsonPrimitive(42);
    
    client.jsonVariationDetail("key", user, defaultVal);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, data, defaultVal, null, EvaluationReason.off());
  }

  @Test
  public void jsonVariationDetailSendsEventForUnknownFlag() throws Exception {
    JsonElement defaultVal = new JsonPrimitive(42);
    
    client.jsonVariationDetail("key", user, defaultVal);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", defaultVal, null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));
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
    checkFeatureEvent(eventSink.events.get(0), f1, js("go"), null, "feature0", null);
    checkFeatureEvent(eventSink.events.get(1), f0, js("fall"), js("default"), null, null);
  }

  @Test
  public void eventIsSentWithReasonForExistingPrererequisiteFlag() throws Exception {
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
    
    client.stringVariationDetail("feature0", user, "default");
    
    assertEquals(2, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), f1, js("go"), null, "feature0", EvaluationReason.fallthrough());
    checkFeatureEvent(eventSink.events.get(1), f0, js("fall"), js("default"), null, EvaluationReason.fallthrough());
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
    checkFeatureEvent(eventSink.events.get(0), f0, js("off"), js("default"), null, null);
  }

  @Test
  public void failureReasonIsGivenForUnknownPrererequisiteFlagIfDetailsWereRequested() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(js("fall"), js("off"), js("on"))
        .version(1)
        .build();
    featureStore.upsert(FEATURES, f0);
    
    client.stringVariationDetail("feature0", user, "default");
    
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), f0, js("off"), js("default"), null,
        EvaluationReason.prerequisiteFailed("feature1"));
  }
  
  private void checkFeatureEvent(Event e, FeatureFlag flag, JsonElement value, JsonElement defaultVal,
      String prereqOf, EvaluationReason reason) {
    assertEquals(Event.FeatureRequest.class, e.getClass());
    Event.FeatureRequest fe = (Event.FeatureRequest)e;
    assertEquals(flag.getKey(), fe.key);
    assertEquals(user.getKey(), fe.user.getKey());
    assertEquals(new Integer(flag.getVersion()), fe.version);
    assertEquals(value, fe.value);
    assertEquals(defaultVal, fe.defaultVal);
    assertEquals(prereqOf, fe.prereqOf);
    assertEquals(reason, fe.reason);
  }

  private void checkUnknownFeatureEvent(Event e, String key, JsonElement defaultVal, String prereqOf,
      EvaluationReason reason) {
    assertEquals(Event.FeatureRequest.class, e.getClass());
    Event.FeatureRequest fe = (Event.FeatureRequest)e;
    assertEquals(key, fe.key);
    assertEquals(user.getKey(), fe.user.getKey());
    assertNull(fe.version);
    assertEquals(defaultVal, fe.value);
    assertEquals(defaultVal, fe.defaultVal);
    assertEquals(prereqOf, fe.prereqOf);
    assertEquals(reason, fe.reason);
  }
}
