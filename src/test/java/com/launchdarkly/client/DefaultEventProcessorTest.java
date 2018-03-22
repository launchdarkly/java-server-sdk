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

import static com.launchdarkly.client.TestUtil.hasJsonProperty;
import static com.launchdarkly.client.TestUtil.isJsonArray;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class DefaultEventProcessorTest {
  private static final String SDK_KEY = "SDK_KEY";
  private static final LDUser user = new LDUser.Builder("userkey").name("Red").build();
  private static final Gson gson = new Gson();

  private final LDConfig.Builder configBuilder = new LDConfig.Builder();
  private final MockWebServer server = new MockWebServer();
  private Long serverTimestamp; 
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
    
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        allOf(
          hasJsonProperty("kind", "identify"),
          hasJsonProperty("creationDate", (double)e.creationDate),
          hasJsonProperty("user", makeUserJson(user))
        )));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void individualFeatureEventIsQueuedWithIndexEvent() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    FeatureRequestEvent fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(fe),
        isFeatureEvent(fe, flag, false, false)
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void featureEventCanContainInlineUser() throws Exception {
    configBuilder.inlineUsersInEvents(true);
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    FeatureRequestEvent fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isFeatureEvent(fe, flag, false, true)
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void eventKindIsDebugIfFlagIsTemporarilyInDebugMode() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    long futureTime = System.currentTimeMillis() + 1000000;
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(futureTime).build();
    FeatureRequestEvent fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(fe),
        isFeatureEvent(fe, flag, true, false)
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void debugModeExpiresBasedOnClientTimeIfClientTimeIsLaterThanServerTime() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    
    // Pick a server time that is somewhat behind the client time
    long serverTime = System.currentTimeMillis() - 20000;
    
    // Send and flush an event we don't care about, just to set the last server time
    serverTimestamp = serverTime;
    ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
    flushAndGetEvents();
    
    // Now send an event with debug mode on, with a "debug until" time that is further in
    // the future than the server time, but in the past compared to the client.
    long debugUntil = serverTime + 1000;
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    FeatureRequestEvent fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    // Should get a summary event only, not a full feature event
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(fe),
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
    serverTimestamp = serverTime;
    ep.sendEvent(EventFactory.DEFAULT.newIdentifyEvent(new LDUser.Builder("otherUser").build()));
    flushAndGetEvents();
    
    // Now send an event with debug mode on, with a "debug until" time that is further in
    // the future than the client time, but in the past compared to the server.
    long debugUntil = serverTime - 1000;
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(debugUntil).build();
    FeatureRequestEvent fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    // Should get a summary event only, not a full feature event
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(fe),
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
    FeatureRequestEvent fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(new Integer(1), value), null);
    FeatureRequestEvent fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        new FeatureFlag.VariationAndValue(new Integer(1), value), null);
    ep.sendEvent(fe1);
    ep.sendEvent(fe2);
    
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(fe1),
        isFeatureEvent(fe1, flag1, false, false),
        isFeatureEvent(fe2, flag2, false, false),
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
    
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(fe1),
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
    CustomEvent ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data);
    ep.sendEvent(ce);

    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(ce),
        isCustomEvent(ce, false)
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void customEventCanContainInlineUser() throws Exception {
    configBuilder.inlineUsersInEvents(true);
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    CustomEvent ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data);
    ep.sendEvent(ce);

    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isCustomEvent(ce, true)
    ));
  }
  
  @Test
  public void nothingIsSentIfThereAreNoEvents() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    ep.flush();
    
    assertEquals(0, server.getRequestCount());
  }
  
  @Test
  public void sdkKeyIsSent() throws Exception {
    ep = new DefaultEventProcessor(SDK_KEY, configBuilder.build());
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    ep.sendEvent(e);
    
    server.enqueue(new MockResponse());
    ep.flush();
    RecordedRequest req = server.takeRequest();
    
    assertThat(req.getHeader("Authorization"), equalTo(SDK_KEY));
  }
  
  private JsonArray flushAndGetEvents() throws Exception {
    MockResponse response = new MockResponse();
    if (serverTimestamp != null) {
      response.addHeader("Date", DefaultEventProcessor.HTTP_DATE_FORMAT.format(new Date(serverTimestamp)));
    }
    server.enqueue(response);
    ep.flush();
    RecordedRequest req = server.takeRequest();
    return gson.fromJson(req.getBody().readUtf8(), JsonElement.class).getAsJsonArray();
  }
  
  private JsonElement makeUserJson(LDUser user) {
    // need to use the gson instance from the config object, which has a custom serializer
    return configBuilder.build().gson.toJsonTree(user);
  }
  
  private Matcher<JsonElement> isIndexEvent(Event sourceEvent) {
    return allOf(
        hasJsonProperty("kind", "index"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("user", makeUserJson(sourceEvent.user))
    );
  }

  @SuppressWarnings("unchecked")
  private Matcher<JsonElement> isFeatureEvent(FeatureRequestEvent sourceEvent, FeatureFlag flag, boolean debug, boolean inlineUsers) {
    return allOf(
        hasJsonProperty("kind", debug ? "debug" : "feature"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("key", flag.getKey()),
        hasJsonProperty("version", (double)flag.getVersion()),
        hasJsonProperty("value", sourceEvent.value),
        inlineUsers ? hasJsonProperty("userKey", nullValue(JsonElement.class)) :
          hasJsonProperty("userKey", sourceEvent.user.getKeyAsString()),
        inlineUsers ? hasJsonProperty("user", makeUserJson(sourceEvent.user)) :
          hasJsonProperty("user", nullValue(JsonElement.class))
    );
  }

  private Matcher<JsonElement> isCustomEvent(CustomEvent sourceEvent, boolean inlineUsers) {
    return allOf(
        hasJsonProperty("kind", "custom"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("key", "eventkey"),
        inlineUsers ? hasJsonProperty("userKey", nullValue(JsonElement.class)) :
          hasJsonProperty("userKey", sourceEvent.user.getKeyAsString()),
        inlineUsers ? hasJsonProperty("user", makeUserJson(sourceEvent.user)) :
          hasJsonProperty("user", nullValue(JsonElement.class)),
        hasJsonProperty("data", sourceEvent.data)
    );
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
