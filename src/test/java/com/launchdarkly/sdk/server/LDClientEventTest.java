package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;

import org.junit.Test;

import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingUser;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseNotMatchingUser;
import static com.launchdarkly.sdk.server.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.ModelBuilders.prerequisite;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.TestComponents.initedDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificEventProcessor;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientEventTest {
  private static final LDUser user = new LDUser("userkey");
  private static final LDUser userWithNullKey = new LDUser.Builder((String)null).build();
  private static final LDUser userWithEmptyKey = new LDUser.Builder("").build();
  
  private DataStore dataStore = initedDataStore();
  private TestComponents.TestEventProcessor eventSink = new TestComponents.TestEventProcessor();
  private LDConfig config = new LDConfig.Builder()
      .dataStore(specificDataStore(dataStore))
      .events(specificEventProcessor(eventSink))
      .dataSource(Components.externalUpdatesOnly())
      .build();
  private LDClientInterface client = new LDClient("SDK_KEY", config);
  
  @Test
  public void identifySendsEvent() throws Exception {
    client.identify(user);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Identify.class, e.getClass());
    Event.Identify ie = (Event.Identify)e;
    assertEquals(user.getKey(), ie.getUser().getKey());
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
  public void identifyWithUserWithEmptyKeyDoesNotSendEvent() {
    client.identify(userWithEmptyKey);
    assertEquals(0, eventSink.events.size());
  }
  
  @Test
  public void trackSendsEventWithoutData() throws Exception {
    client.track("eventkey", user);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Custom.class, e.getClass());
    Event.Custom ce = (Event.Custom)e;
    assertEquals(user.getKey(), ce.getUser().getKey());
    assertEquals("eventkey", ce.getKey());
    assertEquals(LDValue.ofNull(), ce.getData());
  }

  @Test
  public void trackSendsEventWithData() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    client.trackData("eventkey", user, data);
    
    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.Custom.class, e.getClass());
    Event.Custom ce = (Event.Custom)e;
    assertEquals(user.getKey(), ce.getUser().getKey());
    assertEquals("eventkey", ce.getKey());
    assertEquals(data, ce.getData());
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
    assertEquals(user.getKey(), ce.getUser().getKey());
    assertEquals("eventkey", ce.getKey());
    assertEquals(data, ce.getData());
    assertEquals(Double.valueOf(metricValue), ce.getMetricValue());
  }

  @Test
  public void trackWithNullUserDoesNotSendEvent() {
    client.track("eventkey", null);
    assertEquals(0, eventSink.events.size());
    
    client.trackData("eventkey", null, LDValue.of(1));
    assertEquals(0, eventSink.events.size());

    client.trackMetric("eventkey", null, LDValue.of(1), 1.5);
    assertEquals(0, eventSink.events.size());
  }

  @Test
  public void trackWithUserWithNoKeyDoesNotSendEvent() {
    client.track("eventkey", userWithNullKey);
    assertEquals(0, eventSink.events.size());
    
    client.trackData("eventkey", userWithNullKey, LDValue.of(1));
    assertEquals(0, eventSink.events.size());

    client.trackMetric("eventkey", userWithNullKey, LDValue.of(1), 1.5);
    assertEquals(0, eventSink.events.size());
  }

  @Test
  public void trackWithUserWithEmptyKeyDoesNotSendEvent() {
    client.track("eventkey", userWithEmptyKey);
    assertEquals(0, eventSink.events.size());
    
    client.trackData("eventkey", userWithEmptyKey, LDValue.of(1));
    assertEquals(0, eventSink.events.size());

    client.trackMetric("eventkey", userWithEmptyKey, LDValue.of(1), 1.5);
    assertEquals(0, eventSink.events.size());
  }

  @Test
  public void boolVariationSendsEvent() throws Exception {
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(true));
    upsertFlag(dataStore, flag);

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
    upsertFlag(dataStore, flag);

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
    upsertFlag(dataStore, flag);

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
    upsertFlag(dataStore, flag);

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
    upsertFlag(dataStore, flag);

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
    upsertFlag(dataStore, flag);

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
    upsertFlag(dataStore, flag);

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
    upsertFlag(dataStore, flag);

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

  @Test
  public void jsonValueVariationDetailSendsEvent() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    DataModel.FeatureFlag flag = flagWithValue("key", data);
    upsertFlag(dataStore, flag);
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
    upsertFlag(dataStore, flag);

    client.stringVariation("flag", user, "default");
    
    // Note, we did not call stringVariationDetail and the flag is not tracked, but we should still get
    // tracking and a reason, because the rule-level trackEvents flag is on for the matched rule.
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertTrue(event.isTrackEvents());
    assertEquals(EvaluationReason.ruleMatch(0, "id"), event.getReason());
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
    upsertFlag(dataStore, flag);

    client.stringVariation("flag", user, "default");
    
    // It matched rule1, which has trackEvents: false, so we don't get the override behavior
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertFalse(event.isTrackEvents());
    assertNull(event.getReason());
  }

  @Test
  public void eventTrackingAndReasonCanBeForcedForFallthrough() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flag")
        .on(true)
        .fallthrough(new DataModel.VariationOrRollout(0, null))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .trackEventsFallthrough(true)
        .build();
    upsertFlag(dataStore, flag);

    client.stringVariation("flag", user, "default");
    
    // Note, we did not call stringVariationDetail and the flag is not tracked, but we should still get
    // tracking and a reason, because trackEventsFallthrough is on and the evaluation fell through.
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertTrue(event.isTrackEvents());
    assertEquals(EvaluationReason.fallthrough(), event.getReason());
  }

  @Test
  public void eventTrackingAndReasonAreNotForcedForFallthroughIfFlagIsNotSet() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flag")
        .on(true)
        .fallthrough(new DataModel.VariationOrRollout(0, null))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .trackEventsFallthrough(false)
        .build();
    upsertFlag(dataStore, flag);

    client.stringVariation("flag", user, "default");
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertFalse(event.isTrackEvents());
    assertNull(event.getReason());
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
    upsertFlag(dataStore, flag);

    client.stringVariation("flag", user, "default");
    
    assertEquals(1, eventSink.events.size());
    Event.FeatureRequest event = (Event.FeatureRequest)eventSink.events.get(0);
    assertFalse(event.isTrackEvents());
    assertNull(event.getReason());
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
    upsertFlag(dataStore, f0);
    upsertFlag(dataStore, f1);
    
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
    upsertFlag(dataStore, f0);
    upsertFlag(dataStore, f1);
    
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
    upsertFlag(dataStore, f0);
    
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
    upsertFlag(dataStore, f0);
    
    client.stringVariationDetail("feature0", user, "default");
    
    assertEquals(1, eventSink.events.size());
    checkFeatureEvent(eventSink.events.get(0), f0, LDValue.of("off"), LDValue.of("default"), null,
        EvaluationReason.prerequisiteFailed("feature1"));
  }
  
  @Test
  public void canFlush() {
    assertEquals(0, eventSink.flushCount);
    client.flush();
    assertEquals(1, eventSink.flushCount);
  }
  
  @Test
  public void identifyWithEventsDisabledDoesNotCauseError() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .events(Components.noEvents())
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      client.identify(user);
    }
  }
  
  @Test
  public void trackWithEventsDisabledDoesNotCauseError() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .events(Components.noEvents())
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      client.track("event", user);
    }
  }

  @Test
  public void flushWithEventsDisabledDoesNotCauseError() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .events(Components.noEvents())
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      client.flush();
    }
  }

  @Test
  public void aliasEventIsCorrectlyGenerated() {
    LDUser anonymousUser = new LDUser.Builder("anonymous-key").anonymous(true).build();

    client.alias(user, anonymousUser);

    assertEquals(1, eventSink.events.size());
    Event e = eventSink.events.get(0);
    assertEquals(Event.AliasEvent.class, e.getClass());
    Event.AliasEvent evt = (Event.AliasEvent)e;
    assertEquals(user.getKey(), evt.getKey());
    assertEquals("user", evt.getContextKind());
    assertEquals(anonymousUser.getKey(), evt.getPreviousKey());
    assertEquals("anonymousUser", evt.getPreviousContextKind());
  }

  private void checkFeatureEvent(Event e, DataModel.FeatureFlag flag, LDValue value, LDValue defaultVal,
      String prereqOf, EvaluationReason reason) {
    assertEquals(Event.FeatureRequest.class, e.getClass());
    Event.FeatureRequest fe = (Event.FeatureRequest)e;
    assertEquals(flag.getKey(), fe.getKey());
    assertEquals(user.getKey(), fe.getUser().getKey());
    assertEquals(flag.getVersion(), fe.getVersion());
    assertEquals(value, fe.getValue());
    assertEquals(defaultVal, fe.getDefaultVal());
    assertEquals(prereqOf, fe.getPrereqOf());
    assertEquals(reason, fe.getReason());
    assertEquals(flag.isTrackEvents(), fe.isTrackEvents());
    assertEquals(flag.getDebugEventsUntilDate() == null ? 0L : flag.getDebugEventsUntilDate().longValue(), fe.getDebugEventsUntilDate());
  }

  private void checkUnknownFeatureEvent(Event e, String key, LDValue defaultVal, String prereqOf,
      EvaluationReason reason) {
    assertEquals(Event.FeatureRequest.class, e.getClass());
    Event.FeatureRequest fe = (Event.FeatureRequest)e;
    assertEquals(key, fe.getKey());
    assertEquals(user.getKey(), fe.getUser().getKey());
    assertEquals(-1, fe.getVersion());
    assertEquals(-1, fe.getVariation());
    assertEquals(defaultVal, fe.getValue());
    assertEquals(defaultVal, fe.getDefaultVal());
    assertEquals(prereqOf, fe.getPrereqOf());
    assertEquals(reason, fe.getReason());
    assertFalse(fe.isTrackEvents());
    assertEquals(0L, fe.getDebugEventsUntilDate());
  }
}
