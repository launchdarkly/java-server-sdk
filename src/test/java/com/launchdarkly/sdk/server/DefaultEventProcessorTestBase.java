package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.interfaces.BasicConfiguration;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventSender;
import com.launchdarkly.sdk.server.interfaces.EventSenderFactory;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;
import com.launchdarkly.testhelpers.JsonTestValue;

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
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertNoMoreValues;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.awaitValue;
import static com.launchdarkly.testhelpers.JsonAssertions.isJsonArray;
import static com.launchdarkly.testhelpers.JsonAssertions.jsonEqualsValue;
import static com.launchdarkly.testhelpers.JsonAssertions.jsonProperty;
import static com.launchdarkly.testhelpers.JsonAssertions.jsonUndefined;
import static com.launchdarkly.testhelpers.JsonTestValue.jsonFromValue;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("javadoc")
public abstract class DefaultEventProcessorTestBase extends BaseTest {
  public static final String SDK_KEY = "SDK_KEY";
  public static final URI FAKE_URI = URI.create("http://fake");
  public static final LDUser user = new LDUser.Builder("userkey").name("Red").build();
  public static final Gson gson = new Gson();
  public static final LDValue userJson = LDValue.buildObject().put("key", "userkey").put("name", "Red").build();
  public static final LDValue filteredUserJson = LDValue.buildObject().put("key", "userkey")
      .put("privateAttrs", LDValue.buildArray().add("name").build()).build();
  
  // Note that all of these events depend on the fact that DefaultEventProcessor does a synchronous
  // flush when it is closed; in this case, it's closed implicitly by the try-with-resources block.

  public static EventProcessorBuilder baseConfig(MockEventSender es) {
    return sendEvents().eventSender(senderFactory(es));
  }

  public DefaultEventProcessor makeEventProcessor(EventProcessorBuilder ec) {
    LDConfig config = new LDConfig.Builder().diagnosticOptOut(true)
        .logging(Components.logging(testLogging)).build();
    return makeEventProcessor(ec, config);
  }
  
  public DefaultEventProcessor makeEventProcessor(EventProcessorBuilder ec, LDConfig config) {
    return (DefaultEventProcessor)ec.createEventProcessor(clientContext(SDK_KEY, config));
  }

  public DefaultEventProcessor makeEventProcessor(EventProcessorBuilder ec, DiagnosticAccumulator diagnosticAccumulator) {
    LDConfig config = new LDConfig.Builder().diagnosticOptOut(false)
        .logging(Components.logging(testLogging)).build();
    return (DefaultEventProcessor)ec.createEventProcessor(
        clientContext(SDK_KEY, config, diagnosticAccumulator));
  }
  
