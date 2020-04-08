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

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.Components.sendEvents;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestHttpUtil.httpsServerWithSelfSignedCert;
import static com.launchdarkly.sdk.server.TestHttpUtil.makeStartedServer;
import static com.launchdarkly.sdk.server.TestUtil.hasJsonProperty;
import static com.launchdarkly.sdk.server.TestUtil.isJsonArray;
import static com.launchdarkly.sdk.server.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@SuppressWarnings("javadoc")
public class DefaultEventProcessorTest {
  private static final String SDK_KEY = "SDK_KEY";
  private static final LDUser user = new LDUser.Builder("userkey").name("Red").build();
  private static final Gson gson = new Gson();
  private static final LDValue userJson = LDValue.buildObject().put("key", "userkey").put("name", "Red").build();
  private static final LDValue filteredUserJson = LDValue.buildObject().put("key", "userkey")
      .put("privateAttrs", LDValue.buildArray().add("name").build()).build();
  private static final SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
  private static final LDConfig baseLDConfig = new LDConfig.Builder().diagnosticOptOut(true).build();
  private static final LDConfig diagLDConfig = new LDConfig.Builder().diagnosticOptOut(false).build();
  
  // Note that all of these events depend on the fact that DefaultEventProcessor does a synchronous
  // flush when it is closed; in this case, it's closed implicitly by the try-with-resources block.

