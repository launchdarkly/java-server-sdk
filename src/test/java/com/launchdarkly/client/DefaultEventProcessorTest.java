package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.client.TestUtil.hasJsonProperty;
import static com.launchdarkly.client.TestUtil.isJsonArray;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
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

  private final LDConfig.Builder configBuilder = new LDConfig.Builder();
  private final MockWebServer server = new MockWebServer();
  private DefaultEventProcessor ep;
  
  @Before
  public void setup() throws Exception {
    server.start();
    configBuilder.eventsURI(server.url("/").uri());
  }
  
  @After
  public void teardown() throws Exception {
    if (ep != null) {
      ep.close();
    }
    server.shutdown();
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void identifyEventIsQueued() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    ep.sendEvent(e);
    
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(isIdentifyEvent(e, userJson)));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInIdentifyEvent() throws Exception {
    configBuilder.allAttributesPrivate(true);
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    ep.sendEvent(e);
    
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(isIdentifyEvent(e, filteredUserJson)));    
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void individualFeatureEventIsQueuedWithIndexEvent() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, false, null),
        isSummaryEvent()
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInIndexEvent() throws Exception {
    configBuilder.allAttributesPrivate(true);
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isIndexEvent(fe, filteredUserJson),
        isFeatureEvent(fe, flag, false, null),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanContainInlineUser() throws Exception {
    configBuilder.inlineUsersInEvents(true);
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isFeatureEvent(fe, flag, false, userJson),
        isSummaryEvent()
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInFeatureEvent() throws Exception {
    configBuilder.inlineUsersInEvents(true).allAttributesPrivate(true);
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isFeatureEvent(fe, flag, false, filteredUserJson),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void eventKindIsDebugIfFlagIsTemporarilyInDebugMode() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    long futureTime = System.currentTimeMillis() + 1000000;
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(futureTime).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isIndexEvent(fe, userJson),
        isFeatureEvent(fe, flag, true, null),
        isSummaryEvent()
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void debugModeExpiresBasedOnClientTimeIfClientTimeIsLaterThanServerTime() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    
    // Pick a server time that is somewhat behind the client time
    long serverTime = System.currentTimeMillis() - 20000;
    
    // Send and flush an event we don't care about, just to set the last server time
    ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
    flushAndGetEvents(addDateHeader(new MockResponse(), serverTime));
    
    // Now send an event with debug mode on, with a "debug until" time that is further in
    // the future than the server time, but in the past compared to the client.
    long debugUntil = serverTime + 1000;
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    // Should get a summary event only, not a full feature event
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isIndexEvent(fe, userJson),
        isSummaryEvent(fe.creationDate, fe.creationDate)
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void debugModeExpiresBasedOnServerTimeIfServerTimeIsLaterThanClientTime() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    
    // Pick a server time that is somewhat ahead of the client time
    long serverTime = System.currentTimeMillis() + 20000;
    
    // Send and flush an event we don't care about, just to set the last server time
    ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
    flushAndGetEvents(addDateHeader(new MockResponse(), serverTime));
    
    // Now send an event with debug mode on, with a "debug until" time that is further in
    // the future than the client time, but in the past compared to the server.
    long debugUntil = serverTime - 1000;
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    Event.FeatureRequest fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    // Should get a summary event only, not a full feature event
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isIndexEvent(fe, userJson),
        isSummaryEvent(fe.creationDate, fe.creationDate)
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void twoFeatureEventsForSameUserGenerateOnlyOneIndexEvent() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag1 = new FeatureFlagBuilder("flagkey1").version(11).trackEvents(true).build();
    FeatureFlag flag2 = new FeatureFlagBuilder("flagkey2").version(22).trackEvents(true).build();
    JsonElement value = new JsonPrimitive("value");
    Event.FeatureRequest fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(new Integer(1), value), null);
    Event.FeatureRequest fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        new FeatureFlag.VariationAndValue(new Integer(1), value), null);
    ep.sendEvent(fe1);
    ep.sendEvent(fe2);
    
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isIndexEvent(fe1, userJson),
        isFeatureEvent(fe1, flag1, false, null),
        isFeatureEvent(fe2, flag2, false, null),
        isSummaryEvent(fe1.creationDate, fe2.creationDate)
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void nonTrackedEventsAreSummarized() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag1 = new FeatureFlagBuilder("flagkey1").version(11).build();
    FeatureFlag flag2 = new FeatureFlagBuilder("flagkey2").version(22).build();
    JsonElement value = new JsonPrimitive("value");
    JsonElement default1 = new JsonPrimitive("default1");
    JsonElement default2 = new JsonPrimitive("default2");
    Event fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(new Integer(1), value), default1);
    Event fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        new FeatureFlag.VariationAndValue(new Integer(1), value), default2);
    ep.sendEvent(fe1);
    ep.sendEvent(fe2);
    
    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isIndexEvent(fe1, userJson),
        allOf(
            isSummaryEvent(fe1.creationDate, fe2.creationDate),
            hasSummaryFlag(flag1.getKey(), default1,
                hasItem(isSummaryEventCounter(flag1, value, 1))),
            hasSummaryFlag(flag2.getKey(), default2,
                hasItem(isSummaryEventCounter(flag2, value, 1)))
        )
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void customEventIsQueuedWithUser() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data);
    ep.sendEvent(ce);

    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(
        isIndexEvent(ce, userJson),
        isCustomEvent(ce, null)
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void customEventCanContainInlineUser() throws Exception {
    configBuilder.inlineUsersInEvents(true);
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data);
    ep.sendEvent(ce);

    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(isCustomEvent(ce, userJson)));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void userIsFilteredInCustomEvent() throws Exception {
    configBuilder.inlineUsersInEvents(true).allAttributesPrivate(true);
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    Event.Custom ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data);
    ep.sendEvent(ce);

    JsonArray output = flushAndGetEvents(new MockResponse());
    assertThat(output, hasItems(isCustomEvent(ce, filteredUserJson)));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void closingEventProcessorForcesSynchronousFlush() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    ep.sendEvent(e);

    server.enqueue(new MockResponse());
    ep.close();
    JsonArray output = getEventsFromLastRequest();
    assertThat(output, hasItems(isIdentifyEvent(e, userJson)));
  }
  
  @Test
  public void nothingIsSentIfThereAreNoEvents() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    ep.close();
    
    assertEquals(0, server.getRequestCount());
  }
  
  @Test
  public void sdkKeyIsSent() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    ep.sendEvent(e);
    
    server.enqueue(new MockResponse());
    ep.close();
    RecordedRequest req = server.takeRequest();
    
    assertThat(req.getHeader("Authorization"), equalTo(SDK_KEY));
  }
  
  @Test
  public void noMorePayloadsAreSentAfter401Error() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    ep.sendEvent(e);
    flushAndGetEvents(new MockResponse().setResponseCode(401));
    
    ep.sendEvent(e);
    ep.flush();
    ep.waitUntilInactive();
    RecordedRequest req = server.takeRequest(0, TimeUnit.SECONDS);
    assertThat(req, nullValue(RecordedRequest.class));
  }

  private MockResponse addDateHeader(MockResponse response, long timestamp) {
    return response.addHeader("Date", DefaultEventProcessor.HTTP_DATE_FORMAT.format(new Date(timestamp)));
  }
  
  private JsonArray flushAndGetEvents(MockResponse response) throws Exception {
    server.enqueue(response);
    ep.flush();
    ep.waitUntilInactive();
    return getEventsFromLastRequest();
  }
  
  private JsonArray getEventsFromLastRequest() throws Exception {
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

  @SuppressWarnings("unchecked")
  private Matcher<JsonElement> isFeatureEvent(Event.FeatureRequest sourceEvent, FeatureFlag flag, boolean debug, JsonElement inlineUser) {
    return allOf(
        hasJsonProperty("kind", debug ? "debug" : "feature"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("key", flag.getKey()),
        hasJsonProperty("version", (double)flag.getVersion()),
        hasJsonProperty("value", sourceEvent.value),
        (inlineUser != null) ? hasJsonProperty("userKey", nullValue(JsonElement.class)) :
          hasJsonProperty("userKey", sourceEvent.user.getKeyAsString()),
        (inlineUser != null) ? hasJsonProperty("user", inlineUser) :
          hasJsonProperty("user", nullValue(JsonElement.class))
    );
  }

  private Matcher<JsonElement> isCustomEvent(Event.Custom sourceEvent, JsonElement inlineUser) {
    return allOf(
        hasJsonProperty("kind", "custom"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("key", "eventkey"),
        (inlineUser != null) ? hasJsonProperty("userKey", nullValue(JsonElement.class)) :
          hasJsonProperty("userKey", sourceEvent.user.getKeyAsString()),
        (inlineUser != null) ? hasJsonProperty("user", inlineUser) :
          hasJsonProperty("user", nullValue(JsonElement.class)),
        hasJsonProperty("data", sourceEvent.data)
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
  
  private Matcher<JsonElement> hasSummaryFlag(String key, JsonElement defaultVal, Matcher<Iterable<? super JsonElement>> counters) {
    return hasJsonProperty("features",
        hasJsonProperty(key, allOf(
          hasJsonProperty("default", defaultVal),
          hasJsonProperty("counters", isJsonArray(counters))
    )));
  }
  
  private Matcher<JsonElement> isSummaryEventCounter(FeatureFlag flag, JsonElement value, int count) {
    return allOf(
        hasJsonProperty("version", (double)flag.getVersion()),
        hasJsonProperty("value", value),
        hasJsonProperty("count", (double)count)
    );
  }
}
