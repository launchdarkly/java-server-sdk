package com.launchdarkly.sdk.server;

import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.testhelpers.JsonTestValue;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * These tests cover all of the basic DefaultEventProcessor behavior that is not covered by
 * DefaultEventProcessorOutputTest or DefaultEventProcessorDiagnosticTest.
 */
@SuppressWarnings("javadoc")
public class DefaultEventProcessorTest extends EventTestUtil {
  @SuppressWarnings("unchecked")
  @Test
  public void eventsAreFlushedAutomatically() throws Exception {
    MockEventSender es = new MockEventSender();
    Duration briefFlushInterval = Duration.ofMillis(50);
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).flushInterval(briefFlushInterval))) {
      Event.Custom event1 = makeCustomEvent("event1", user, null, null);
      Event.Custom event2 = makeCustomEvent("event2", user, null, null);
      ep.sendEvent(event1);
      ep.sendEvent(event2);
      
      // getEventsFromLastRequest will block until the MockEventSender receives a payload - we expect
      // both events to be in one payload, but if some unusual delay happened in between the two
      // sendEvent calls, they might be in two
      Iterable<JsonTestValue> payload1 = es.getEventsFromLastRequest();
      if (Iterables.size(payload1) == 1) {
        assertThat(payload1, contains(isCustomEvent(event1)));
        assertThat(es.getEventsFromLastRequest(), contains(isCustomEvent(event2)));
      } else {
        assertThat(payload1, contains(isCustomEvent(event1), isCustomEvent(event2)));
      }
      
      Event.Custom event3 = makeCustomEvent("event3", user, null, null);
      ep.sendEvent(event3);
      assertThat(es.getEventsFromLastRequest(), contains(isCustomEvent(event3)));
    }
    
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    double metric = 1.5;
    Event.Custom ce = makeCustomEvent("eventkey", user, data, metric);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(ce);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isCustomEvent(ce)
    ));
  }

  @Test
  public void closingEventProcessorForcesSynchronousFlush() throws Exception {
    MockEventSender es = new MockEventSender();
    Event e = makeIdentifyEvent(user);
    
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

  @Test
  public void contextKeysAreFlushedAutomatically() throws Exception {
    // This test sets the context key flush interval to a small value and verifies that the
    // context deduplicator receives a flush call.
    MockEventSender es = new MockEventSender();
    long briefContextFlushIntervalMillis = 60;
    Semaphore flushCalled = new Semaphore(0);
    EventContextDeduplicator contextDeduplicator = new EventContextDeduplicator() {
      @Override
      public Long getFlushInterval() {
        return briefContextFlushIntervalMillis;
      }

      @Override
      public boolean processContext(LDContext context) {
        return false;
      }

      @Override
      public void flush() {
        flushCalled.release();
      }
    };
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).contextDeduplicator(contextDeduplicator))) {
      boolean called = flushCalled.tryAcquire(briefContextFlushIntervalMillis * 2, TimeUnit.MILLISECONDS);
      assertTrue("expected context deduplicator flush method to be called, but it was not", called);
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
    Event e = makeIdentifyEvent(user);
    URI uri = URI.create("fake-uri");

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).eventsUri(uri))) {
      ep.sendEvent(e);
    }

    MockEventSender.Params p = es.awaitRequest();
    assertThat(p.eventsBaseUri, equalTo(uri));
  }
  
  @Test
  public void eventCapacityIsEnforced() throws Exception {
    int capacity = 10;
    MockEventSender es = new MockEventSender();
    EventsConfigurationBuilder config = baseConfig(es).capacity(capacity)
        .flushInterval(Duration.ofSeconds(1));
    // The flush interval setting is a failsafe in case we do get a queue overflow due to the tiny buffer size -
    // that might cause the special message that's generated by ep.flush() to be missed, so we just want to make
    // sure a flush will happen within a few seconds so getEventsFromLastRequest() won't time out.
    
    try (DefaultEventProcessor ep = makeEventProcessor(config)) {
      for (int i = 0; i < capacity + 2; i++) {
        ep.sendEvent(makeIdentifyEvent(user));
        
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
    EventsConfigurationBuilder config = baseConfig(es).capacity(capacity)
        .flushInterval(Duration.ofSeconds(1));
    // The flush interval setting is a failsafe in case we do get a queue overflow due to the tiny buffer size -
    // that might cause the special message that's generated by ep.flush() to be missed, so we just want to make
    // sure a flush will happen within a few seconds so getEventsFromLastRequest() won't time out.

    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();

    try (DefaultEventProcessor ep = makeEventProcessor(config)) {
      Event.FeatureRequest fe = makeFeatureRequestEvent(flag, user,
          simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());
      ep.sendEvent(fe);
     
      for (int i = 0; i < capacity; i++) {
        Event.Custom ce = makeCustomEvent("event-key", user, LDValue.ofNull(), null);
        ep.sendEvent(ce);
        
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
      ep.sendEvent(makeIdentifyEvent(user));
      ep.flush();
      es.awaitRequest();
      
      // allow a little time for the event processor to pass the "must shut down" signal back from the sender
      Thread.sleep(50);
      
      ep.sendEvent(makeIdentifyEvent(user));
      ep.flush();
      es.expectNoRequests(Duration.ofMillis(100));
    }
  }

  @Test
  public void noMoreEventsAreProcessedAfterClosingEventProcessor() throws Exception {
    MockEventSender es = new MockEventSender();
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.close();
      
      ep.sendEvent(makeIdentifyEvent(user));
      ep.flush();
      
      es.expectNoRequests(Duration.ofMillis(100));
    }
  }

  @Test
  public void uncheckedExceptionFromEventSenderDoesNotStopWorkerThread() throws Exception {
    MockEventSender es = new MockEventSender();
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      es.fakeError = new RuntimeException("sorry");
      
      ep.sendEvent(makeIdentifyEvent(user));
      ep.flush();
      es.awaitRequest();
      // MockEventSender now throws an unchecked exception up to EventProcessor's flush worker -
      // verify that a subsequent flush still works
      
      es.fakeError = null;
      ep.sendEvent(makeIdentifyEvent(user));
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
    LDContext testUser1 = LDContext.create("me");
    LDValue testUserJson1 = LDValue.buildObject().put("kind", "user").put("key", "me").build();
    LDContext testUser2 = LDContext.create("you");
    LDValue testUserJson2 = LDValue.buildObject().put("kind", "user").put("key", "you").build();
    LDContext testUser3 = LDContext.create("everyone we know");
    LDValue testUserJson3 = LDValue.buildObject().put("kind", "user").put("key", "everyone we know").build();
    
    Object sendersWaitOnThis = new Object();
    CountDownLatch sendersSignalThisWhenWaiting = new CountDownLatch(numWorkers);
    MockEventSender es = new MockEventSender();
    es.waitSignal = sendersWaitOnThis;
    es.receivedCounter = sendersSignalThisWhenWaiting;
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      for (int i = 0; i < 5; i++) {
        ep.sendEvent(makeIdentifyEvent(user));
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
      Event.Identify event1 = makeIdentifyEvent(testUser1);
      ep.sendEvent(event1);
      ep.flush();
      
      // Do an additional flush with another event. This time, the event processor should see that there's
      // no space available and simply ignore the flush request. There's no way to verify programmatically
      // that this has happened, so just give it a short delay.
      Event.Identify event2 = makeIdentifyEvent(testUser2);
      ep.sendEvent(event2);
      ep.flush();
      Thread.sleep(100);
      
      // Enqueue a third event. The current payload should now be event2 + event3.
      Event.Identify event3 = makeIdentifyEvent(testUser3);
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
