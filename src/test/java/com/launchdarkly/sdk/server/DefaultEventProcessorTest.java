package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.EventSender;
import com.launchdarkly.sdk.server.interfaces.EventSenderFactory;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.Components.sendEvents;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestUtil.hasJsonProperty;
import static com.launchdarkly.sdk.server.TestUtil.isJsonArray;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class DefaultEventProcessorTest {
  private static final String SDK_KEY = "SDK_KEY";
  private static final LDUser user = new LDUser.Builder("userkey").name("Red").build();
  private static final Gson gson = new Gson();
  private static final LDValue userJson = LDValue.buildObject().put("key", "userkey").put("name", "Red").build();
  private static final LDValue filteredUserJson = LDValue.buildObject().put("key", "userkey")
      .put("privateAttrs", LDValue.buildArray().add("name").build()).build();
  private static final LDConfig baseLDConfig = new LDConfig.Builder().diagnosticOptOut(true).build();
  private static final LDConfig diagLDConfig = new LDConfig.Builder().diagnosticOptOut(false).build();
  
  // Note that all of these events depend on the fact that DefaultEventProcessor does a synchronous
  // flush when it is closed; in this case, it's closed implicitly by the try-with-resources block.

  private EventProcessorBuilder baseConfig(MockEventSender es) {
    return sendEvents().eventSender(senderFactory(es));
  }

  private DefaultEventProcessor makeEventProcessor(EventProcessorBuilder ec) {
    return makeEventProcessor(ec, baseLDConfig);
  }
  
  private DefaultEventProcessor makeEventProcessor(EventProcessorBuilder ec, LDConfig config) {
    return (DefaultEventProcessor)ec.createEventProcessor(clientContext(SDK_KEY, config));
  }

  private DefaultEventProcessor makeEventProcessor(EventProcessorBuilder ec, DiagnosticAccumulator diagnosticAccumulator) {
    return (DefaultEventProcessor)ec.createEventProcessor(
        clientContext(SDK_KEY, diagLDConfig, diagnosticAccumulator));
  }
  
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
    URI uri = URI.create("http://fake");
    MockEventSender es = new MockEventSender();
    EventProcessorFactory epf = Components.sendEvents()
        .allAttributesPrivate(true)
        .baseURI(uri)
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
      assertThat(ec.eventsUri, equalTo(uri));
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

  @Test
  public void identifyEventIsQueued() throws Exception {
    MockEventSender es = new MockEventSender();
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

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
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).allAttributesPrivate(true))) {
      ep.sendEvent(e);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIdentifyEvent(e, filteredUserJson)
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void individualFeatureEventIsQueuedWithIndexEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, false, null),
        isSummaryEvent()
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInIndexEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).allAttributesPrivate(true))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, filteredUserJson),
        isFeatureEvent(fe, flag, false, null),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanContainInlineUser() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());
    
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).inlineUsersInEvents(true))) {
      ep.sendEvent(fe);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isFeatureEvent(fe, flag, false, userJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInFeatureEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es)
        .inlineUsersInEvents(true).allAttributesPrivate(true))) {
      ep.sendEvent(fe);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isFeatureEvent(fe, flag, false, filteredUserJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanContainReason() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    EvaluationReason reason = EvaluationReason.ruleMatch(1, null);
    Event.FeatureRequest fe = EventFactory.DEFAULT_WITH_REASONS.newFeatureRequestEvent(flag, user,
          new Evaluator.EvalResult(LDValue.of("value"), 1, reason), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, false, null, reason),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void indexEventIsStillGeneratedIfInlineUsersIsTrueButFeatureEventIsNotTracked() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(false).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).inlineUsersInEvents(true))) {
      ep.sendEvent(fe);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventKindIsDebugIfFlagIsTemporarilyInDebugMode() throws Exception {
    MockEventSender es = new MockEventSender();
    long futureTime = System.currentTimeMillis() + 1000000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).debugEventsUntilDate(futureTime).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }
  
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, true, userJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventCanBeBothTrackedAndDebugged() throws Exception {
    MockEventSender es = new MockEventSender();
    long futureTime = System.currentTimeMillis() + 1000000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true)
        .debugEventsUntilDate(futureTime).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe);
    }

    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, false, null),
        isFeatureEvent(fe, flag, true, userJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void debugModeExpiresBasedOnClientTimeIfClientTimeIsLaterThanServerTime() throws Exception {
    MockEventSender es = new MockEventSender();
    
    // Pick a server time that is somewhat behind the client time
    long serverTime = System.currentTimeMillis() - 20000;
    es.result = new EventSender.Result(true, false, new Date(serverTime));
    
    long debugUntil = serverTime + 1000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      // Send and flush an event we don't care about, just so we'll receive "resp1" which sets the last server time
      ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
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
        isIndexEvent(fe, userJson),
        isSummaryEvent(fe.getCreationDate(), fe.getCreationDate())
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void debugModeExpiresBasedOnServerTimeIfServerTimeIsLaterThanClientTime() throws Exception {
    MockEventSender es = new MockEventSender();
    
    // Pick a server time that is somewhat ahead of the client time
    long serverTime = System.currentTimeMillis() + 20000;
    es.result = new EventSender.Result(true, false, new Date(serverTime));
    
    long debugUntil = serverTime - 1000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      // Send and flush an event we don't care about, just to set the last server time
      ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
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
        isIndexEvent(fe, userJson),
        isSummaryEvent(fe.getCreationDate(), fe.getCreationDate())
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void twoFeatureEventsForSameUserGenerateOnlyOneIndexEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag1 = flagBuilder("flagkey1").version(11).trackEvents(true).build();
    DataModel.FeatureFlag flag2 = flagBuilder("flagkey2").version(22).trackEvents(true).build();
    LDValue value = LDValue.of("value");
    Event.FeatureRequest fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value), LDValue.ofNull());
    Event.FeatureRequest fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        simpleEvaluation(1, value), LDValue.ofNull());

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe1);
      ep.sendEvent(fe2);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe1, userJson),
        isFeatureEvent(fe1, flag1, false, null),
        isFeatureEvent(fe2, flag2, false, null),
        isSummaryEvent(fe1.getCreationDate(), fe2.getCreationDate())
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void identifyEventMakesIndexEventUnnecessary() throws Exception {
    MockEventSender es = new MockEventSender();
    Event ie = EventFactory.DEFAULT.newIdentifyEvent(user);
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(ie);
      ep.sendEvent(fe); 
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isIdentifyEvent(ie, userJson),
        isFeatureEvent(fe, flag, false, null),
        isSummaryEvent()
    ));
  }

  
  @SuppressWarnings("unchecked")
  @Test
  public void nonTrackedEventsAreSummarized() throws Exception {
    MockEventSender es = new MockEventSender();
    DataModel.FeatureFlag flag1 = flagBuilder("flagkey1").version(11).build();
    DataModel.FeatureFlag flag2 = flagBuilder("flagkey2").version(22).build();
    LDValue value1 = LDValue.of("value1");
    LDValue value2 = LDValue.of("value2");
    LDValue default1 = LDValue.of("default1");
    LDValue default2 = LDValue.of("default2");
    Event fe1a = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value1), default1);
    Event fe1b = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value1), default1);
    Event fe1c = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(2, value2), default1);
    Event fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        simpleEvaluation(2, value2), default2);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es))) {
      ep.sendEvent(fe1a);
      ep.sendEvent(fe1b);
      ep.sendEvent(fe1c);
      ep.sendEvent(fe2);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(
        isIndexEvent(fe1a, userJson),
        allOf(
            isSummaryEvent(fe1a.getCreationDate(), fe2.getCreationDate()),
            hasSummaryFlag(flag1.getKey(), default1,
                Matchers.containsInAnyOrder(
                    isSummaryEventCounter(flag1, 1, value1, 2),
                    isSummaryEventCounter(flag1, 2, value2, 1)
                )),
            hasSummaryFlag(flag2.getKey(), default2,
                contains(isSummaryEventCounter(flag2, 2, value2, 1)))
        )
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void customEventIsQueuedWithUser() throws Exception {
    MockEventSender es = new MockEventSender();
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
  public void customEventCanContainInlineUser() throws Exception {
    MockEventSender es = new MockEventSender();
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data, null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).inlineUsersInEvents(true))) {
      ep.sendEvent(ce);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(isCustomEvent(ce, userJson)));
  }
  
  @Test
  public void userIsFilteredInCustomEvent() throws Exception {
    MockEventSender es = new MockEventSender();
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data, null);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es)
        .inlineUsersInEvents(true).allAttributesPrivate(true))) {
      ep.sendEvent(ce);
    }
    
    assertThat(es.getEventsFromLastRequest(), contains(isCustomEvent(ce, filteredUserJson)));
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

  @Test
  public void diagnosticEventsSentToDiagnosticEndpoint() throws Exception {
    MockEventSender es = new MockEventSender();
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es), diagnosticAccumulator)) {
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
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es), diagnosticAccumulator)) {
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
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es), diagnosticAccumulator)) {
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

    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es), diagnosticAccumulator)) {
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
  public void customBaseUriIsPassedToEventSenderForDiagnosticEvents() throws Exception {
    MockEventSender es = new MockEventSender();
    URI uri = URI.create("fake-uri");
    DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);

    try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(es).baseURI(uri), diagnosticAccumulator)) {
    }

    MockEventSender.Params p = es.awaitRequest();
    assertThat(p.eventsBaseUri, equalTo(uri));
  }

  private static EventSenderFactory senderFactory(final MockEventSender es) {
    return new EventSenderFactory() {
      @Override
      public EventSender createEventSender(String sdkKey, HttpConfiguration httpConfiguration) {
        return es;
      }
    };
  }
  
  private static final class MockEventSender implements EventSender {
    volatile boolean closed;
    volatile Result result = new Result(true, false, null);
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
    }

    @Override
    public Result sendEventData(EventDataKind kind, String data, int eventCount, URI eventsBaseUri) {
      receivedParams.add(new Params(kind, data, eventCount, eventsBaseUri));
      return result;
    }
    
    Params awaitRequest() throws Exception {
      Params p = receivedParams.poll(5, TimeUnit.SECONDS);
      if (p == null) {
        fail("did not receive event post within 5 seconds");
      }
      return p;
    }
    
    Iterable<LDValue> getEventsFromLastRequest() throws Exception {
      Params p = awaitRequest();
      LDValue a = LDValue.parse(p.data);
      assertEquals(p.eventCount, a.size());
      return a.values();
    }
  }

  private Matcher<LDValue> isIdentifyEvent(Event sourceEvent, LDValue user) {
    return allOf(
        hasJsonProperty("kind", "identify"),
        hasJsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        hasJsonProperty("user", user)
    );
  }

  private Matcher<LDValue> isIndexEvent(Event sourceEvent, LDValue user) {
    return allOf(
        hasJsonProperty("kind", "index"),
        hasJsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        hasJsonProperty("user", user)
    );
  }

  private Matcher<LDValue> isFeatureEvent(Event.FeatureRequest sourceEvent, DataModel.FeatureFlag flag, boolean debug, LDValue inlineUser) {
    return isFeatureEvent(sourceEvent, flag, debug, inlineUser, null);
  }

  @SuppressWarnings("unchecked")
  private Matcher<LDValue> isFeatureEvent(Event.FeatureRequest sourceEvent, DataModel.FeatureFlag flag, boolean debug, LDValue inlineUser,
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

  @SuppressWarnings("unchecked")
  private Matcher<LDValue> isCustomEvent(Event.Custom sourceEvent, LDValue inlineUser) {
    return allOf(
        hasJsonProperty("kind", "custom"),
        hasJsonProperty("creationDate", (double)sourceEvent.getCreationDate()),
        hasJsonProperty("key", "eventkey"),
        hasJsonProperty("userKey", inlineUser == null ? LDValue.of(sourceEvent.getUser().getKey()) : LDValue.ofNull()),
        hasJsonProperty("user", inlineUser == null ? LDValue.ofNull() : inlineUser),
        hasJsonProperty("data", sourceEvent.getData()),
        hasJsonProperty("metricValue", sourceEvent.getMetricValue() == null ? LDValue.ofNull() : LDValue.of(sourceEvent.getMetricValue()))              
    );
  }

  private Matcher<LDValue> isSummaryEvent() {
    return hasJsonProperty("kind", "summary");
  }

  private Matcher<LDValue> isSummaryEvent(long startDate, long endDate) {
    return allOf(
        hasJsonProperty("kind", "summary"),
        hasJsonProperty("startDate", (double)startDate),
        hasJsonProperty("endDate", (double)endDate)
    );
  }
  
  private Matcher<LDValue> hasSummaryFlag(String key, LDValue defaultVal, Matcher<Iterable<? extends LDValue>> counters) {
    return hasJsonProperty("features",
        hasJsonProperty(key, allOf(
          hasJsonProperty("default", defaultVal),
          hasJsonProperty("counters", isJsonArray(counters))
    )));
  }
  
  private Matcher<LDValue> isSummaryEventCounter(DataModel.FeatureFlag flag, Integer variation, LDValue value, int count) {
    return allOf(
        hasJsonProperty("variation", variation),
        hasJsonProperty("version", (double)flag.getVersion()),
        hasJsonProperty("value", value),
        hasJsonProperty("count", (double)count)
    );
  }
}
