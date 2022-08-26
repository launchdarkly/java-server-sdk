package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;

import org.junit.Test;

import java.net.URI;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * These DefaultEventProcessor tests cover diagnostic event behavior.
 */
@SuppressWarnings("javadoc")
public class DefaultEventProcessorDiagnosticsTest extends EventTestUtil {
  private static LDValue fakePlatformData = LDValue.buildObject().put("cats", 2).build();
  
  private DiagnosticId diagnosticId;
  private DiagnosticStore diagnosticStore;
  
  public DefaultEventProcessorDiagnosticsTest() {
    diagnosticId = new DiagnosticId(SDK_KEY);
    diagnosticStore = new DiagnosticStore(
        new DiagnosticStore.SdkDiagnosticParams(
            SDK_KEY,
            "fake-sdk",
            "1.2.3",
            "fake-platform",
            fakePlatformData,
            null,
            null
            ));
  }
  
  @Test
  public void diagnosticEventsSentToDiagnosticEndpoint() throws Exception {
    MockEventSender es = new MockEventSender();
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).diagnosticStore(diagnosticStore))) {
      MockEventSender.Params initReq = es.awaitRequest();
      ep.postDiagnostic();
      MockEventSender.Params periodicReq = es.awaitRequest();

      assertThat(initReq.diagnostic, is(true));
      assertThat(periodicReq.diagnostic, is(true));
    }
  }

  @Test
  public void initialDiagnosticEventHasInitBody() throws Exception {
    MockEventSender es = new MockEventSender();
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).diagnosticStore(diagnosticStore))) {
      MockEventSender.Params req = es.awaitRequest();

      DiagnosticEvent.Init initEvent = gson.fromJson(req.data, DiagnosticEvent.Init.class);

      assertNotNull(initEvent);
      assertThat(initEvent.kind, equalTo("diagnostic-init"));
      assertThat(initEvent.id, samePropertyValuesAs(diagnosticId));
      assertNotNull(initEvent.configuration);
      assertNotNull(initEvent.sdk);
      assertNotNull(initEvent.platform);
    }
  }

  @Test
  public void periodicDiagnosticEventHasStatisticsBody() throws Exception {
    MockEventSender es = new MockEventSender();
    long dataSinceDate = diagnosticStore.getDataSinceDate();
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).diagnosticStore(diagnosticStore))) {
      // Ignore the initial diagnostic event
      es.awaitRequest();
      ep.postDiagnostic();
      MockEventSender.Params periodicReq = es.awaitRequest();

      assertNotNull(periodicReq);
      DiagnosticEvent.Statistics statsEvent = gson.fromJson(periodicReq.data, DiagnosticEvent.Statistics.class);

      assertNotNull(statsEvent);
      assertThat(statsEvent.kind, equalTo("diagnostic"));
      assertThat(statsEvent.id, samePropertyValuesAs(diagnosticId));
      assertThat(statsEvent.dataSinceDate, equalTo(dataSinceDate));
      assertThat(statsEvent.creationDate, equalTo(diagnosticStore.getDataSinceDate()));
      assertThat(statsEvent.deduplicatedUsers, equalTo(0L));
      assertThat(statsEvent.eventsInLastBatch, equalTo(0L));
      assertThat(statsEvent.droppedEvents, equalTo(0L));
    }
  }

  @Test
  public void periodicDiagnosticEventGetsEventsInLastBatchAndDeduplicatedUsers() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag1 = flagBuilder("flagkey1").version(11).trackEvents(true).build();
    DataModel.FeatureFlag flag2 = flagBuilder("flagkey2").version(22).trackEvents(true).build();
    LDValue value = LDValue.of("value");
    Event.FeatureRequest fe1 = makeFeatureRequestEvent(flag1, user,
            simpleEvaluation(1, value), LDValue.ofNull());
    Event.FeatureRequest fe2 = makeFeatureRequestEvent(flag2, user,
            simpleEvaluation(1, value), LDValue.ofNull());

    // Create a fake deduplicator that just says "not seen" for the first call and "seen" thereafter
    EventContextDeduplicator contextDeduplicator = contextDeduplicatorThatSaysKeyIsNewOnFirstCallOnly();
    
    try (DefaultEventProcessor ep = makeEventProcessor(
        baseConfig(es).contextDeduplicator(contextDeduplicator).diagnosticStore(diagnosticStore))) {
      // Ignore the initial diagnostic event
      es.awaitRequest();

      ep.sendEvent(fe1);
      ep.sendEvent(fe2);
      ep.flush();
      // Ignore normal events
      es.awaitRequest();

      ep.postDiagnostic();
      MockEventSender.Params periodicReq = es.awaitRequest();

      assertNotNull(periodicReq);
      DiagnosticEvent.Statistics statsEvent = gson.fromJson(periodicReq.data, DiagnosticEvent.Statistics.class);

      assertNotNull(statsEvent);
      assertThat(statsEvent.deduplicatedUsers, equalTo(1L));
      assertThat(statsEvent.eventsInLastBatch, equalTo(3L));
      assertThat(statsEvent.droppedEvents, equalTo(0L));
    }
  }

  @Test
  public void periodicDiagnosticEventsAreSentAutomatically() throws Exception {
    MockEventSender es = new MockEventSender();
    
    EventsConfigurationBuilder eventsConfig = makeEventsConfigurationWithBriefDiagnosticInterval(es);
    
    try (DefaultEventProcessor ep = makeEventProcessor(eventsConfig.diagnosticStore(diagnosticStore))) {
      // Ignore the initial diagnostic event
      es.awaitRequest();

      MockEventSender.Params periodicReq = es.awaitRequest();

      assertNotNull(periodicReq);
      DiagnosticEvent.Statistics statsEvent = gson.fromJson(periodicReq.data, DiagnosticEvent.Statistics.class);
      assertEquals("diagnostic", statsEvent.kind);
    }
  }

  private EventsConfigurationBuilder makeEventsConfigurationWithBriefDiagnosticInterval(EventSender es) {
    return baseConfig(es).diagnosticRecordingIntervalMillis(50);
  }

  @Test
  public void diagnosticEventsStopAfter401Error() throws Exception {
    // This is easier to test with a mock component than it would be in LDClientEndToEndTest, because
    // we don't have to worry about the latency of a real HTTP request which could allow the periodic
    // task to fire again before we received a response. In real life, that wouldn't matter because
    // the minimum diagnostic interval is so long, but in a test we need to be able to use a short
    // interval.
    MockEventSender es = new MockEventSender();
    es.result = new EventSender.Result(false, true, null); // mustShutdown=true; this is what would be returned for a 401 error

    EventsConfigurationBuilder eventsConfig = makeEventsConfigurationWithBriefDiagnosticInterval(es);
    
    try (DefaultEventProcessor ep = makeEventProcessor(eventsConfig.diagnosticStore(diagnosticStore))) {
      // Ignore the initial diagnostic event
      es.awaitRequest();

      es.expectNoRequests(100);
    }
  }
  
  @Test
  public void customBaseUriIsPassedToEventSenderForDiagnosticEvents() throws Exception {
    MockEventSender es = new MockEventSender();
    URI uri = URI.create("fake-uri");

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).eventsUri(uri).diagnosticStore(diagnosticStore))) {
    }

    MockEventSender.Params p = es.awaitRequest();
    assertThat(p.eventsBaseUri, equalTo(uri));
  }
}
