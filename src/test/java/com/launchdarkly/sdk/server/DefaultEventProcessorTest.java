package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.EventSender;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;

/**
 * These tests cover all of the basic DefaultEventProcessor behavior that is not covered by
 * DefaultEventProcessorOutputTest or DefaultEventProcessorDiagnosticTest.
 */
@SuppressWarnings("javadoc")
public class DefaultEventProcessorTest extends DefaultEventProcessorTestBase {
  @Test
  public void builderHasDefaultConfiguration() throws Exception {
    EventProcessorFactory epf = Components.sendEvents();
    try (DefaultEventProcessor ep = (DefaultEventProcessor)epf.createEventProcessor(clientContext(SDK_KEY, LDConfig.DEFAULT))) {
      EventsConfiguration ec = ep.dispatcher.eventsConfig;
      assertThat(ec.allAttributesPrivate, is(false));
      assertThat(ec.capacity, equalTo(EventProcessorBuilder.DEFAULT_CAPACITY));
      assertThat(ec.diagnosticRecordingInterval, equalTo(EventProcessorBuilder.DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL));
      assertThat(ec.eventSender, instanceOf(DefaultEventSender.class));
      assertThat(ec.eventsUri, equalTo(LDConfig.DEFAULT_EVENTS_URI));
      assertThat(ec.flushInterval, equalTo(EventProcessorBuilder.DEFAULT_FLUSH_INTERVAL));
      assertThat(ec.inlineUsersInEvents, is(false));
      assertThat(ec.privateAttributes, equalTo(ImmutableSet.<UserAttribute>of()));
      assertThat(ec.userKeysCapacity, equalTo(EventProcessorBuilder.DEFAULT_USER_KEYS_CAPACITY));
      assertThat(ec.userKeysFlushInterval, equalTo(EventProcessorBuilder.DEFAULT_USER_KEYS_FLUSH_INTERVAL));
    }
  }
  
