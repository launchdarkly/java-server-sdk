package com.launchdarkly.sdk.internal.events;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;

/**
 * These DefaultEventProcessor tests cover the specific content that should appear in event payloads.
 */
@SuppressWarnings("javadoc")
public class DefaultEventProcessorOutputTest extends BaseEventTest {
  private static final LDContext invalidContext = LDContext.create(null);
  
  // Note: context deduplication behavior has been abstracted out of DefaultEventProcessor, so that
  // by default it does not generate any index events. Test cases in this file that are not
  // specifically related to index events use this default behavior, and do not expect to see any.
  // When we are specifically testing this behavior, we substitute a mock EventContextDeduplicator
  // so we can verify how its outputs affect DefaultEventProcessor.
  
  @Test
  public void identifyEventIsQueued() throws Exception {
    MockEventSender es = new MockEventSender();
    Event e = identifyEvent(user);

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
    Event e = identifyEvent(user);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).allAttributesPrivate(true))) {
      ep.sendEvent(e);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIdentifyEvent(e, filteredUserJson)
    ));
  }

  @Test
  public void identifyEventWithNullContextOrInvalidContextDoesNotCauseError() throws Exception {
    // This should never happen because LDClient.identify() rejects such a user, but just in case,
    // we want to make sure it doesn't blow up the event processor.
    MockEventSender es = new MockEventSender();
    Event event1 = identifyEvent(invalidContext);
    Event event2 = identifyEvent(null);
    Event event3 = identifyEvent(user);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(event1);
      ep.sendEvent(event2);
      ep.sendEvent(event3);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIdentifyEvent(event3, userJson)
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void individualFeatureEventIsQueuedWithIndexEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    Event.FeatureRequest fe = featureEvent(user, FLAG_KEY).trackEvents(true).build();

    EventContextDeduplicator contextDeduplicator = contextDeduplicatorThatAlwaysSaysKeysAreNew();
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).contextDeduplicator(contextDeduplicator))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe),
        isSummaryEvent()
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInIndexEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    Event.FeatureRequest fe = featureEvent(user, FLAG_KEY).build();

    EventContextDeduplicator contextDeduplicator = contextDeduplicatorThatAlwaysSaysKeysAreNew();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).allAttributesPrivate(true).contextDeduplicator(contextDeduplicator))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, filteredUserJson),
        isSummaryEvent()
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanBeForPrerequisite() throws Exception {
    MockEventSender es = new MockEventSender();
    String prereqKey = "prereqkey";
    Event.FeatureRequest fe = featureEvent(user, prereqKey).prereqOf(FLAG_KEY).trackEvents(true).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isFeatureEvent(fe),
        isSummaryEvent()
    ));
  }

  @Test
  public void featureEventWithNullContextOrInvalidContextIsIgnored() throws Exception {
    // This should never happen because LDClient rejects such a user, but just in case,
    // we want to make sure it doesn't blow up the event processor.
    MockEventSender es = new MockEventSender();
    Event.FeatureRequest event1 = featureEvent(invalidContext, FLAG_KEY).build();
    Event.FeatureRequest event2 = featureEvent(null, FLAG_KEY).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es)
        .allAttributesPrivate(true))) {
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
    EvaluationReason reason = EvaluationReason.ruleMatch(1, null);
    Event.FeatureRequest fe = featureEvent(user, FLAG_KEY).reason(reason).trackEvents(true).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isFeatureEvent(fe),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventKindIsDebugIfFlagIsTemporarilyInDebugMode() throws Exception {
    MockEventSender es = new MockEventSender();
    long futureTime = System.currentTimeMillis() + 1000000;
    Event.FeatureRequest fe = featureEvent(user, FLAG_KEY).debugEventsUntilDate(futureTime).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isDebugEvent(fe, userJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventCanBeBothTrackedAndDebugged() throws Exception {
    MockEventSender es = new MockEventSender();
    long futureTime = System.currentTimeMillis() + 1000000;
    Event.FeatureRequest fe = featureEvent(user, FLAG_KEY).trackEvents(true).debugEventsUntilDate(futureTime).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isFeatureEvent(fe),
        isDebugEvent(fe, userJson),
        isSummaryEvent()
    ));
  }
  
  @Test
  public void debugModeExpiresBasedOnClientTimeIfClientTimeIsLaterThanServerTime() throws Exception {
    MockEventSender es = new MockEventSender();
    
    // Pick a server time that is somewhat behind the client time
    long serverTime = System.currentTimeMillis() - 20000;
    es.result = new EventSender.Result(true, false, new Date(serverTime));
    
    long debugUntil = serverTime + 1000;
    Event.FeatureRequest fe = featureEvent(user, FLAG_KEY).debugEventsUntilDate(debugUntil).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      // Send and flush an event we don't care about, just so we'll receive "resp1" which sets the last server time
      ep.sendEvent(identifyEvent(LDContext.create("otherUser")));
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
        isSummaryEvent(fe.getCreationDate(), fe.getCreationDate())
    ));
  }

  @Test
  public void debugModeExpiresBasedOnServerTimeIfServerTimeIsLaterThanClientTime() throws Exception {
    MockEventSender es = new MockEventSender();
    
    // Pick a server time that is somewhat ahead of the client time
    long serverTime = System.currentTimeMillis() + 20000;
    es.result = new EventSender.Result(true, false, new Date(serverTime));
    
    long debugUntil = serverTime - 1000;
    Event.FeatureRequest fe = featureEvent(user, FLAG_KEY).debugEventsUntilDate(debugUntil).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      // Send and flush an event we don't care about, just to set the last server time
      ep.sendEvent(identifyEvent(LDContext.create("otherUser")));
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
        isSummaryEvent(fe.getCreationDate(), fe.getCreationDate())
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void twoFeatureEventsForSameContextGenerateOnlyOneIndexEvent() throws Exception {
    // More accurately, this is testing that DefaultEventProcessor respects whatever the
    // EventContextDeduplicator says about whether a context key is new or not. We will set up
    // an EventContextDeduplicator that reports "new" on the first call and "not new" on the 2nd.
    EventContextDeduplicator contextDeduplicator = contextDeduplicatorThatSaysKeyIsNewOnFirstCallOnly();
    
    MockEventSender es = new MockEventSender();
    Event.FeatureRequest fe1 = featureEvent(user, "flagkey1").trackEvents(true).build();
    Event.FeatureRequest fe2 = featureEvent(user, "flagkey2").trackEvents(true).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).contextDeduplicator(contextDeduplicator))) {
      ep.sendEvent(fe1);
      ep.sendEvent(fe2);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe1, userJson),
        isFeatureEvent(fe1),
        isFeatureEvent(fe2),
        isSummaryEvent(fe1.getCreationDate(), fe2.getCreationDate())
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void identifyEventMakesIndexEventUnnecessary() throws Exception {
    MockEventSender es = new MockEventSender();
    Event ie = new Event.Identify(FAKE_TIME, user);
    Event.FeatureRequest fe = featureEvent(user, FLAG_KEY).trackEvents(true).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(ie);
      ep.sendEvent(fe); 
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isIdentifyEvent(ie, userJson),
        isFeatureEvent(fe),
        isSummaryEvent()
    ));
  }

  
  @SuppressWarnings("unchecked")
  @Test
  public void nonTrackedEventsAreSummarized() throws Exception {
    MockEventSender es = new MockEventSender();
    String flagkey1 = "flagkey1", flagkey2 = "flagkey2";
    int version1 = 11, version2 = 22;
    LDValue value1 = LDValue.of("value1"), value2 = LDValue.of("value2");
    LDValue default1 = LDValue.of("default1"), default2 = LDValue.of("default2");
    Event fe1a = featureEvent(user, flagkey1).flagVersion(version1)
        .variation(1).value(value1).defaultValue(default1).build();
    Event fe1b = featureEvent(user, flagkey1).flagVersion(version1)
        .variation(1).value(value1).defaultValue(default1).build();
    Event fe1c = featureEvent(user, flagkey1).flagVersion(version1)
        .variation(2).value(value2).defaultValue(default1).build();
    Event fe2 = featureEvent(user, flagkey2).flagVersion(version2)
        .variation(2).value(value2).defaultValue(default2).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe1a);
      ep.sendEvent(fe1b);
      ep.sendEvent(fe1c);
      ep.sendEvent(fe2);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        allOf(
            isSummaryEvent(fe1a.getCreationDate(), fe2.getCreationDate()),
            hasSummaryFlag(flagkey1, default1,
                Matchers.containsInAnyOrder(
                    isSummaryEventCounter(version1, 1, value1, 2),
                    isSummaryEventCounter(version1, 2, value2, 1)
                )),
            hasSummaryFlag(flagkey2, default2,
                contains(isSummaryEventCounter(version2, 2, value2, 1)))
        )
    ));
  }
  
  @Test
  public void customEventIsQueuedWithUser() throws Exception {
    MockEventSender es = new MockEventSender();
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    double metric = 1.5;
    Event.Custom ce = customEvent(user, "eventkey").data(data).metricValue(metric).build();

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(ce);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isCustomEvent(ce)
    ));
  }
  
  @Test
  public void customEventWithNullContextOrInvalidContextDoesNotCauseError() throws Exception {
    // This should never happen because LDClient rejects such a user, but just in case,
    // we want to make sure it doesn't blow up the event processor.
    MockEventSender es = new MockEventSender();
    Event.Custom event1 = customEvent(invalidContext, "eventkey").build();
    Event.Custom event2 = customEvent(null, "eventkey").build();
    Event.Custom event3 = customEvent(user, "eventkey").build();
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(event1);
      ep.sendEvent(event2);
      ep.sendEvent(event3);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isCustomEvent(event3)
    ));
  }
}