  private EventProcessorBuilder baseConfig(MockWebServer server) {
    return sendEvents().baseURI(server.url("").uri());
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
      assertThat(ec.eventsUri, equalTo(LDConfig.DEFAULT_EVENTS_URI));
      assertThat(ec.flushInterval, equalTo(EventProcessorBuilder.DEFAULT_FLUSH_INTERVAL));
      assertThat(ec.inlineUsersInEvents, is(false));
      assertThat(ec.privateAttributes, equalTo(ImmutableSet.<UserAttribute>of()));
      assertThat(ec.samplingInterval, equalTo(0));
      assertThat(ec.userKeysCapacity, equalTo(EventProcessorBuilder.DEFAULT_USER_KEYS_CAPACITY));
      assertThat(ec.userKeysFlushInterval, equalTo(EventProcessorBuilder.DEFAULT_USER_KEYS_FLUSH_INTERVAL));
    }
  }
  
  @Test
  public void builderCanSpecifyConfiguration() throws Exception {
    URI uri = URI.create("http://fake");
    EventProcessorFactory epf = Components.sendEvents()
        .allAttributesPrivate(true)
        .baseURI(uri)
        .capacity(3333)
        .diagnosticRecordingInterval(Duration.ofSeconds(480))
        .flushInterval(Duration.ofSeconds(99))
        .privateAttributeNames("name", "dogs")
        .userKeysCapacity(555)
        .userKeysFlushInterval(Duration.ofSeconds(101));
    try (DefaultEventProcessor ep = (DefaultEventProcessor)epf.createEventProcessor(clientContext(SDK_KEY, LDConfig.DEFAULT))) {
      EventsConfiguration ec = ep.dispatcher.eventsConfig;
      assertThat(ec.allAttributesPrivate, is(true));
      assertThat(ec.capacity, equalTo(3333));
      assertThat(ec.diagnosticRecordingInterval, equalTo(Duration.ofSeconds(480)));
      assertThat(ec.eventsUri, equalTo(uri));
      assertThat(ec.flushInterval, equalTo(Duration.ofSeconds(99)));
      assertThat(ec.inlineUsersInEvents, is(false)); // will test this separately below
      assertThat(ec.privateAttributes, equalTo(ImmutableSet.of(UserAttribute.NAME, UserAttribute.forName("dogs"))));
      assertThat(ec.samplingInterval, equalTo(0)); // can only set this with the deprecated config API
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
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(e);
      }

      assertThat(getEventsFromLastRequest(server), contains(
        isIdentifyEvent(e, userJson)
      ));
    }
  }
  
  @Test
  public void userIsFilteredInIdentifyEvent() throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server).allAttributesPrivate(true))) {
        ep.sendEvent(e);
      }
  
      assertThat(getEventsFromLastRequest(server), contains(
          isIdentifyEvent(e, filteredUserJson)
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void individualFeatureEventIsQueuedWithIndexEvent() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(fe);
      }
    
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe, userJson),
          isFeatureEvent(fe, flag, false, null),
          isSummaryEvent()
      ));
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInIndexEvent() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server).allAttributesPrivate(true))) {
        ep.sendEvent(fe);
      }
    
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe, filteredUserJson),
          isFeatureEvent(fe, flag, false, null),
          isSummaryEvent()
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanContainInlineUser() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());
    
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server).inlineUsersInEvents(true))) {
        ep.sendEvent(fe);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(
          isFeatureEvent(fe, flag, false, userJson),
          isSummaryEvent()
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInFeatureEvent() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server)
          .inlineUsersInEvents(true).allAttributesPrivate(true))) {
        ep.sendEvent(fe);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(
          isFeatureEvent(fe, flag, false, filteredUserJson),
          isSummaryEvent()
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanContainReason() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    EvaluationReason reason = EvaluationReason.ruleMatch(1, null);
    Event.FeatureRequest fe = EventFactory.DEFAULT_WITH_REASONS.newFeatureRequestEvent(flag, user,
          new Evaluator.EvalResult(LDValue.of("value"), 1, reason), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(fe);
      }
  
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe, userJson),
          isFeatureEvent(fe, flag, false, null, reason),
          isSummaryEvent()
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void indexEventIsStillGeneratedIfInlineUsersIsTrueButFeatureEventIsNotTracked() throws Exception {
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(false).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), null);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server).inlineUsersInEvents(true))) {
        ep.sendEvent(fe);
      }
  
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe, userJson),
          isSummaryEvent()
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventKindIsDebugIfFlagIsTemporarilyInDebugMode() throws Exception {
    long futureTime = System.currentTimeMillis() + 1000000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).debugEventsUntilDate(futureTime).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(fe);
      }
    
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe, userJson),
          isFeatureEvent(fe, flag, true, userJson),
          isSummaryEvent()
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventCanBeBothTrackedAndDebugged() throws Exception {
    long futureTime = System.currentTimeMillis() + 1000000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true)
        .debugEventsUntilDate(futureTime).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(fe);
      }
  
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe, userJson),
          isFeatureEvent(fe, flag, false, null),
          isFeatureEvent(fe, flag, true, userJson),
          isSummaryEvent()
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void debugModeExpiresBasedOnClientTimeIfClientTimeIsLaterThanServerTime() throws Exception {
    // Pick a server time that is somewhat behind the client time
    long serverTime = System.currentTimeMillis() - 20000;
    MockResponse resp1 = addDateHeader(eventsSuccessResponse(), serverTime);
    MockResponse resp2 = eventsSuccessResponse();
    
    long debugUntil = serverTime + 1000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(resp1, resp2)) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        // Send and flush an event we don't care about, just so we'll receive "resp1" which sets the last server time
        ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
        ep.flush();
        ep.waitUntilInactive(); // this ensures that it has received the server response (resp1)
        server.takeRequest(); // discard the first request
        
        // Now send an event with debug mode on, with a "debug until" time that is further in
        // the future than the server time, but in the past compared to the client.
        ep.sendEvent(fe);
      }
  
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe, userJson),
          isSummaryEvent(fe.getCreationDate(), fe.getCreationDate())
      ));
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void debugModeExpiresBasedOnServerTimeIfServerTimeIsLaterThanClientTime() throws Exception {
    // Pick a server time that is somewhat ahead of the client time
    long serverTime = System.currentTimeMillis() + 20000;
    MockResponse resp1 = addDateHeader(eventsSuccessResponse(), serverTime);
    MockResponse resp2 = eventsSuccessResponse();
    
    long debugUntil = serverTime - 1000;
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(resp1, resp2)) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        // Send and flush an event we don't care about, just to set the last server time
        ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
        ep.flush();
        ep.waitUntilInactive(); // this ensures that it has received the server response (resp1)
        server.takeRequest();
        
        // Now send an event with debug mode on, with a "debug until" time that is further in
        // the future than the client time, but in the past compared to the server.
        ep.sendEvent(fe);
      }
      
      // Should get a summary event only, not a full feature event
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe, userJson),
          isSummaryEvent(fe.getCreationDate(), fe.getCreationDate())
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void twoFeatureEventsForSameUserGenerateOnlyOneIndexEvent() throws Exception {
    DataModel.FeatureFlag flag1 = flagBuilder("flagkey1").version(11).trackEvents(true).build();
    DataModel.FeatureFlag flag2 = flagBuilder("flagkey2").version(22).trackEvents(true).build();
    LDValue value = LDValue.of("value");
    Event.FeatureRequest fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value), LDValue.ofNull());
    Event.FeatureRequest fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        simpleEvaluation(1, value), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(fe1);
        ep.sendEvent(fe2);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe1, userJson),
          isFeatureEvent(fe1, flag1, false, null),
          isFeatureEvent(fe2, flag2, false, null),
          isSummaryEvent(fe1.getCreationDate(), fe2.getCreationDate())
      ));
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void identifyEventMakesIndexEventUnnecessary() throws Exception {
    Event ie = EventFactory.DEFAULT.newIdentifyEvent(user);
    DataModel.FeatureFlag flag = flagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), null);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(ie);
        ep.sendEvent(fe); 
      }
      
      assertThat(getEventsFromLastRequest(server), contains(
          isIdentifyEvent(ie, userJson),
          isFeatureEvent(fe, flag, false, null),
          isSummaryEvent()
      ));
    }
  }

  
  @SuppressWarnings("unchecked")
  @Test
  public void nonTrackedEventsAreSummarized() throws Exception {
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

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(fe1a);
        ep.sendEvent(fe1b);
        ep.sendEvent(fe1c);
        ep.sendEvent(fe2);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(
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
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void customEventIsQueuedWithUser() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    double metric = 1.5;
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data, metric);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(ce);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(ce, userJson),
          isCustomEvent(ce, null)
      ));
    }
  }

  @Test
  public void customEventCanContainInlineUser() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data, null);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server).inlineUsersInEvents(true))) {
        ep.sendEvent(ce);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(isCustomEvent(ce, userJson)));
    }
  }
  
  @Test
  public void userIsFilteredInCustomEvent() throws Exception {
    LDValue data = LDValue.buildObject().put("thing", LDValue.of("stuff")).build();
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data, null);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server)
          .inlineUsersInEvents(true).allAttributesPrivate(true))) {
        ep.sendEvent(ce);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(isCustomEvent(ce, filteredUserJson)));
    }
  }
  
  @Test
  public void closingEventProcessorForcesSynchronousFlush() throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(e);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(isIdentifyEvent(e, userJson)));
    }
  }
  
  @Test
  public void nothingIsSentIfThereAreNoEvents() throws Exception {
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      DefaultEventProcessor ep = makeEventProcessor(baseConfig(server));
      ep.close();
    
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  public void diagnosticEventsSentToDiagnosticEndpoint() throws Exception {
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server), diagnosticAccumulator)) {
          RecordedRequest initReq = server.takeRequest();
          ep.postDiagnostic();
          RecordedRequest periodicReq = server.takeRequest();

          assertThat(initReq.getPath(), equalTo("//diagnostic"));
          assertThat(periodicReq.getPath(), equalTo("//diagnostic"));
      }
    }
  }

  @Test
  public void initialDiagnosticEventHasInitBody() throws Exception {
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
      DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server), diagnosticAccumulator)) {
        RecordedRequest req = server.takeRequest();

        assertNotNull(req);

        DiagnosticEvent.Init initEvent = gson.fromJson(req.getBody().readUtf8(), DiagnosticEvent.Init.class);

        assertNotNull(initEvent);
        assertThat(initEvent.kind, equalTo("diagnostic-init"));
        assertThat(initEvent.id, samePropertyValuesAs(diagnosticId));
        assertNotNull(initEvent.configuration);
        assertNotNull(initEvent.sdk);
        assertNotNull(initEvent.platform);
      }
    }
  }

  @Test
  public void periodicDiagnosticEventHasStatisticsBody() throws Exception {
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse(), eventsSuccessResponse())) {
      DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
      DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
      long dataSinceDate = diagnosticAccumulator.dataSinceDate;
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server), diagnosticAccumulator)) {
        // Ignore the initial diagnostic event
        server.takeRequest();
        ep.postDiagnostic();
        RecordedRequest periodicReq = server.takeRequest();

        assertNotNull(periodicReq);
        DiagnosticEvent.Statistics statsEvent = gson.fromJson(periodicReq.getBody().readUtf8(), DiagnosticEvent.Statistics.class);

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
  }

  @Test
  public void periodicDiagnosticEventGetsEventsInLastBatchAndDeduplicatedUsers() throws Exception {
    DataModel.FeatureFlag flag1 = flagBuilder("flagkey1").version(11).trackEvents(true).build();
    DataModel.FeatureFlag flag2 = flagBuilder("flagkey2").version(22).trackEvents(true).build();
    LDValue value = LDValue.of("value");
    Event.FeatureRequest fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
            simpleEvaluation(1, value), LDValue.ofNull());
    Event.FeatureRequest fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
            simpleEvaluation(1, value), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse(), eventsSuccessResponse())) {
      DiagnosticId diagnosticId = new DiagnosticId(SDK_KEY);
      DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server), diagnosticAccumulator)) {
        // Ignore the initial diagnostic event
        server.takeRequest();

        ep.sendEvent(fe1);
        ep.sendEvent(fe2);
        ep.flush();
        // Ignore normal events
        server.takeRequest();

        ep.postDiagnostic();
        RecordedRequest periodicReq = server.takeRequest();

        assertNotNull(periodicReq);
        DiagnosticEvent.Statistics statsEvent = gson.fromJson(periodicReq.getBody().readUtf8(), DiagnosticEvent.Statistics.class);

        assertNotNull(statsEvent);
        assertThat(statsEvent.deduplicatedUsers, equalTo(1L));
        assertThat(statsEvent.eventsInLastBatch, equalTo(3L));
        assertThat(statsEvent.droppedEvents, equalTo(0L));
      }
    }
  }

  
  @Test
  public void sdkKeyIsSent() throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(e);
      }
      
      RecordedRequest req = server.takeRequest();      
      assertThat(req.getHeader("Authorization"), equalTo(SDK_KEY));
    }
  }

  @Test
  public void sdkKeyIsSentOnDiagnosticEvents() throws Exception {
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server), diagnosticAccumulator)) {
        RecordedRequest initReq = server.takeRequest();
        ep.postDiagnostic();
        RecordedRequest periodicReq = server.takeRequest();

        assertThat(initReq.getHeader("Authorization"), equalTo(SDK_KEY));
        assertThat(periodicReq.getHeader("Authorization"), equalTo(SDK_KEY));
      }
    }
  }

  @Test
  public void eventSchemaIsSent() throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(e);
      }

      RecordedRequest req = server.takeRequest();
      assertThat(req.getHeader("X-LaunchDarkly-Event-Schema"), equalTo("3"));
    }
  }

  @Test
  public void eventPayloadIdIsSent() throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(e);
      }

      RecordedRequest req = server.takeRequest();
      String payloadHeaderValue = req.getHeader("X-LaunchDarkly-Payload-ID");
      assertThat(payloadHeaderValue, notNullValue(String.class));
      assertThat(UUID.fromString(payloadHeaderValue), notNullValue(UUID.class));
    }
  }

  @Test
  public void eventPayloadIdReusedOnRetry() throws Exception {
    MockResponse errorResponse = new MockResponse().setResponseCode(429);
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(errorResponse, eventsSuccessResponse(), eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(e);
        ep.flush();
        // Necessary to ensure the retry occurs before the second request for test assertion ordering
        ep.waitUntilInactive();
        ep.sendEvent(e);
      }

      // Failed response request
      RecordedRequest req = server.takeRequest(0, TimeUnit.SECONDS);
      String payloadId = req.getHeader("X-LaunchDarkly-Payload-ID");
      // Retry request has same payload ID as failed request
      req = server.takeRequest(0, TimeUnit.SECONDS);
      String retryId = req.getHeader("X-LaunchDarkly-Payload-ID");
      assertThat(retryId, equalTo(payloadId));
      // Second request has different payload ID from first request
      req = server.takeRequest(0, TimeUnit.SECONDS);
      payloadId = req.getHeader("X-LaunchDarkly-Payload-ID");
      assertThat(retryId, not(equalTo(payloadId)));
    }
  }
  
  @Test
  public void eventSchemaNotSetOnDiagnosticEvents() throws Exception {
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server), diagnosticAccumulator)) {
        RecordedRequest initReq = server.takeRequest();
        ep.postDiagnostic();
        RecordedRequest periodicReq = server.takeRequest();

        assertNull(initReq.getHeader("X-LaunchDarkly-Event-Schema"));
        assertNull(periodicReq.getHeader("X-LaunchDarkly-Event-Schema"));
      }
    }
  }

  @Test
  public void wrapperHeaderSentWhenSet() throws Exception {
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      LDConfig config = new LDConfig.Builder()
              .diagnosticOptOut(true)
              .wrapperName("Scala")
              .wrapperVersion("0.1.0")
              .build();
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server), config)) {
        Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
        ep.sendEvent(e);
      }

      RecordedRequest req = server.takeRequest();
      assertThat(req.getHeader("X-LaunchDarkly-Wrapper"), equalTo("Scala/0.1.0"));
    }
  }

  @Test
  public void wrapperHeaderSentWithoutVersion() throws Exception {
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      LDConfig config = new LDConfig.Builder()
          .diagnosticOptOut(true)
          .wrapperName("Scala")
          .build();

      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server), config)) {
        Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
        ep.sendEvent(e);
      }

      RecordedRequest req = server.takeRequest();
      assertThat(req.getHeader("X-LaunchDarkly-Wrapper"), equalTo("Scala"));
    }
  }

  @Test
  public void http400ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(400);
  }

  @Test
  public void http401ErrorIsUnrecoverable() throws Exception {
    testUnrecoverableHttpError(401);
  }

  @Test
  public void http403ErrorIsUnrecoverable() throws Exception {
    testUnrecoverableHttpError(403);
  }

  // Cannot test our retry logic for 408, because OkHttp insists on doing its own retry on 408 so that
  // we never actually see that response status.
