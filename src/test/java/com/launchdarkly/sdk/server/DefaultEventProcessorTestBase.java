package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventSender;
import com.launchdarkly.sdk.server.interfaces.EventSenderFactory;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.hamcrest.Matcher;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.Components.sendEvents;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestUtil.hasJsonProperty;
import static com.launchdarkly.sdk.server.TestUtil.isJsonArray;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public abstract class DefaultEventProcessorTestBase {
  public static final String SDK_KEY = "SDK_KEY";
  public static final URI FAKE_URI = URI.create("http://fake");
  public static final LDUser user = new LDUser.Builder("userkey").name("Red").build();
  public static final Gson gson = new Gson();
  public static final LDValue userJson = LDValue.buildObject().put("key", "userkey").put("name", "Red").build();
  public static final LDValue filteredUserJson = LDValue.buildObject().put("key", "userkey")
      .put("privateAttrs", LDValue.buildArray().add("name").build()).build();
  public static final LDConfig baseLDConfig = new LDConfig.Builder().diagnosticOptOut(true).build();
  public static final LDConfig diagLDConfig = new LDConfig.Builder().diagnosticOptOut(false).build();
  
  // Note that all of these events depend on the fact that DefaultEventProcessor does a synchronous
  // flush when it is closed; in this case, it's closed implicitly by the try-with-resources block.

  public static EventProcessorBuilder baseConfig(MockEventSender es) {
    return sendEvents().eventSender(senderFactory(es));
  }

  public static DefaultEventProcessor makeEventProcessor(EventProcessorBuilder ec) {
    return makeEventProcessor(ec, baseLDConfig);
  }
  
  public static DefaultEventProcessor makeEventProcessor(EventProcessorBuilder ec, LDConfig config) {
    return (DefaultEventProcessor)ec.createEventProcessor(clientContext(SDK_KEY, config));
  }

  public static DefaultEventProcessor makeEventProcessor(EventProcessorBuilder ec, DiagnosticAccumulator diagnosticAccumulator) {
    return (DefaultEventProcessor)ec.createEventProcessor(
        clientContext(SDK_KEY, diagLDConfig, diagnosticAccumulator));
  }
  
  public static EventSenderFactory senderFactory(final MockEventSender es) {
    return new EventSenderFactory() {
      @Override
      public EventSender createEventSender(String sdkKey, HttpConfiguration httpConfiguration) {
        return es;
      }
    };
  }
  
  public static final class MockEventSender implements EventSender {
    volatile boolean closed;
    volatile Result result = new Result(true, false, null);
    volatile RuntimeException fakeError = null;
    volatile IOException fakeErrorOnClose = null;
    volatile CountDownLatch receivedCounter = null;
    volatile Object waitSignal = null;
    
    final BlockingQueue<Params> receivedParams = new LinkedBlockingQueue<>();
    
    static final class Params {
      final EventDataKind kind;
      final String data;
      final int eventCount;
      final URI eventsBaseUri;
      
      Params(EventDataKind kind, String data, int eventCount, URI eventsBaseUri) {
        this.kind = kind;
        this.data = data;
        this.eventCount = eventCount;
        assertNotNull(eventsBaseUri);
        this.eventsBaseUri = eventsBaseUri;
      }
    }
    
    @Override
    public void close() throws IOException {
      closed = true;
      if (fakeErrorOnClose != null) {
        throw fakeErrorOnClose;
      }
    }

    @Override
    public Result sendEventData(EventDataKind kind, String data, int eventCount, URI eventsBaseUri) {
      receivedParams.add(new Params(kind, data, eventCount, eventsBaseUri));
      if (waitSignal != null) {
        // this is used in DefaultEventProcessorTest.eventsAreKeptInBufferIfAllFlushWorkersAreBusy 
        synchronized (waitSignal) {
          if (receivedCounter != null) {
            receivedCounter.countDown();
          }
          try {
            waitSignal.wait();
          } catch (InterruptedException e) {}
        }
      }
      if (fakeError != null) {
        throw fakeError;
      }
      return result;
    }
    
    Params awaitRequest() throws InterruptedException {
      Params p = receivedParams.poll(5, TimeUnit.SECONDS);
      if (p == null) {
        fail("did not receive event post within 5 seconds");
      }
      return p;
    }
    
    void expectNoRequests(Duration timeout) throws InterruptedException {
      Params p = receivedParams.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (p != null) {
        fail("received unexpected event payload");
      }
    }
    
    Iterable<LDValue> getEventsFromLastRequest() throws InterruptedException {
      Params p = awaitRequest();
      LDValue a = LDValue.parse(p.data);
      assertEquals(p.eventCount, a.size());
      return a.values();
    }
  }

  public static Matcher<LDValue> isIdentifyEvent(Event sourceEvent, LDValue user) {
    return allOf(
        hasJsonProperty("kind", "identify"),
        hasJsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        hasJsonProperty("user", user)
    );
  }

  public static Matcher<LDValue> isIndexEvent(Event sourceEvent, LDValue user) {
    return allOf(
        hasJsonProperty("kind", "index"),
        hasJsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        hasJsonProperty("user", user)
    );
  }

  public static Matcher<LDValue> isFeatureEvent(Event.FeatureRequest sourceEvent, DataModel.FeatureFlag flag, boolean debug, LDValue inlineUser) {
    return isFeatureEvent(sourceEvent, flag, debug, inlineUser, null);
  }

  @SuppressWarnings("unchecked")
  public static Matcher<LDValue> isFeatureEvent(Event.FeatureRequest sourceEvent, DataModel.FeatureFlag flag, boolean debug, LDValue inlineUser,
      EvaluationReason reason) {
    return allOf(
        hasJsonProperty("kind", debug ? "debug" : "feature"),
        hasJsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        hasJsonProperty("key", flag.getKey()),
        hasJsonProperty("version", (double)flag.getVersion()),
        hasJsonProperty("variation", sourceEvent.getVariation()),
        hasJsonProperty("value", sourceEvent.getValue()),
        hasJsonProperty("userKey", inlineUser == null ? LDValue.of(sourceEvent.getUser().getKey()) : LDValue.ofNull()),
        hasJsonProperty("user", inlineUser == null ? LDValue.ofNull() : inlineUser),
        hasJsonProperty("reason", reason == null ? LDValue.ofNull() : LDValue.parse(gson.toJson(reason)))
    );
  }

  public static Matcher<LDValue> isPrerequisiteOf(String parentFlagKey) {
    return hasJsonProperty("prereqOf", parentFlagKey);
  }

  @SuppressWarnings("unchecked")
  public static Matcher<LDValue> isCustomEvent(Event.Custom sourceEvent, LDValue inlineUser) {
    return allOf(
        hasJsonProperty("kind", "custom"),
        hasJsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        hasJsonProperty("key", sourceEvent.getKey()),
        hasJsonProperty("userKey", inlineUser == null ? LDValue.of(sourceEvent.getUser().getKey()) : LDValue.ofNull()),
        hasJsonProperty("user", inlineUser == null ? LDValue.ofNull() : inlineUser),
        hasJsonProperty("data", sourceEvent.getData()),
        hasJsonProperty("metricValue", sourceEvent.getMetricValue() == null ? LDValue.ofNull() : LDValue.of(sourceEvent.getMetricValue()))              
    );
  }

  public static Matcher<LDValue> isSummaryEvent() {
    return hasJsonProperty("kind", "summary");
  }

  public static Matcher<LDValue> isSummaryEvent(long startDate, long endDate) {
    return allOf(
        hasJsonProperty("kind", "summary"),
        hasJsonProperty("startDate", (double)startDate),
        hasJsonProperty("endDate", (double)endDate)
    );
  }
  
  public static Matcher<LDValue> hasSummaryFlag(String key, LDValue defaultVal, Matcher<Iterable<? extends LDValue>> counters) {
    return hasJsonProperty("features",
        hasJsonProperty(key, allOf(
          hasJsonProperty("default", defaultVal),
          hasJsonProperty("counters", isJsonArray(counters))
    )));
  }
  
  public static Matcher<LDValue> isSummaryEventCounter(DataModel.FeatureFlag flag, Integer variation, LDValue value, int count) {
    return allOf(
        hasJsonProperty("variation", variation),
        hasJsonProperty("version", (double)flag.getVersion()),
        hasJsonProperty("value", value),
        hasJsonProperty("count", (double)count)
    );
  }
}
