package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventSender;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Date;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;

/**
 * These DefaultEventProcessor tests cover the specific content that should appear in event payloads.
 */
@SuppressWarnings("javadoc")
public class DefaultEventProcessorOutputTest extends DefaultEventProcessorTestBase {
  private static final LDUser userWithNullKey = new LDUser(null);
  
  @Test
  public void identifyEventIsQueued() throws Exception {
    MockEventSender es = new MockEventSender();
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(e);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
      isIdentifyEvent(e, userJson)
    ));
  }
  
  @Test
  public void userIsFilteredInIdentifyEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).allAttributesPrivate(true))) {
      ep.sendEvent(e);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIdentifyEvent(e, filteredUserJson)
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void identifyEventWithNullUserOrNullUserKeyDoesNotCauseError() throws Exception {
    // This should never happen because LDClient.identify() rejects such a user, but just in case,
    // we want to make sure it doesn't blow up the event processor.
    MockEventSender es = new MockEventSender();
    Event event1 = EventFactory.DEFAULT.newIdentifyEvent(userWithNullKey);
    Event event2 = EventFactory.DEFAULT.newIdentifyEvent(null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).allAttributesPrivate(true))) {
      ep.sendEvent(event1);
      ep.sendEvent(event2);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIdentifyEvent(event1, LDValue.buildObject().build()),
        isIdentifyEvent(event2, LDValue.ofNull())
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void individualFeatureEventIsQueuedWithIndexEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, false, null),
        isSummaryEvent()
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInIndexEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).allAttributesPrivate(true))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, filteredUserJson),
        isFeatureEvent(fe, flag, false, null),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanContainInlineUser() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).inlineUsersInEvents(true))) {
      ep.sendEvent(fe);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isFeatureEvent(fe, flag, false, userJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInFeatureEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es)
        .inlineUsersInEvents(true).allAttributesPrivate(true))) {
      ep.sendEvent(fe);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isFeatureEvent(fe, flag, false, filteredUserJson),
        isSummaryEvent()
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanBeForPrerequisite() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag mainFlag = flagBuilder("flagkey").version(11).build();
    DataModel.FeatureFlag prereqFlag = flagBuilder("prereqkey").version(12).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newPrerequisiteFeatureRequestEvent(prereqFlag, user,
        simpleEvaluation(1, LDValue.of("value")),
        mainFlag);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        allOf(isFeatureEvent(fe, prereqFlag, false, null), isPrerequisiteOf(mainFlag.getKey())),
        isSummaryEvent()
    ));
  }

  @Test
  public void featureEventWithNullUserOrNullUserKeyIsIgnored() throws Exception {
    // This should never happen because LDClient rejects such a user, but just in case,
    // we want to make sure it doesn't blow up the event processor.
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).build();
    Event.FeatureRequest event1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag, userWithNullKey,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());
    Event.FeatureRequest event2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag, null,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es)
        .inlineUsersInEvents(true).allAttributesPrivate(true))) {
      ep.sendEvent(event1);
      ep.sendEvent(event2);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanContainReason() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    EvaluationReason reason = EvaluationReason.ruleMatch(1, null);
    Event.FeatureRequest fe = EventFactory.DEFAULT_WITH_REASONS.newFeatureRequestEvent(flag, user,
          EvalResult.of(LDValue.of("value"), 1, reason), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, false, null, reason),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void indexEventIsStillGeneratedIfInlineUsersIsTrueButFeatureEventIsNotTracked() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(false).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).inlineUsersInEvents(true))) {
      ep.sendEvent(fe);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventKindIsDebugIfFlagIsTemporarilyInDebugMode() throws Exception {
    MockEventSender es = new MockEventSender();
    long futureTime = System.currentTimeMillis() + 1000000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).debugEventsUntilDate(futureTime).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, true, userJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventCanBeBothTrackedAndDebugged() throws Exception {
    MockEventSender es = new MockEventSender();
    long futureTime = System.currentTimeMillis() + 1000000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true)
        .debugEventsUntilDate(futureTime).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, false, null),
        isFeatureEvent(fe, flag, true, userJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void debugModeExpiresBasedOnClientTimeIfClientTimeIsLaterThanServerTime() throws Exception {
    MockEventSender es = new MockEventSender();
    
    // Pick a server time that is somewhat behind the client time
    long serverTime = System.currentTimeMillis() - 20000;
    es.result = new EventSender.Result(true, false, new Date(serverTime));
    
    long debugUntil = serverTime + 1000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      // Send and flush an event we don't care about, just so we'll receive "resp1" which sets the last server time
      ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
      ep.flush();
      ep.waitUntilInactive(); // this ensures that it has received the first response, with the date
      
      es.receivedParams.clear();
      es.result = new EventSender.Result(true, false, null);
      
      // Now send an event with debug mode on, with a "debug until" time that is further in
      // the future than the server time, but in the past compared to the client.
      ep.sendEvent(fe);
    }
    
    // Should get a summary event only, not a full feature event
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isSummaryEvent(fe.getCreationDate(), fe.getCreationDate())
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void debugModeExpiresBasedOnServerTimeIfServerTimeIsLaterThanClientTime() throws Exception {
    MockEventSender es = new MockEventSender();
    
    // Pick a server time that is somewhat ahead of the client time
    long serverTime = System.currentTimeMillis() + 20000;
    es.result = new EventSender.Result(true, false, new Date(serverTime));
    
    long debugUntil = serverTime - 1000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      // Send and flush an event we don't care about, just to set the last server time
      ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
      ep.flush();
      ep.waitUntilInactive(); // this ensures that it has received the first response, with the date
      
      es.receivedParams.clear();
      es.result = new EventSender.Result(true, false, null);

      // Now send an event with debug mode on, with a "debug until" time that is further in
      // the future than the client time, but in the past compared to the server.
      ep.sendEvent(fe);
    }
    
    // Should get a summary event only, not a full feature event
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isSummaryEvent(fe.getCreationDate(), fe.getCreationDate())
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void twoFeatureEventsForSameUserGenerateOnlyOneIndexEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag1 = flagBuilder("flagkey1").version(11).trackEvents(true).build();
    DataModel.FeatureFlag flag2 = flagBuilder("flagkey2").version(22).trackEvents(true).build();
    LDValue value = LDValue.of("value");
    Event.FeatureRequest fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value), LDValue.ofNull());
    Event.FeatureRequest fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        simpleEvaluation(1, value), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe1);
      ep.sendEvent(fe2);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe1, userJson),
        isFeatureEvent(fe1, flag1, false, null),
        isFeatureEvent(fe2, flag2, false, null),
        isSummaryEvent(fe1.getCreationDate(), fe2.getCreationDate())
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void identifyEventMakesIndexEventUnnecessary() throws Exception {
    MockEventSender es = new MockEventSender();
    Event ie = EventFactory.DEFAULT.newIdentifyEvent(user);
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(ie);
      ep.sendEvent(fe); 
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isIdentifyEvent(ie, userJson),
        isFeatureEvent(fe, flag, false, null),
        isSummaryEvent()
    ));
  }

  
  @SuppressWarnings("unchecked")
  @Test
  public void nonTrackedEventsAreSummarized() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag1 = flagBuilder("flagkey1").version(11).build();
    DataModel.FeatureFlag flag2 = flagBuilder("flagkey2").version(22).build();
    LDValue value1 = LDValue.of("value1");
    LDValue value2 = LDValue.of("value2");
    LDValue default1 = LDValue.of("default1");
    LDValue default2 = LDValue.of("default2");
    Event fe1a = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value1), default1);
    Event fe1b = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value1), default1);
    Event fe1c = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(2, value2), default1);
    Event fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        simpleEvaluation(2, value2), default2);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe1a);
      ep.sendEvent(fe1b);
      ep.sendEvent(fe1c);
      ep.sendEvent(fe2);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe1a, userJson),
        allOf(
            isSummaryEvent(fe1a.getCreationDate(), fe2.getCreationDate()),
            hasSummaryFlag(flag1.getKey(), default1,
                Matchers.containsInAnyOrder(
                    isSummaryEventCounter(flag1, 1, value1, 2),
                    isSummaryEventCounter(flag1, 2, value2, 1)
                )),
            hasSummaryFlag(flag2.getKey(), default2,
                contains(isSummaryEventCounter(flag2, 2, value2, 1)))
        )
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void customEventIsQueuedWithUser() throws Exception {
    MockEventSender es = new MockEventSender();
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    double metric = 1.5;
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data, metric);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(ce);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(ce, userJson),
        isCustomEvent(ce, null)
    ));
  }

  @Test
  public void customEventCanContainInlineUser() throws Exception {
    MockEventSender es = new MockEventSender();
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data, null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).inlineUsersInEvents(true))) {
      ep.sendEvent(ce);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(isCustomEvent(ce, userJson)));
  }
  
  @Test
  public void userIsFilteredInCustomEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data, null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es)
        .inlineUsersInEvents(true).allAttributesPrivate(true))) {
      ep.sendEvent(ce);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(isCustomEvent(ce, filteredUserJson)));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void customEventWithNullUserOrNullUserKeyDoesNotCauseError() throws Exception {
    // This should never happen because LDClient rejects such a user, but just in case,
    // we want to make sure it doesn't blow up the event processor.
    MockEventSender es = new MockEventSender();
    Event.Custom event1 = EventFactory.DEFAULT.newCustomEvent("eventkey", userWithNullKey, LDValue.ofNull(), null);
    Event.Custom event2 = EventFactory.DEFAULT.newCustomEvent("eventkey", null, LDValue.ofNull(), null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es)
        .inlineUsersInEvents(true).allAttributesPrivate(true))) {
      ep.sendEvent(event1);
      ep.sendEvent(event2);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isCustomEvent(event1, LDValue.buildObject().build()),
        isCustomEvent(event2, LDValue.ofNull())
    ));
  }
  
  @Test
  public void aliasEventIsQueued() throws Exception {
    MockEventSender es = new MockEventSender();
    LDUser user1 = new LDUser.Builder("anon-user").anonymous(true).build();
    LDUser user2 = new LDUser("non-anon-user");
    Event.AliasEvent event = EventFactory.DEFAULT.newAliasEvent(user2, user1);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(event);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isAliasEvent(event)
    ));
  }
}