//  @Test
//  public void http408ErrorIsRecoverable() throws Exception {
//    testRecoverableHttpError(408);
//  }

  @Test
  public void http429ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(429);
  }

  @Test
  public void http500ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(500);
  }
  
  @Test
  public void flushIsRetriedOnceAfter5xxError() throws Exception {
  }

  @Test
  public void httpClientDoesNotAllowSelfSignedCertByDefault() throws Exception {
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(eventsSuccessResponse())) {
      EventProcessorBuilder ec = sendEvents().baseURI(serverWithCert.uri());
      try (DefaultEventProcessor ep = makeEventProcessor(ec)) {
        Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
        ep.sendEvent(e);
        
        ep.flush();
        ep.waitUntilInactive();
      }
      
      assertEquals(0, serverWithCert.server.getRequestCount());
    }
  }
  
  @Test
  public void httpClientCanUseCustomTlsConfig() throws Exception {
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(eventsSuccessResponse())) {
      EventProcessorBuilder ec = sendEvents().baseURI(serverWithCert.uri());
      LDConfig config = new LDConfig.Builder()
          .sslSocketFactory(serverWithCert.socketFactory, serverWithCert.trustManager) // allows us to trust the self-signed cert
          .build();
      
      try (DefaultEventProcessor ep = makeEventProcessor(ec, config)) {
        Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
        ep.sendEvent(e);
        
        ep.flush();
        ep.waitUntilInactive();
      }
      
      assertEquals(1, serverWithCert.server.getRequestCount());
    }
  }
  
  private void testUnrecoverableHttpError(int status) throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(e);
      }

      RecordedRequest req = server.takeRequest(0, TimeUnit.SECONDS);
      assertThat(req, notNullValue(RecordedRequest.class)); // this was the initial request that received the error
      
      // it does not retry after this type of error, so there are no more requests 
      assertThat(server.takeRequest(0, TimeUnit.SECONDS), nullValue(RecordedRequest.class));
    }
  }
  
  private void testRecoverableHttpError(int status) throws Exception {
    MockResponse errorResponse = new MockResponse().setResponseCode(status);
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);    

    // send two errors in a row, because the flush will be retried one time
    try (MockWebServer server = makeStartedServer(errorResponse, errorResponse, eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = makeEventProcessor(baseConfig(server))) {
        ep.sendEvent(e);
      }

      RecordedRequest req = server.takeRequest(0, TimeUnit.SECONDS);
      assertThat(req, notNullValue(RecordedRequest.class));
      req = server.takeRequest(0, TimeUnit.SECONDS);
      assertThat(req, notNullValue(RecordedRequest.class));
      req = server.takeRequest(0, TimeUnit.SECONDS);
      assertThat(req, nullValue(RecordedRequest.class)); // only 2 requests total
    }
  }

  private MockResponse eventsSuccessResponse() {
    return new MockResponse().setResponseCode(202);
  }

  private MockResponse addDateHeader(MockResponse response, long timestamp) {
    return response.addHeader("Date", httpDateFormat.format(new Date(timestamp)));
  }
  
  private Iterable<LDValue> getEventsFromLastRequest(MockWebServer server) throws Exception {
    RecordedRequest req = server.takeRequest(0, TimeUnit.MILLISECONDS);
    assertNotNull(req);
    return gson.fromJson(req.getBody().readUtf8(), LDValue.class).values();
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
