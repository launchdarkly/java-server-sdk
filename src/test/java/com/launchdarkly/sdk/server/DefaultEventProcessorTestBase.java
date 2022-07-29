package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.Event;
import com.launchdarkly.sdk.server.subsystems.EventSender;
import com.launchdarkly.sdk.server.subsystems.EventSenderFactory;
import com.launchdarkly.testhelpers.JsonTestValue;

import org.hamcrest.Matcher;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
  public static final LDContext user = LDContext.builder("userkey").name("Red").build();
  public static final Gson gson = new Gson();
  public static final LDValue userJson = LDValue.buildObject().put("kind", "user")
      .put("key", "userkey").put("name", "Red").build();
  public static final LDValue filteredUserJson = LDValue.buildObject().put("kind", "user")
      .put("key", "userkey").put("_meta", LDValue.parse("{\"redactedAttributes\":[\"name\"]}")).build();
  
  // Note that all of these events depend on the fact that DefaultEventProcessor does a synchronous
  // flush when it is closed; in this case, it's closed implicitly by the try-with-resources block.

  public static EventsConfigurationBuilder baseConfig(EventSender es) {
    return new EventsConfigurationBuilder().eventSender(es);
  }

  public DefaultEventProcessor makeEventProcessor(EventsConfigurationBuilder ec) {
    return makeEventProcessor(ec, null);
  }

  public DefaultEventProcessor makeEventProcessor(
      EventsConfigurationBuilder ec,
      DiagnosticStore diagnosticStore
      ) {
    return new DefaultEventProcessor(
        ec.build(),
        TestComponents.sharedExecutor,
        Thread.MAX_PRIORITY,
        diagnosticStore,
        testLogger
        );
  }

  public static EventSenderFactory senderFactory(final EventSender es) {
    return new EventSenderFactory() {
      @Override
      public EventSender createEventSender(ClientContext clientContext) {
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

  public static Matcher<JsonTestValue> isIdentifyEvent(Event sourceEvent, LDValue context) {
    return allOf(
        jsonProperty("kind", "identify"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("context", jsonFromValue(context))
    );
  }

  public static Matcher<JsonTestValue> isIndexEvent() {
    return jsonProperty("kind", "index");
  }

  public static Matcher<JsonTestValue> isIndexEvent(Event sourceEvent, LDValue context) {
    return allOf(
        jsonProperty("kind", "index"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("context", jsonFromValue(context))
    );
  }

  public static Matcher<JsonTestValue> isFeatureEvent(Event.FeatureRequest sourceEvent, DataModel.FeatureFlag flag, boolean debug, LDValue inlineUser) {
    return isFeatureEvent(sourceEvent, flag, debug, inlineUser, null);
  }

  @SuppressWarnings("unchecked")
  public static Matcher<JsonTestValue> isFeatureEvent(Event.FeatureRequest sourceEvent, DataModel.FeatureFlag flag,
      boolean debug, LDValue inlineContext, EvaluationReason reason) {
    return allOf(
        jsonProperty("kind", debug ? "debug" : "feature"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("key", flag.getKey()),
        jsonProperty("version", (double)flag.getVersion()),
        jsonProperty("variation", sourceEvent.getVariation()),
        jsonProperty("value", jsonFromValue(sourceEvent.getValue())),
        inlineContext == null ? hasContextKeys(sourceEvent) : hasInlineContext(inlineContext),
        jsonProperty("reason", reason == null ? jsonUndefined() : jsonEqualsValue(reason))
    );
  }

  public static Matcher<JsonTestValue> isPrerequisiteOf(String parentFlagKey) {
    return jsonProperty("prereqOf", parentFlagKey);
  }

  public static Matcher<JsonTestValue> isCustomEvent(Event.Custom sourceEvent) {
    boolean hasData = sourceEvent.getData() != null && !sourceEvent.getData().isNull();
    return allOf(
        jsonProperty("kind", "custom"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("key", sourceEvent.getKey()),
        hasContextKeys(sourceEvent),
        jsonProperty("data", hasData ? jsonEqualsValue(sourceEvent.getData()) : jsonUndefined()),
        jsonProperty("metricValue", sourceEvent.getMetricValue() == null ? jsonUndefined() : jsonEqualsValue(sourceEvent.getMetricValue()))              
    );
  }

  public static Matcher<JsonTestValue> hasContextKeys(Event sourceEvent) {
    ObjectBuilder b = LDValue.buildObject();
    LDContext c = sourceEvent.getContext();
    for (int i = 0; i < c.getIndividualContextCount(); i++) {
      LDContext c1 = c.getIndividualContext(i);
      b.put(c1.getKind().toString(), c1.getKey());
    }
    return jsonProperty("contextKeys", jsonEqualsValue(b.build()));
  }
  
  public static Matcher<JsonTestValue> hasInlineContext(LDValue inlineContext) {
    return allOf(
        jsonProperty("context", jsonEqualsValue(inlineContext)),
        jsonProperty("contextKeys", jsonUndefined())
        );
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
  
  /**
   * This builder is similar to the public SDK configuration builder for events, except it is building
   * the internal config object for the lower-level event processing code. This allows us to test that
   * code independently of the rest of the SDK. Note that the default values here are deliberately not
   * the same as the defaults in the SDK; they are chosen to make it unlikely for tests to be affected
   * by any behavior we're not specifically trying to test-- for instance, a long flush interval means
   * that flushes normally won't happen, and any test where we want flushes to happen will not rely on
   * the defaults.    
   */
  public static class EventsConfigurationBuilder {
    private boolean allAttributesPrivate = false;
    private int capacity = 1000;
    private EventContextDeduplicator contextDeduplicator = null;
    private Duration diagnosticRecordingInterval = Duration.ofDays(1);
    private URI eventsUri = URI.create("not-valid");
    private Duration flushInterval = Duration.ofDays(1);
    private Set<AttributeRef> privateAttributes = new HashSet<>();
    private EventSender eventSender = null;

    public EventsConfiguration build() {
      return new EventsConfiguration(
          allAttributesPrivate,
          capacity,
          contextDeduplicator,
          eventSender,
          eventsUri,
          flushInterval,
          privateAttributes,
          diagnosticRecordingInterval
          );
    }
    
    public EventsConfigurationBuilder allAttributesPrivate(boolean allAttributesPrivate) {
      this.allAttributesPrivate = allAttributesPrivate;
      return this;
    }
    
    public EventsConfigurationBuilder capacity(int capacity) {
      this.capacity = capacity;
      return this;
    }
    
    public EventsConfigurationBuilder contextDeduplicator(EventContextDeduplicator contextDeduplicator) {
      this.contextDeduplicator = contextDeduplicator;
      return this;
    }
    
    public EventsConfigurationBuilder diagnosticRecordingInterval(Duration diagnosticRecordingInterval) {
      this.diagnosticRecordingInterval = diagnosticRecordingInterval;
      return this;
    }
    
    public EventsConfigurationBuilder eventsUri(URI eventsUri) {
      this.eventsUri = eventsUri;
      return this;
    }
    
    public EventsConfigurationBuilder flushInterval(Duration flushInterval) {
      this.flushInterval = flushInterval;
      return this;
    }
    
    public EventsConfigurationBuilder privateAttributes(Set<AttributeRef> privateAttributes) {
      this.privateAttributes = privateAttributes;
      return this;
    }
    
    public EventsConfigurationBuilder eventSender(EventSender eventSender) {
      this.eventSender = eventSender;
      return this;
    }
  }
  
  public static EventContextDeduplicator contextDeduplicatorThatAlwaysSaysKeysAreNew() {
    return new EventContextDeduplicator() {
      @Override
      public Long getFlushInterval() {
        return null;
      }

      @Override
      public boolean processContext(LDContext context) {
        return true;
      }

      @Override
      public void flush() {}
    };
  }
  
  public static EventContextDeduplicator contextDeduplicatorThatSaysKeyIsNewOnFirstCallOnly() {
    return new EventContextDeduplicator() {
      private int calls = 0;
      
      @Override
      public Long getFlushInterval() {
        return null;
      }

      @Override
      public boolean processContext(LDContext context) {
        ++calls;
        return calls == 1;
      }

      @Override
      public void flush() {}
    };
  
  }
}
