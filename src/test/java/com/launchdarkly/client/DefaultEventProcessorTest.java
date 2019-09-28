package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.DefaultEventProcessor.EventDispatcher;
import com.launchdarkly.client.value.LDValue;

import org.hamcrest.Matcher;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.client.TestHttpUtil.httpsServerWithSelfSignedCert;
import static com.launchdarkly.client.TestHttpUtil.makeStartedServer;
import static com.launchdarkly.client.TestUtil.hasJsonProperty;
import static com.launchdarkly.client.TestUtil.isJsonArray;
import static com.launchdarkly.client.TestUtil.simpleEvaluation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class DefaultEventProcessorTest {
  private static final String SDK_KEY = "SDK_KEY";
  private static final LDUser user = new LDUser.Builder("userkey").name("Red").build();
  private static final Gson gson = new Gson();
  private static final JsonElement userJson =
      gson.fromJson("{\"key\":\"userkey\",\"name\":\"Red\"}", JsonElement.class);
  private static final JsonElement filteredUserJson =
      gson.fromJson("{\"key\":\"userkey\",\"privateAttrs\":[\"name\"]}", JsonElement.class);
  private static final SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

  // Note that all of these events depend on the fact that DefaultEventProcessor does a synchronous
  // flush when it is closed; in this case, it's closed implicitly by the try-with-resources block.
  
  @Test
  public void identifyEventIsQueued() throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
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
      LDConfig config = baseConfig(server).allAttributesPrivate(true).build();
      
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, config)) {
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      LDConfig config = baseConfig(server).allAttributesPrivate(true).build();
      
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, config)) {
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());
    
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      LDConfig config = baseConfig(server).inlineUsersInEvents(true).build();
      
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, config)) {
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      LDConfig config = baseConfig(server).inlineUsersInEvents(true).allAttributesPrivate(true).build();

      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, config)) {
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    EvaluationReason reason = EvaluationReason.ruleMatch(1, null);
    Event.FeatureRequest fe = EventFactory.DEFAULT_WITH_REASONS.newFeatureRequestEvent(flag, user,
          EvaluationDetail.fromJsonValue(LDValue.of("value"), 1, reason), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(false).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), null);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      LDConfig config = baseConfig(server).inlineUsersInEvents(true).build();

      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, config)) {
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(futureTime).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true)
        .debugEventsUntilDate(futureTime).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(resp1, resp2)) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
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
          isSummaryEvent(fe.creationDate, fe.creationDate)
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
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        simpleEvaluation(1, LDValue.of("value")), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(resp1, resp2)) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
        
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
          isSummaryEvent(fe.creationDate, fe.creationDate)
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void twoFeatureEventsForSameUserGenerateOnlyOneIndexEvent() throws Exception {
    FeatureFlag flag1 = new FeatureFlagBuilder("flagkey1").version(11).trackEvents(true).build();
    FeatureFlag flag2 = new FeatureFlagBuilder("flagkey2").version(22).trackEvents(true).build();
    LDValue value = LDValue.of("value");
    Event.FeatureRequest fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(1, value), LDValue.ofNull());
    Event.FeatureRequest fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        simpleEvaluation(1, value), LDValue.ofNull());

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
        ep.sendEvent(fe1);
        ep.sendEvent(fe2);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe1, userJson),
          isFeatureEvent(fe1, flag1, false, null),
          isFeatureEvent(fe2, flag2, false, null),
          isSummaryEvent(fe1.creationDate, fe2.creationDate)
      ));
    }
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void nonTrackedEventsAreSummarized() throws Exception {
    FeatureFlag flag1 = new FeatureFlagBuilder("flagkey1").version(11).build();
    FeatureFlag flag2 = new FeatureFlagBuilder("flagkey2").version(22).build();
    LDValue value = LDValue.of("value");
    LDValue default1 = LDValue.of("default1");
    LDValue default2 = LDValue.of("default2");
    Event fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        simpleEvaluation(2, value), default1);
    Event fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        simpleEvaluation(2, value), default2);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
        ep.sendEvent(fe1);
        ep.sendEvent(fe2);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(
          isIndexEvent(fe1, userJson),
          allOf(
              isSummaryEvent(fe1.creationDate, fe2.creationDate),
              hasSummaryFlag(flag1.getKey(), default1,
                  contains(isSummaryEventCounter(flag1, 2, value, 1))),
              hasSummaryFlag(flag2.getKey(), default2,
                  contains(isSummaryEventCounter(flag2, 2, value, 1)))
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
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
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
      LDConfig config = baseConfig(server).inlineUsersInEvents(true).build();

      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, config)) {
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
      LDConfig config = baseConfig(server).inlineUsersInEvents(true).allAttributesPrivate(true).build();

      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, config)) {
        ep.sendEvent(ce);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(isCustomEvent(ce, filteredUserJson)));
    }
  }
  
  @Test
  public void closingEventProcessorForcesSynchronousFlush() throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
        ep.sendEvent(e);
      }
      
      assertThat(getEventsFromLastRequest(server), contains(isIdentifyEvent(e, userJson)));
    }
  }
  
  @Test
  public void nothingIsSentIfThereAreNoEvents() throws Exception {
    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build());
      ep.close();
    
      assertEquals(0, server.getRequestCount());
    }
  }
  
  @Test
  public void sdkKeyIsSent() throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
        ep.sendEvent(e);
      }
      
      RecordedRequest req = server.takeRequest();      
      assertThat(req.getHeader("Authorization"), equalTo(SDK_KEY));
    }
  }

  @Test
  public void eventSchemaIsSent() throws Exception {
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);

    try (MockWebServer server = makeStartedServer(eventsSuccessResponse())) {
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
        ep.sendEvent(e);
      }

      RecordedRequest req = server.takeRequest();
      assertThat(req.getHeader("X-LaunchDarkly-Event-Schema"), equalTo("3"));
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
      LDConfig config = new LDConfig.Builder()
          .eventsURI(serverWithCert.uri())
          .build();
      
      try (DefaultEventProcessor ep = new DefaultEventProcessor("sdk-key", config)) {
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
      LDConfig config = new LDConfig.Builder()
          .eventsURI(serverWithCert.uri())
          .sslSocketFactory(serverWithCert.sslClient.socketFactory, serverWithCert.sslClient.trustManager) // allows us to trust the self-signed cert
          .build();
      
      try (DefaultEventProcessor ep = new DefaultEventProcessor("sdk-key", config)) {
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
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
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
      try (DefaultEventProcessor ep = new DefaultEventProcessor(SDK_KEY, baseConfig(server).build())) {
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

  private LDConfig.Builder baseConfig(MockWebServer server) {
    return new LDConfig.Builder().eventsURI(server.url("/").uri());
  }
  
  private MockResponse eventsSuccessResponse() {
    return new MockResponse().setResponseCode(202);
  }

  private MockResponse addDateHeader(MockResponse response, long timestamp) {
    return response.addHeader("Date", httpDateFormat.format(new Date(timestamp)));
  }
  
  private JsonArray getEventsFromLastRequest(MockWebServer server) throws Exception {
    RecordedRequest req = server.takeRequest(0, TimeUnit.MILLISECONDS);
    assertNotNull(req);
    return gson.fromJson(req.getBody().readUtf8(), JsonElement.class).getAsJsonArray();
  }
  
  private Matcher<JsonElement> isIdentifyEvent(Event sourceEvent, JsonElement user) {
    return allOf(
        hasJsonProperty("kind", "identify"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("user", user)
    );
  }

  private Matcher<JsonElement> isIndexEvent(Event sourceEvent, JsonElement user) {
    return allOf(
        hasJsonProperty("kind", "index"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("user", user)
    );
  }

  private Matcher<JsonElement> isFeatureEvent(Event.FeatureRequest sourceEvent, FeatureFlag flag, boolean debug, JsonElement inlineUser) {
    return isFeatureEvent(sourceEvent, flag, debug, inlineUser, null);
  }

  @SuppressWarnings("unchecked")
  private Matcher<JsonElement> isFeatureEvent(Event.FeatureRequest sourceEvent, FeatureFlag flag, boolean debug, JsonElement inlineUser,
      EvaluationReason reason) {
    return allOf(
        hasJsonProperty("kind", debug ? "debug" : "feature"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("key", flag.getKey()),
        hasJsonProperty("version", (double)flag.getVersion()),
        hasJsonProperty("variation", sourceEvent.variation),
        hasJsonProperty("value", sourceEvent.value),
        (inlineUser != null) ? hasJsonProperty("userKey", nullValue(JsonElement.class)) :
          hasJsonProperty("userKey", sourceEvent.user.getKeyAsString()),
        (inlineUser != null) ? hasJsonProperty("user", inlineUser) :
          hasJsonProperty("user", nullValue(JsonElement.class)),
        (reason == null) ? hasJsonProperty("reason", nullValue(JsonElement.class)) :
          hasJsonProperty("reason", gson.toJsonTree(reason))
    );
  }

  @SuppressWarnings("unchecked")
  private Matcher<JsonElement> isCustomEvent(Event.Custom sourceEvent, JsonElement inlineUser) {
    return allOf(
        hasJsonProperty("kind", "custom"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("key", "eventkey"),
        (inlineUser != null) ? hasJsonProperty("userKey", nullValue(JsonElement.class)) :
          hasJsonProperty("userKey", sourceEvent.user.getKeyAsString()),
        (inlineUser != null) ? hasJsonProperty("user", inlineUser) :
          hasJsonProperty("user", nullValue(JsonElement.class)),
        hasJsonProperty("data", sourceEvent.data),
        (sourceEvent.metricValue == null) ? hasJsonProperty("metricValue", nullValue(JsonElement.class)) :
          hasJsonProperty("metricValue", sourceEvent.metricValue.doubleValue())              
    );
  }

  private Matcher<JsonElement> isSummaryEvent() {
    return hasJsonProperty("kind", "summary");
  }

  private Matcher<JsonElement> isSummaryEvent(long startDate, long endDate) {
    return allOf(
        hasJsonProperty("kind", "summary"),
        hasJsonProperty("startDate", (double)startDate),
        hasJsonProperty("endDate", (double)endDate)
    );
  }
  
  private Matcher<JsonElement> hasSummaryFlag(String key, LDValue defaultVal, Matcher<Iterable<? extends JsonElement>> counters) {
    return hasJsonProperty("features",
        hasJsonProperty(key, allOf(
          hasJsonProperty("default", defaultVal),
          hasJsonProperty("counters", isJsonArray(counters))
    )));
  }
  
  private Matcher<JsonElement> isSummaryEventCounter(FeatureFlag flag, Integer variation, LDValue value, int count) {
    return allOf(
        hasJsonProperty("variation", variation),
        hasJsonProperty("version", (double)flag.getVersion()),
        hasJsonProperty("value", value),
        hasJsonProperty("count", (double)count)
    );
  }
}
