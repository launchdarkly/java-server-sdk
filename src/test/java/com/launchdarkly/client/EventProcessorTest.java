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

import static com.launchdarkly.client.TestUtils.hasJsonProperty;
import static com.launchdarkly.client.TestUtils.isJsonArray;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class EventProcessorTest {
  private static final String SDK_KEY = "SDK_KEY";
  private static final LDUser user = new LDUser.Builder("userkey").name("Red").build();
  private static final Gson gson = new Gson();

  private final LDConfig.Builder configBuilder = new LDConfig.Builder();
  private final MockWebServer server = new MockWebServer();
  private EventProcessor ep;
  
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
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
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
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    FeatureRequestEvent fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(fe),
        isFeatureEvent(fe, flag, false)
    ));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void eventKindIsDebugIfFlagIsTemporarilyInDebugMode() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    long futureTime = System.currentTimeMillis() + 1000000;
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(futureTime).build();
    FeatureRequestEvent fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(fe),
        isFeatureEvent(fe, flag, true)
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void twoFeatureEventsForSameUserGenerateOnlyOneIndexEvent() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
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
        isFeatureEvent(fe1, flag1, false),
        isFeatureEvent(fe2, flag2, false),
        isSummaryEvent(fe1.creationDate, fe2.creationDate)
    ));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void nonTrackedEventsAreSummarized() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
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
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    Event ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data);
    ep.sendEvent(ce);

    JsonArray output = flushAndGetEvents();
    assertThat(output, hasItems(
        isIndexEvent(ce),
        allOf(
            hasJsonProperty("kind", "custom"),
            hasJsonProperty("creationDate", (double)ce.creationDate),
            hasJsonProperty("key", "eventkey"),
            hasJsonProperty("userKey", user.getKeyAsString()),
            hasJsonProperty("data", data)
        )
    ));
  }
  
  @Test
  public void sdkKeyIsSent() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    ep.sendEvent(e);
    
    server.enqueue(new MockResponse());
    ep.flush();
    RecordedRequest req = server.takeRequest();
    
    assertThat(req.getHeader("Authorization"), equalTo(SDK_KEY));
  }
  
  private JsonArray flushAndGetEvents() throws Exception {
    server.enqueue(new MockResponse());
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

  private Matcher<JsonElement> isFeatureEvent(FeatureRequestEvent sourceEvent, FeatureFlag flag, boolean debug) {
    return allOf(
        hasJsonProperty("kind", debug ? "debug" : "feature"),
        hasJsonProperty("creationDate", (double)sourceEvent.creationDate),
        hasJsonProperty("key", flag.getKey()),
        hasJsonProperty("version", (double)flag.getVersion()),
        hasJsonProperty("value", sourceEvent.value),
        hasJsonProperty("userKey", sourceEvent.user.getKeyAsString())
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
