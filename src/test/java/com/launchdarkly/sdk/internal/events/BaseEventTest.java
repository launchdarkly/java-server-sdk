package com.launchdarkly.sdk.internal.events;

import com.google.gson.Gson;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.internal.BaseInternalTest;
import com.launchdarkly.sdk.internal.http.HttpProperties;
import com.launchdarkly.sdk.server.TestComponents;
import com.launchdarkly.testhelpers.JsonTestValue;

import org.hamcrest.Matcher;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
public abstract class BaseEventTest extends BaseInternalTest {
  public static final String SDK_KEY = "SDK_KEY";
  public static final long FAKE_TIME = 100000;
  public static final String FLAG_KEY = "flagkey";
  public static final int FLAG_VERSION = 11;
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

  public static HttpProperties defaultHttpProperties() {
    return new HttpProperties(0, null, null, null, null, 0, null, null);
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
        testLogger
        );
  }

  public static EventsConfiguration defaultEventsConfig() {
    return makeEventsConfig(false, null);
  }

  public static EventsConfiguration makeEventsConfig(boolean allAttributesPrivate,
      Collection<AttributeRef> privateAttributes) {
    return new EventsConfiguration(
        allAttributesPrivate,
        0,
        null,
        100000, // arbitrary long flush interval
        null,
        null,
        null,
        100000, // arbitrary long flush interval
        privateAttributes
        );
  }
  
  public static EvaluationDetail<LDValue> simpleEvaluation(int variation, LDValue value) {
    return EvaluationDetail.fromValue(value, variation, EvaluationReason.off());
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
      final boolean diagnostic;
      final String data;
      final int eventCount;
      final URI eventsBaseUri;
      
      Params(boolean diagnostic, String data, int eventCount, URI eventsBaseUri) {
        this.diagnostic = diagnostic;
        this.data = data;
        this.eventCount = eventCount;
        assertNotNull(eventsBaseUri);
        this.eventsBaseUri = eventsBaseUri;
      }
    }

    @Override
    public Result sendAnalyticsEvents(byte[] data, int eventCount, URI eventsBaseUri) {
      return receive(false, data, eventCount, eventsBaseUri);
    }

    @Override
    public Result sendDiagnosticEvent(byte[] data, URI eventsBaseUri) {
      return receive(true, data, 1, eventsBaseUri);
    }
    
    @Override
    public void close() throws IOException {
      closed = true;
      if (fakeErrorOnClose != null) {
        throw fakeErrorOnClose;
      }
    }

    private Result receive(boolean diagnostic, byte[] data, int eventCount, URI eventsBaseUri) {
      receivedParams.add(new Params(diagnostic, new String(data, Charset.forName("UTF-8")), eventCount, eventsBaseUri));
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
    
    void expectNoRequests(long timeoutMillis) {
      assertNoMoreValues(receivedParams, timeoutMillis, TimeUnit.MILLISECONDS);
    }
    
    List<JsonTestValue> getEventsFromLastRequest() {
      Params p = awaitRequest();
      LDValue a = LDValue.parse(p.data);
      assertEquals(p.eventCount, a.size());
      List<JsonTestValue> ret = new ArrayList<>();
      for (LDValue v: a.values()) {
        ret.add(jsonFromValue(v));
      }
      return ret;
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

  public static Matcher<JsonTestValue> isFeatureEvent(Event.FeatureRequest sourceEvent) {
    return isFeatureOrDebugEvent(sourceEvent, null, false);
  }

  public static Matcher<JsonTestValue> isDebugEvent(Event.FeatureRequest sourceEvent, LDValue inlineContext) {
    return isFeatureOrDebugEvent(sourceEvent, inlineContext, true);
  }

  @SuppressWarnings("unchecked")
  private static Matcher<JsonTestValue> isFeatureOrDebugEvent(Event.FeatureRequest sourceEvent,
      LDValue inlineContext, boolean debug) {
    return allOf(
        jsonProperty("kind", debug ? "debug" : "feature"),
        jsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        jsonProperty("key", sourceEvent.getKey()),
        jsonProperty("version", sourceEvent.getVersion()),
        jsonProperty("variation", sourceEvent.getVariation()),
        jsonProperty("value", jsonFromValue(sourceEvent.getValue())),
        inlineContext == null ? hasContextKeys(sourceEvent) : hasInlineContext(inlineContext),
        jsonProperty("reason", sourceEvent.getReason() == null ? jsonUndefined() : jsonEqualsValue(sourceEvent.getReason())),
        jsonProperty("prereqOf", sourceEvent.getPrereqOf() == null ? jsonUndefined() : jsonEqualsValue(sourceEvent.getPrereqOf()))
    );
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
  
  public static Matcher<JsonTestValue> isSummaryEventCounter(int flagVersion, Integer variation, LDValue value, int count) {
    return allOf(
        jsonProperty("variation", variation),
        jsonProperty("version", (double)flagVersion),
        jsonProperty("value", jsonFromValue(value)),
        jsonProperty("count", (double)count)
    );
  }

  public static FeatureRequestEventBuilder featureEvent(LDContext context, String flagKey) {
    return new FeatureRequestEventBuilder(context, flagKey);
  }

  public static CustomEventBuilder customEvent(LDContext context, String flagKey) {
    return new CustomEventBuilder(context, flagKey);
  }
  
  public static Event.Identify identifyEvent(LDContext context) {
    return new Event.Identify(FAKE_TIME, context);
  }
  
  /**
   * This builder is similar to the public SDK configuration builder for events, except it is building
   * the internal config object for the lower-level event processing code. This allows us to test that
   * code independently of the rest of the SDK. Note that the default values here are deliberately not
   * the same as the defaults in the SDK; they are chosen to make it unlikely for tests to be affected
   * by any behavior we're not specifically trying to test-- for instance, a long flush interval means
   * that flushes normally won't happen, and any test where we want flushes to happen will not rely on
   * the defaults.    
   * <p>
   * This is defined only in test code, instead of as an inner class of EventsConfiguration, because
   * in non-test code there's only one place where we ever construct EventsConfiguration. 
   */
  public static class EventsConfigurationBuilder {
    private boolean allAttributesPrivate = false;
    private int capacity = 1000;
    private EventContextDeduplicator contextDeduplicator = null;
    private long diagnosticRecordingIntervalMillis = 1000000;
    private DiagnosticStore diagnosticStore = null;
    private URI eventsUri = URI.create("not-valid");
    private long flushIntervalMillis = 1000000;
    private Set<AttributeRef> privateAttributes = new HashSet<>();
    private EventSender eventSender = null;

    public EventsConfiguration build() {
      return new EventsConfiguration(
          allAttributesPrivate,
          capacity,
          contextDeduplicator,
          diagnosticRecordingIntervalMillis,
          diagnosticStore,
          eventSender,
          eventsUri,
          flushIntervalMillis,
          privateAttributes
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
    
    public EventsConfigurationBuilder diagnosticRecordingIntervalMillis(long diagnosticRecordingIntervalMillis) {
      this.diagnosticRecordingIntervalMillis = diagnosticRecordingIntervalMillis;
      return this;
    }
    
    public EventsConfigurationBuilder diagnosticStore(DiagnosticStore diagnosticStore) {
      this.diagnosticStore = diagnosticStore;
      return this;
    }
    
    public EventsConfigurationBuilder eventsUri(URI eventsUri) {
      this.eventsUri = eventsUri;
      return this;
    }
    
    public EventsConfigurationBuilder flushIntervalMillis(long flushIntervalMillis) {
      this.flushIntervalMillis = flushIntervalMillis;
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
  
  public static final class FeatureRequestEventBuilder {
    private long timestamp = FAKE_TIME;
    private LDContext context;
    private String flagKey;
    private int flagVersion = 100;
    private int variation = 1;
    private LDValue value = LDValue.of("value");
    private EvaluationReason reason = null;
    private LDValue defaultValue = LDValue.of("default");
    private String prereqOf = null;
    private boolean trackEvents = false;
    private Long debugEventsUntilDate = null;
    
    public FeatureRequestEventBuilder(LDContext context, String flagKey) {
      this.context = context;
      this.flagKey = flagKey;
    }
    
    public Event.FeatureRequest build() {
      return new Event.FeatureRequest(timestamp, flagKey, context, flagVersion, variation, value,
          defaultValue, reason, prereqOf, trackEvents, debugEventsUntilDate, false);
    }
    
    public FeatureRequestEventBuilder flagVersion(int flagVersion) {
      this.flagVersion = flagVersion;
      return this;
    }
    
    public FeatureRequestEventBuilder variation(int variation) {
      this.variation = variation;
      return this;
    }
    
    public FeatureRequestEventBuilder value(LDValue value) {
      this.value = value;
      return this;
    }
    
    public FeatureRequestEventBuilder defaultValue(LDValue defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }
    
    public FeatureRequestEventBuilder reason(EvaluationReason reason) {
      this.reason = reason;
      return this;
    }
    
    public FeatureRequestEventBuilder prereqOf(String prereqOf) {
      this.prereqOf = prereqOf;
      return this;
    }

    public FeatureRequestEventBuilder trackEvents(boolean trackEvents) {
      this.trackEvents = trackEvents;
      return this;
    }

    public FeatureRequestEventBuilder debugEventsUntilDate(Long debugEventsUntilDate) {
      this.debugEventsUntilDate = debugEventsUntilDate;
      return this;
    }
  }
  
  public static final class CustomEventBuilder {
    private long timestamp = FAKE_TIME;
    private LDContext context;
    private String eventKey;
    private LDValue data = LDValue.ofNull();
    private Double metricValue = null;
    
    public CustomEventBuilder(LDContext context, String eventKey) {
      this.context = context;
      this.eventKey = eventKey;
    }
    
    public Event.Custom build() {
      return new Event.Custom(timestamp, eventKey, context, data, metricValue);
    }
    
    public CustomEventBuilder data(LDValue data) {
      this.data = data;
      return this;
    }
    
    public CustomEventBuilder metricValue(Double metricValue) {
      this.metricValue = metricValue;
      return this;
    }
  }
}
