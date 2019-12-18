package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.EvaluationReason.ErrorKind;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import static com.launchdarkly.client.ModelBuilders.clauseMatchingUser;
import static com.launchdarkly.client.ModelBuilders.clauseNotMatchingUser;
import static com.launchdarkly.client.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.client.ModelBuilders.flagBuilder;
import static com.launchdarkly.client.ModelBuilders.flagWithValue;
import static com.launchdarkly.client.ModelBuilders.prerequisite;
import static com.launchdarkly.client.ModelBuilders.ruleBuilder;
import static com.launchdarkly.client.TestUtil.specificEventProcessor;
import static com.launchdarkly.client.TestUtil.specificFeatureStore;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
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
    assertEquals(LDValue.ofNull(), ce.data);
  }

  @Test
  public void trackSendsEventWithData() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    client.trackData("eventkey", user, data);
    
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
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    double metricValue = 1.5;
    client.trackMetric("eventkey", user, data, metricValue);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Custom.class, e.getClass());
    Event.Custom ce = (Event.Custom)e;
    assertEquals(user.getKey(), ce.user.getKey());
    assertEquals("eventkey", ce.key);
    assertEquals(data, ce.data);
    assertEquals(new Double(metricValue), ce.metricValue);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void deprecatedTrackSendsEventWithData() throws Exception {
    JsonElement data = new JsonPrimitive("stuff"); 
    client.track("eventkey", user, data);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Custom.class, e.getClass());
    Event.Custom ce = (Event.Custom)e;
    assertEquals(user.getKey(), ce.user.getKey());
    assertEquals("eventkey", ce.key);
    assertEquals(data, ce.data.asJsonElement());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void deprecatedTrackSendsEventWithDataAndMetricValue() throws Exception {
    JsonElement data = new JsonPrimitive("stuff"); 
    double metricValue = 1.5;
    client.track("eventkey", user, data, metricValue);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Custom.class, e.getClass());
    Event.Custom ce = (Event.Custom)e;
    assertEquals(user.getKey(), ce.user.getKey());
    assertEquals("eventkey", ce.key);
    assertEquals(data, ce.data.asJsonElement());
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
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(true));
    featureStore.upsert(FEATURES, flag);

    client.boolVariation("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, LDValue.of(true), LDValue.of(false), null, null);
  }

  @Test
  public void boolVariationSendsEventForUnknownFlag() throws Exception {
    client.boolVariation("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", LDValue.of(false), null, null);
  }

  @Test
  public void boolVariationDetailSendsEvent() throws Exception {
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(true));
    featureStore.upsert(FEATURES, flag);

    client.boolVariationDetail("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, LDValue.of(true), LDValue.of(false), null, EvaluationReason.off());
  }
  
  @Test
  public void boolVariationDetailSendsEventForUnknownFlag() throws Exception {
    client.boolVariationDetail("key", user, false);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", LDValue.of(false), null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));    
  }
  
  @Test
  public void intVariationSendsEvent() throws Exception {
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(2));
    featureStore.upsert(FEATURES, flag);

    client.intVariation("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, LDValue.of(2), LDValue.of(1), null, null);
  }

  @Test
  public void intVariationSendsEventForUnknownFlag() throws Exception {
    client.intVariation("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", LDValue.of(1), null, null);
  }

  @Test
  public void intVariationDetailSendsEvent() throws Exception {
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(2));
    featureStore.upsert(FEATURES, flag);

    client.intVariationDetail("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, LDValue.of(2), LDValue.of(1), null, EvaluationReason.off());
  }

  @Test
  public void intVariationDetailSendsEventForUnknownFlag() throws Exception {
    client.intVariationDetail("key", user, 1);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", LDValue.of(1), null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));
  }

  @Test
  public void doubleVariationSendsEvent() throws Exception {
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(2.5d));
    featureStore.upsert(FEATURES, flag);

    client.doubleVariation("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, LDValue.of(2.5d), LDValue.of(1.0d), null, null);
  }

  @Test
  public void doubleVariationSendsEventForUnknownFlag() throws Exception {
    client.doubleVariation("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", LDValue.of(1.0), null, null);
  }

  @Test
  public void doubleVariationDetailSendsEvent() throws Exception {
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(2.5d));
    featureStore.upsert(FEATURES, flag);

    client.doubleVariationDetail("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, LDValue.of(2.5d), LDValue.of(1.0d), null, EvaluationReason.off());
  }

  @Test
  public void doubleVariationDetailSendsEventForUnknownFlag() throws Exception {
    client.doubleVariationDetail("key", user, 1.0d);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", LDValue.of(1.0), null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));
  }

  @Test
  public void stringVariationSendsEvent() throws Exception {
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of("b"));
    featureStore.upsert(FEATURES, flag);

    client.stringVariation("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, LDValue.of("b"), LDValue.of("a"), null, null);
  }

  @Test
  public void stringVariationSendsEventForUnknownFlag() throws Exception {
    client.stringVariation("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", LDValue.of("a"), null, null);
  }

  @Test
  public void stringVariationDetailSendsEvent() throws Exception {
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of("b"));
    featureStore.upsert(FEATURES, flag);

    client.stringVariationDetail("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, LDValue.of("b"), LDValue.of("a"), null, EvaluationReason.off());
  }

  @Test
  public void stringVariationDetailSendsEventForUnknownFlag() throws Exception {
    client.stringVariationDetail("key", user, "a");
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", LDValue.of("a"), null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));
  }

  @SuppressWarnings("deprecation")
  @Test
  public void jsonVariationSendsEvent() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    DataModel.FeatureFlag flag = flagWithValue("key", data);
    featureStore.upsert(FEATURES, flag);
    LDValue defaultVal = LDValue.of(42);
    
    client.jsonVariation("key", user, new JsonPrimitive(defaultVal.intValue()));
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, data, defaultVal, null, null);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void jsonVariationSendsEventForUnknownFlag() throws Exception {
    LDValue defaultVal = LDValue.of(42);
    
    client.jsonVariation("key", user, new JsonPrimitive(defaultVal.intValue()));
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", defaultVal, null, null);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void jsonVariationDetailSendsEvent() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    DataModel.FeatureFlag flag = flagWithValue("key", data);
    featureStore.upsert(FEATURES, flag);
    LDValue defaultVal = LDValue.of(42);
    
    client.jsonVariationDetail("key", user, new JsonPrimitive(defaultVal.intValue()));
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, data, defaultVal, null, EvaluationReason.off());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void jsonVariationDetailSendsEventForUnknownFlag() throws Exception {
    LDValue defaultVal = LDValue.of(42);
    
    client.jsonVariationDetail("key", user, new JsonPrimitive(defaultVal.intValue()));
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", defaultVal, null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));
  }

  @Test
  public void jsonValueVariationDetailSendsEvent() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    DataModel.FeatureFlag flag = flagWithValue("key", data);
    featureStore.upsert(FEATURES, flag);
    LDValue defaultVal = LDValue.of(42);
    
    client.jsonValueVariationDetail("key", user, defaultVal);
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), flag, data, defaultVal, null, EvaluationReason.off());
  }

  @Test
  public void jsonValueVariationDetailSendsEventForUnknownFlag() throws Exception {
    LDValue defaultVal = LDValue.of(42);
    
    client.jsonValueVariationDetail("key", user, defaultVal);
    assertEquals(1, eventSink.events.size());
    checkUnknownFeatureEvent(eventSink.events.get(0), "key", defaultVal, null,
        EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND));
  }

  @Test
  public void eventTrackingAndReasonCanBeForcedForRule() throws Exception {
    DataModel.Clause clause = clauseMatchingUser(user);
    DataModel.Rule rule = ruleBuilder().id("id").clauses(clause).variation(1).trackEvents(true).build();
    DataModel.FeatureFlag flag = flagBuilder("flag")
        .on(true)
        .rules(rule)
        .offVariation(0)
        .variations(LDValue.of("off"), LDValue.of("on"))
        .build();
    featureStore.upsert(FEATURES, flag);

    client.stringVariation("flag", user, "default");
    
    // Note, we did not call stringVariationDetail and the flag is not tracked, but we should still get
    // tracking and a reason, because the rule-level trackEvents flag is on for the matched rule.
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertTrue(event.trackEvents);
    assertEquals(EvaluationReason.ruleMatch(0, "id"), event.reason);
  }

  @Test
  public void eventTrackingAndReasonAreNotForcedIfFlagIsNotSetForMatchingRule() throws Exception {
    DataModel.Clause clause0 = clauseNotMatchingUser(user);
    DataModel.Clause clause1 = clauseMatchingUser(user);
    DataModel.Rule rule0 = ruleBuilder().id("id0").clauses(clause0).variation(1).trackEvents(true).build();
    DataModel.Rule rule1 = ruleBuilder().id("id1").clauses(clause1).variation(1).trackEvents(false).build();
    DataModel.FeatureFlag flag = flagBuilder("flag")
        .on(true)
        .rules(rule0, rule1)
        .offVariation(0)
        .variations(LDValue.of("off"), LDValue.of("on"))
        .build();
    featureStore.upsert(FEATURES, flag);

    client.stringVariation("flag", user, "default");
    
    // It matched rule1, which has trackEvents: false, so we don't get the override behavior
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertFalse(event.trackEvents);
    assertNull(event.reason);
  }

  @Test
  public void eventTrackingAndReasonCanBeForcedForFallthrough() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flag")
        .on(true)
        .fallthrough(new DataModel.VariationOrRollout(0, null))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .trackEventsFallthrough(true)
        .build();
    featureStore.upsert(FEATURES, flag);

    client.stringVariation("flag", user, "default");
    
    // Note, we did not call stringVariationDetail and the flag is not tracked, but we should still get
    // tracking and a reason, because trackEventsFallthrough is on and the evaluation fell through.
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertTrue(event.trackEvents);
    assertEquals(EvaluationReason.fallthrough(), event.reason);
  }

  @Test
  public void eventTrackingAndReasonAreNotForcedForFallthroughIfFlagIsNotSet() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flag")
        .on(true)
        .fallthrough(new DataModel.VariationOrRollout(0, null))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .trackEventsFallthrough(false)
        .build();
    featureStore.upsert(FEATURES, flag);

    client.stringVariation("flag", user, "default");
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertFalse(event.trackEvents);
    assertNull(event.reason);
  }

  @Test
  public void eventTrackingAndReasonAreNotForcedForFallthroughIfReasonIsNotFallthrough() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flag")
        .on(false) // so the evaluation reason will be OFF, not FALLTHROUGH
        .offVariation(1)
        .fallthrough(new DataModel.VariationOrRollout(0, null))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .trackEventsFallthrough(true)
        .build();
    featureStore.upsert(FEATURES, flag);

    client.stringVariation("flag", user, "default");
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertFalse(event.trackEvents);
    assertNull(event.reason);
  }
  
  @Test
  public void eventIsSentForExistingPrererequisiteFlag() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    DataModel.FeatureFlag f1 = flagBuilder("feature1")
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    featureStore.upsert(FEATURES, f0);
    featureStore.upsert(FEATURES, f1);
    
    client.stringVariation("feature0", user, "default");
    
    assertEquals(2, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), f1, LDValue.of("go"), LDValue.ofNull(), "feature0", null);
    checkFeatureEvent(eventSink.events.get(1), f0, LDValue.of("fall"), LDValue.of("default"), null, null);
  }

  @Test
  public void eventIsSentWithReasonForExistingPrererequisiteFlag() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    DataModel.FeatureFlag f1 = flagBuilder("feature1")
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    featureStore.upsert(FEATURES, f0);
    featureStore.upsert(FEATURES, f1);
    
    client.stringVariationDetail("feature0", user, "default");
    
    assertEquals(2, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), f1, LDValue.of("go"), LDValue.ofNull(), "feature0", EvaluationReason.fallthrough());
    checkFeatureEvent(eventSink.events.get(1), f0, LDValue.of("fall"), LDValue.of("default"), null, EvaluationReason.fallthrough());
  }

  @Test
  public void eventIsNotSentForUnknownPrererequisiteFlag() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    featureStore.upsert(FEATURES, f0);
    
    client.stringVariation("feature0", user, "default");
    
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), f0, LDValue.of("off"), LDValue.of("default"), null, null);
  }

  @Test
  public void failureReasonIsGivenForUnknownPrererequisiteFlagIfDetailsWereRequested() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    featureStore.upsert(FEATURES, f0);
    
    client.stringVariationDetail("feature0", user, "default");
    
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), f0, LDValue.of("off"), LDValue.of("default"), null,
        EvaluationReason.prerequisiteFailed("feature1"));
  }
  
  private void checkFeatureEvent(Event e, DataModel.FeatureFlag flag, LDValue value, LDValue defaultVal,
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
    assertEquals(flag.isTrackEvents(), fe.trackEvents);
    assertEquals(flag.getDebugEventsUntilDate(), fe.debugEventsUntilDate);
  }

  private void checkUnknownFeatureEvent(Event e, String key, LDValue defaultVal, String prereqOf,
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
    assertFalse(fe.trackEvents);
    assertNull(fe.debugEventsUntilDate);
  }
}
