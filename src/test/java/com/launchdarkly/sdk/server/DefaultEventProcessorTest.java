package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.EventSender;
import com.launchdarkly.testhelpers.JsonTestValue;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

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
      assertThat(ec.eventsUri, equalTo(StandardEndpoints.DEFAULT_EVENTS_BASE_URI));
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
    try (DefaultEventProcessor ep = new DefaultEventProcessor(eventsConfig, sharedExecutor, Thread.MAX_PRIORITY,
        null, null, testLogger)) {
      Event.Custom event1 = EventFactory.DEFAULT.newCustomEvent("event1", user, null, null);
      Event.Custom event2 = EventFactory.DEFAULT.newCustomEvent("event2", user, null, null);
      ep.sendEvent(event1);
      ep.sendEvent(event2);
      
      // getEventsFromLastRequest will block until the MockEventSender receives a payload - we expect
      // both events to be in one payload, but if some unusual delay happened in between the two
      // sendEvent calls, they might be in two
      Iterable<JsonTestValue> payload1 = es.getEventsFromLastRequest();
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
        null, null, testLogger)) {
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
  public void eventProcessorCatchesExceptionWhenClosingEventSender() throws Exception {
    MockEventSender es = new MockEventSender();
    es.fakeErrorOnClose = new IOException("sorry");
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
    EventProcessorBuilder config = baseConfig(es).capacity(capacity)
        .flushInterval(Duration.ofSeconds(1));
    // The flush interval setting is a failsafe in case we do get a queue overflow due to the tiny buffer size -
    // that might cause the special message that's generated by ep.flush() to be missed, so we just want to make
    // sure a flush will happen within a few seconds so getEventsFromLastRequest() won't time out.
    
    try (DefaultEventProcessor ep = makeEventProcessor(config)) {
      for (int i = 0; i < capacity + 2; i++) {
        ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(user));
        
        // Using such a tiny buffer means there's also a tiny inbox queue, so we'll add a slight
        // delay to keep EventDispatcher from being overwhelmed
        Thread.sleep(10);
      }
      ep.flush();
      assertThat(es.getEventsFromLastRequest(), Matchers.iterableWithSize(capacity));
    }
  }

  @Test
  public void eventCapacityDoesNotPreventSummaryEventFromBeingSent() throws Exception {
    int capacity = 10;
    MockEventSender es = new MockEventSender();
    EventProcessorBuilder config = baseConfig(es).capacity(capacity).inlineUsersInEvents(true)
        .flushInterval(Duration.ofSeconds(1));
    // The flush interval setting is a failsafe in case we do get a queue overflow due to the tiny buffer size -
    // that might cause the special message that's generated by ep.flush() to be missed, so we just want to make
    // sure a flush will happen within a few seconds so getEventsFromLastRequest() won't time out.

    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();

    try (DefaultEventProcessor ep = makeEventProcessor(config)) {
      for (int i = 0; i < capacity; i++) {
        Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
            simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());
        ep.sendEvent(fe);
        
        // Using such a tiny buffer means there's also a tiny inbox queue, so we'll add a slight
        // delay to keep EventDispatcher from being overwhelmed
        Thread.sleep(10);
      }
      
      ep.flush();
      Iterable<JsonTestValue> eventsReceived = es.getEventsFromLastRequest(); 
      
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
  public void noMoreEventsAreProcessedAfterClosingEventProcessor() throws Exception {
    MockEventSender es = new MockEventSender();
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.close();
      
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
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventsAreKeptInBufferIfAllFlushWorkersAreBusy() throws Exception {
    // Note that in the current implementation, although the intention was that we would cancel a flush
    // if there's not an available flush worker, instead what happens is that we will queue *one* flush
    // in that case, and then cancel the *next* flush if the workers are still busy. This is because we
    // used a BlockingQueue with a size of 1, rather than a SynchronousQueue. The test below verifies
    // the current behavior.
    
    int numWorkers = 5; // must equal EventDispatcher.MAX_FLUSH_THREADS
    LDUser testUser1 = new LDUser("me");
    LDValue testUserJson1 = LDValue.buildObject().put("key", "me").build();
    LDUser testUser2 = new LDUser("you");
    LDValue testUserJson2 = LDValue.buildObject().put("key", "you").build();
    LDUser testUser3 = new LDUser("everyone we know");
    LDValue testUserJson3 = LDValue.buildObject().put("key", "everyone we know").build();
    
    Object sendersWaitOnThis = new Object();
    CountDownLatch sendersSignalThisWhenWaiting = new CountDownLatch(numWorkers);
    MockEventSender es = new MockEventSender();
    es.waitSignal = sendersWaitOnThis;
    es.receivedCounter = sendersSignalThisWhenWaiting;
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      for (int i = 0; i < 5; i++) {
        ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(user));
        ep.flush();
        es.awaitRequest(); // we don't need to see this payload, just throw it away
      }
      
      // When our CountDownLatch reaches zero, it means all of the worker threads are blocked in MockEventSender
      sendersSignalThisWhenWaiting.await();
      es.waitSignal = null;
      es.receivedCounter = null;
      
      // Now, put an event in the buffer and try to flush again. In the current implementation (see
      // above) this payload gets queued in a holding area, and will be flushed after a worker
      // becomes free.
      Event.Identify event1 = EventFactory.DEFAULT.newIdentifyEvent(testUser1);
      ep.sendEvent(event1);
      ep.flush();
      
      // Do an additional flush with another event. This time, the event processor should see that there's
      // no space available and simply ignore the flush request. There's no way to verify programmatically
      // that this has happened, so just give it a short delay.
      Event.Identify event2 = EventFactory.DEFAULT.newIdentifyEvent(testUser2);
      ep.sendEvent(event2);
      ep.flush();
      Thread.sleep(100);
      
      // Enqueue a third event. The current payload should now be event2 + event3.
      Event.Identify event3 = EventFactory.DEFAULT.newIdentifyEvent(testUser3);
      ep.sendEvent(event3);
      
      // Now allow the workers to unblock
      synchronized (sendersWaitOnThis) {
        sendersWaitOnThis.notifyAll();
      }
      
      // The first unblocked worker should pick up the queued payload with event1.
      assertThat(es.getEventsFromLastRequest(), contains(isIdentifyEvent(event1, testUserJson1)));

      // Now a flush should succeed and send the current payload.
      ep.flush();
      assertThat(es.getEventsFromLastRequest(), contains(
          isIdentifyEvent(event2, testUserJson2),
          isIdentifyEvent(event3, testUserJson3)));
    }
  }
}
