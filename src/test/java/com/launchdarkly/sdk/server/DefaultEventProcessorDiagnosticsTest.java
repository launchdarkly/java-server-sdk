package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.Event;
import com.launchdarkly.sdk.server.subsystems.EventSender;

import org.junit.Test;

import java.net.URI;
import java.time.Duration;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * These DefaultEventProcessor tests cover diagnostic event behavior.
 */
@SuppressWarnings("javadoc")
public class DefaultEventProcessorDiagnosticsTest extends DefaultEventProcessorTestBase {
  private DiagnosticEvent.Init configuredInitEvent;
  
  public DefaultEventProcessorDiagnosticsTest() {
    // Currently, the logic for constructing the diagnostic init event is intertwined with various SDK
    // components. This will be more cleanly separated in the future, but for now, to verify the current
    // server-side SDK behavior we will set up some temporary components to create the event.
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
    ClientContext ctx = clientContext(SDK_KEY, new LDConfig.Builder().build(), diagnosticAccumulator);
    configuredInitEvent = ClientContextImpl.get(ctx).diagnosticInitEvent;
  }
  
  @Test
  public void diagnosticEventsSentToDiagnosticEndpoint() throws Exception {
    MockEventSender es = new MockEventSender();
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es), diagnosticAccumulator, configuredInitEvent)) {
      MockEventSender.Params initReq = es.awaitRequest();
      ep.postDiagnostic();
      MockEventSender.Params periodicReq = es.awaitRequest();

      assertThat(initReq.kind, equalTo(EventSender.EventDataKind.DIAGNOSTICS));
      assertThat(periodicReq.kind, equalTo(EventSender.EventDataKind.DIAGNOSTICS));
    }
  }

  @Test
  public void initialDiagnosticEventHasInitBody() throws Exception {
    MockEventSender es = new MockEventSender();
    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es), diagnosticAccumulator, configuredInitEvent)) {
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
    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    long dataSinceDate = diagnosticAccumulator.dataSinceDate;
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es), diagnosticAccumulator, configuredInitEvent)) {
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
      assertThat(statsEvent.creationDate, equalTo(diagnosticAccumulator.dataSinceDate));
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
    Event.FeatureRequest fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
            simpleEvaluation(1, value), LDValue.ofNull());
    Event.FeatureRequest fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
            simpleEvaluation(1, value), LDValue.ofNull());

    // Create a fake deduplicator that just says "not seen" for the first call and "seen" thereafter
    EventContextDeduplicator contextDeduplicator = contextDeduplicatorThatSaysKeyIsNewOnFirstCallOnly();
    
    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    try (DefaultEventProcessor ep = makeEventProcessor(
        baseConfig(es).contextDeduplicator(contextDeduplicator),
        diagnosticAccumulator, configuredInitEvent)) {
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
    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    ClientContext context = clientContext(SDK_KEY, LDConfig.DEFAULT); 
    DiagnosticEvent.Init initEvent = new DiagnosticEvent.Init(0, diagnosticId, LDConfig.DEFAULT, context);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    
    EventsConfigurationBuilder eventsConfig = makeEventsConfigurationWithBriefDiagnosticInterval(es);
    
    try (DefaultEventProcessor ep = makeEventProcessor(eventsConfig, diagnosticAccumulator, initEvent)) {
      // Ignore the initial diagnostic event
      es.awaitRequest();

      MockEventSender.Params periodicReq = es.awaitRequest();

      assertNotNull(periodicReq);
      DiagnosticEvent.Statistics statsEvent = gson.fromJson(periodicReq.data, DiagnosticEvent.Statistics.class);
      assertEquals("diagnostic", statsEvent.kind);
    }
  }

  private EventsConfigurationBuilder makeEventsConfigurationWithBriefDiagnosticInterval(EventSender es) {
    return baseConfig(es).diagnosticRecordingInterval(Duration.ofMillis(50));
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

    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    ClientContext context = clientContext(SDK_KEY, LDConfig.DEFAULT); 
    DiagnosticEvent.Init initEvent = new DiagnosticEvent.Init(0, diagnosticId, LDConfig.DEFAULT, context);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    
    EventsConfigurationBuilder eventsConfig = makeEventsConfigurationWithBriefDiagnosticInterval(es);
    
    try (DefaultEventProcessor ep = makeEventProcessor(eventsConfig, diagnosticAccumulator, initEvent)) {
      // Ignore the initial diagnostic event
      es.awaitRequest();

      es.expectNoRequests(Duration.ofMillis(100));
    }
  }
  
  @Test
  public void customBaseUriIsPassedToEventSenderForDiagnosticEvents() throws Exception {
    MockEventSender es = new MockEventSender();
    URI uri = URI.create("fake-uri");
    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).eventsUri(uri),
        diagnosticAccumulator, configuredInitEvent)) {
    }

    MockEventSender.Params p = es.awaitRequest();
    assertThat(p.eventsBaseUri, equalTo(uri));
  }
}