  public static EventSenderFactory senderFactory(final MockEventSender es) {
    return new EventSenderFactory() {
      @Override
      public EventSender createEventSender(BasicConfiguration basicConfiguration, HttpConfiguration httpConfiguration) {
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
    
    Params awaitRequest() {
      return awaitValue(receivedParams, 5, TimeUnit.SECONDS);
    }
    
    void expectNoRequests(Duration timeout) {
      assertNoMoreValues(receivedParams, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    Iterable<JsonTestValue> getEventsFromLastRequest() {
      Params p = awaitRequest();
      LDValue a = LDValue.parse(p.data);
      assertEquals(p.eventCount, a.size());
      ImmutableList.Builder<JsonTestValue> ret = ImmutableList.builder();
      for (LDValue v: a.values()) {
        ret.add(jsonFromValue(v));
      }
      return ret.build();
    }
  }

  public static Matcher<JsonTestValue> isIdentifyEvent(Event sourceEvent, LDValue user) {
    return allOf(
        jsonProperty("kind", "identify"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("user", (user == null || user.isNull()) ? jsonUndefined() : jsonEqualsValue(user))
    );
  }

  public static Matcher<JsonTestValue> isIndexEvent(Event sourceEvent, LDValue user) {
    return allOf(
        jsonProperty("kind", "index"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("user", jsonFromValue(user))
    );
  }

  public static Matcher<JsonTestValue> isFeatureEvent(Event.FeatureRequest sourceEvent, DataModel.FeatureFlag flag, boolean debug, LDValue inlineUser) {
    return isFeatureEvent(sourceEvent, flag, debug, inlineUser, null);
  }

  @SuppressWarnings("unchecked")
  public static Matcher<JsonTestValue> isFeatureEvent(Event.FeatureRequest sourceEvent, DataModel.FeatureFlag flag, boolean debug, LDValue inlineUser,
      EvaluationReason reason) {
    return allOf(
        jsonProperty("kind", debug ? "debug" : "feature"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("key", flag.getKey()),
        jsonProperty("version", (double)flag.getVersion()),
        jsonProperty("variation", sourceEvent.getVariation()),
        jsonProperty("value", jsonFromValue(sourceEvent.getValue())),
        hasUserOrUserKey(sourceEvent, inlineUser),
        jsonProperty("reason", reason == null ? jsonUndefined() : jsonEqualsValue(reason))
    );
  }

  public static Matcher<JsonTestValue> isPrerequisiteOf(String parentFlagKey) {
    return jsonProperty("prereqOf", parentFlagKey);
  }

  public static Matcher<JsonTestValue> isCustomEvent(Event.Custom sourceEvent, LDValue inlineUser) {
    boolean hasData = sourceEvent.getData() != null && !sourceEvent.getData().isNull();
    return allOf(
        jsonProperty("kind", "custom"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("key", sourceEvent.getKey()),
        hasUserOrUserKey(sourceEvent, inlineUser),
        jsonProperty("data", hasData ? jsonEqualsValue(sourceEvent.getData()) : jsonUndefined()),
        jsonProperty("metricValue", sourceEvent.getMetricValue() == null ? jsonUndefined() : jsonEqualsValue(sourceEvent.getMetricValue()))              
    );
  }

  public static Matcher<JsonTestValue> isAliasEvent(Event.AliasEvent sourceEvent) {
    return allOf(
        jsonProperty("kind", "alias"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("key", sourceEvent.getKey()),
        jsonProperty("previousKey", sourceEvent.getPreviousKey()),
        jsonProperty("contextKind", sourceEvent.getContextKind()),
        jsonProperty("previousContextKind", sourceEvent.getPreviousContextKind())
    );
  }

  public static Matcher<JsonTestValue> hasUserOrUserKey(Event sourceEvent, LDValue inlineUser) {
    if (inlineUser != null && !inlineUser.isNull()) {
      return allOf(
          jsonProperty("user", jsonEqualsValue(inlineUser)),
          jsonProperty("userKey", jsonUndefined()));
    }
    return allOf(
        jsonProperty("user", jsonUndefined()),
        jsonProperty("userKey", sourceEvent.getUser() == null ? jsonUndefined() :
          jsonEqualsValue(sourceEvent.getUser().getKey())));
  }
  
  public static Matcher<JsonTestValue> isSummaryEvent() {
    return jsonProperty("kind", "summary");
  }

  public static Matcher<JsonTestValue> isSummaryEvent(long startDate, long endDate) {
    return allOf(
        jsonProperty("kind", "summary"),
        jsonProperty("startDate", (double)startDate),
        jsonProperty("endDate", (double)endDate)
    );
  }
  
  public static Matcher<JsonTestValue> hasSummaryFlag(String key, LDValue defaultVal, Matcher<Iterable<? extends JsonTestValue>> counters) {
    return jsonProperty("features",
        jsonProperty(key, allOf(
          jsonProperty("default", jsonFromValue(defaultVal)),
          jsonProperty("counters", isJsonArray(counters))
    )));
  }
  
  public static Matcher<JsonTestValue> isSummaryEventCounter(DataModel.FeatureFlag flag, Integer variation, LDValue value, int count) {
    return allOf(
        jsonProperty("variation", variation),
        jsonProperty("version", (double)flag.getVersion()),
        jsonProperty("value", jsonFromValue(value)),
        jsonProperty("count", (double)count)
    );
  }
}