  @Test
  public void builderCanSpecifyConfiguration() throws Exception {
    MockEventSender es = new MockEventSender();
    EventProcessorFactory epf = Components.sendEvents()
        .allAttributesPrivate(true)
        .baseURI(FAKE_URI)
        .capacity(3333)
        .diagnosticRecordingInterval(Duration.ofSeconds(480))
        .eventSender(senderFactory(es))
        .flushInterval(Duration.ofSeconds(99))
        .privateAttributeNames("name", "dogs")
        .userKeysCapacity(555)
        .userKeysFlushInterval(Duration.ofSeconds(101));
    try (DefaultEventProcessor ep = (DefaultEventProcessor)epf.createEventProcessor(clientContext(SDK_KEY, LDConfig.DEFAULT))) {
      EventsConfiguration ec = ep.dispatcher.eventsConfig;
      assertThat(ec.allAttributesPrivate, is(true));
      assertThat(ec.capacity, equalTo(3333));
      assertThat(ec.diagnosticRecordingInterval, equalTo(Duration.ofSeconds(480)));
      assertThat(ec.eventSender, sameInstance((EventSender)es));
      assertThat(ec.eventsUri, equalTo(FAKE_URI));
      assertThat(ec.flushInterval, equalTo(Duration.ofSeconds(99)));
      assertThat(ec.inlineUsersInEvents, is(false)); // will test this separately below
      assertThat(ec.privateAttributes, equalTo(ImmutableSet.of(UserAttribute.NAME, UserAttribute.forName("dogs"))));
      assertThat(ec.userKeysCapacity, equalTo(555));
      assertThat(ec.userKeysFlushInterval, equalTo(Duration.ofSeconds(101)));
    }
    // Test inlineUsersInEvents separately to make sure it and the other boolean property (allAttributesPrivate)
    // are really independently settable, since there's no way to distinguish between two true values
    EventProcessorFactory epf1 = Components.sendEvents().inlineUsersInEvents(true);
    try (DefaultEventProcessor ep = (DefaultEventProcessor)epf1.createEventProcessor(clientContext(SDK_KEY, LDConfig.DEFAULT))) {
      EventsConfiguration ec = ep.dispatcher.eventsConfig;
      assertThat(ec.allAttributesPrivate, is(false));
      assertThat(ec.inlineUsersInEvents, is(true));
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void eventsAreFlushedAutomatically() throws Exception {
    MockEventSender es = new MockEventSender();
    Duration briefFlushInterval = Duration.ofMillis(50);
    
    // Can't use the regular config builder for this, because it will enforce a minimum flush interval
    EventsConfiguration eventsConfig = new EventsConfiguration(
        false,
        100,
        es,
        FAKE_URI,
        briefFlushInterval,
        true,
        ImmutableSet.of(),
        100,
        Duration.ofSeconds(5),
        null
        );
    try (DefaultEventProcessor ep = new DefaultEventProcessor(eventsConfig, sharedExecutor, Thread.MAX_PRIORITY, null, null)) {
      Event.Custom event1 = EventFactory.DEFAULT.newCustomEvent("event1", user, null, null);
      Event.Custom event2 = EventFactory.DEFAULT.newCustomEvent("event2", user, null, null);
      ep.sendEvent(event1);
      ep.sendEvent(event2);
      
      // getEventsFromLastRequest will block until the MockEventSender receives a payload - we expect
      // both events to be in one payload, but if some unusual delay happened in between the two
      // sendEvent calls, they might be in two
      Iterable<LDValue> payload1 = es.getEventsFromLastRequest();
      if (Iterables.size(payload1) == 1) {
        assertThat(payload1, contains(isCustomEvent(event1, userJson)));
        assertThat(es.getEventsFromLastRequest(), contains(isCustomEvent(event2, userJson)));
      } else {
        assertThat(payload1, contains(isCustomEvent(event1, userJson), isCustomEvent(event2, userJson)));
      }
      
      Event.Custom event3 = EventFactory.DEFAULT.newCustomEvent("event3", user, null, null);
      ep.sendEvent(event3);
      assertThat(es.getEventsFromLastRequest(), contains(isCustomEvent(event3, userJson)));
    }
    
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
  public void closingEventProcessorForcesSynchronousFlush() throws Exception {
    MockEventSender es = new MockEventSender();
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(e);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(isIdentifyEvent(e, userJson)));
  }
  
  @Test
  public void nothingIsSentIfThereAreNoEvents() throws Exception {
    MockEventSender es = new MockEventSender();
    DefaultEventProcessor ep = makeEventProcessor(baseConfig(es));
    ep.close();
    
    assertEquals(0, es.receivedParams.size());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void userKeysAreFlushedAutomatically() throws Exception {
    // This test overrides the user key flush interval to a small value and verifies that a new
    // index event is generated for a user after the user keys have been flushed.
    MockEventSender es = new MockEventSender();
    Duration briefUserKeyFlushInterval = Duration.ofMillis(60);
    
    // Can't use the regular config builder for this, because it will enforce a minimum flush interval
    EventsConfiguration eventsConfig = new EventsConfiguration(
        false,
        100,
        es,
        FAKE_URI,
        Duration.ofSeconds(5),
        false, // do not inline users in events
        ImmutableSet.of(),
        100,
        briefUserKeyFlushInterval,
        null
        );
    try (DefaultEventProcessor ep = new DefaultEventProcessor(eventsConfig, sharedExecutor, Thread.MAX_PRIORITY,
        null, null)) {
      Event.Custom event1 = EventFactory.DEFAULT.newCustomEvent("event1", user, null, null);
      Event.Custom event2 = EventFactory.DEFAULT.newCustomEvent("event2", user, null, null);
      ep.sendEvent(event1);
      ep.sendEvent(event2);
      
      // We're relying on the user key flush not happening in between event1 and event2, so we should get
      // a single index event for the user.
      ep.flush();
      assertThat(es.getEventsFromLastRequest(), contains(
          isIndexEvent(event1, userJson),
          isCustomEvent(event1, null),
          isCustomEvent(event2, null)
      ));

      // Now wait long enough for the user key cache to be flushed
      Thread.sleep(briefUserKeyFlushInterval.toMillis() * 2);

      // Referencing the same user in a new even should produce a new index event
      Event.Custom event3 = EventFactory.DEFAULT.newCustomEvent("event3", user, null, null);
      ep.sendEvent(event3);
      ep.flush();
      assertThat(es.getEventsFromLastRequest(), contains(
          isIndexEvent(event3, userJson),
          isCustomEvent(event3, null)
      ));
    }
  }
  
  @Test
  public void eventSenderIsClosedWithEventProcessor() throws Exception {
    MockEventSender es = new MockEventSender();
    assertThat(es.closed, is(false));
    DefaultEventProcessor ep = makeEventProcessor(baseConfig(es));
    ep.close();
    assertThat(es.closed, is(true));
  }
  
  @Test
  public void customBaseUriIsPassedToEventSenderForAnalyticsEvents() throws Exception {
    MockEventSender es = new MockEventSender();
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    URI uri = URI.create("fake-uri");

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).baseURI(uri))) {
      ep.sendEvent(e);
    }

    MockEventSender.Params p = es.awaitRequest();
    assertThat(p.eventsBaseUri, equalTo(uri));
  }
  
  @Test
  public void eventCapacityIsEnforced() throws Exception {
    int capacity = 10;
    MockEventSender es = new MockEventSender();
    EventProcessorBuilder config = baseConfig(es).capacity(capacity);
    
    try (DefaultEventProcessor ep = makeEventProcessor(config)) {
      for (int i = 0; i < capacity + 2; i++) {
        ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(user));
      }
      ep.flush();
      assertThat(es.getEventsFromLastRequest(), Matchers.iterableWithSize(capacity));
    }
  }

  @Test
  public void eventCapacityDoesNotPreventSummaryEventFromBeingSent() throws Exception {
    int capacity = 10;
    MockEventSender es = new MockEventSender();
    EventProcessorBuilder config = baseConfig(es).capacity(capacity).inlineUsersInEvents(true);
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();

    try (DefaultEventProcessor ep = makeEventProcessor(config)) {
      for (int i = 0; i < capacity; i++) {
        Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
            simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());
        ep.sendEvent(fe);
        
        // Using such a tiny buffer means there's also a tiny inbox queue, so we'll add a slight
        // delay to keep EventDispatcher from being overwhelmed
        Thread.sleep(1);
      }
      
      ep.flush();
      Iterable<LDValue> eventsReceived = es.getEventsFromLastRequest(); 
      
      assertThat(eventsReceived, Matchers.iterableWithSize(capacity + 1));
      assertThat(Iterables.get(eventsReceived, capacity), isSummaryEvent());
    }
  }
  
  @Test
  public void noMoreEventsAreProcessedAfterUnrecoverableError() throws Exception {
    MockEventSender es = new MockEventSender();
    es.result = new EventSender.Result(false, true, null); // mustShutdown == true
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(user));
      ep.flush();
      es.awaitRequest();
      
      // allow a little time for the event processor to pass the "must shut down" signal back from the sender
      Thread.sleep(50);
      
      ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(user));
      ep.flush();
      es.expectNoRequests(Duration.ofMillis(100));
    }
  }

  @Test
  public void uncheckedExceptionFromEventSenderDoesNotStopWorkerThread() throws Exception {
    MockEventSender es = new MockEventSender();
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      es.fakeError = new RuntimeException("sorry");
      
      ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(user));
      ep.flush();
      es.awaitRequest();
      // MockEventSender now throws an unchecked exception up to EventProcessor's flush worker -
      // verify that a subsequent flush still works
      
      es.fakeError = null;
      ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(user));
      ep.flush();
      es.awaitRequest();
    }
  }
}
