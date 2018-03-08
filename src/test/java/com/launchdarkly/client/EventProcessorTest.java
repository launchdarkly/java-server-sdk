package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
  
  @Test
  public void testIdentifyEventIsQueued() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    ep.sendEvent(e);
    
    JsonArray output = flushAndGetEvents();
    assertEquals(1, output.size());
    JsonObject expected = new JsonObject();
    expected.addProperty("kind", "identify");
    expected.addProperty("creationDate", e.creationDate);
    expected.add("user", makeUserJson(user));
    assertEquals(expected, output.get(0));
  }
  
  @Test
  public void testIndividualFeatureEventIsQueuedWithIndexEvent() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents();
    assertEquals(2, output.size());
    assertIndexEventMatches(output.get(0), fe);
    assertFeatureEventMatches(output.get(1), fe, flag, false);
  }

  @Test
  public void testDebugFlagIsSetIfFlagIsTemporarilyInDebugMode() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    long futureTime = System.currentTimeMillis() + 1000000;
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).debugEventsUntilDate(futureTime).build();
    Event fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    JsonArray output = flushAndGetEvents();
    assertEquals(2, output.size());
    assertIndexEventMatches(output.get(0), fe);
    assertFeatureEventMatches(output.get(1), fe, flag, true);
  }
  
  @Test
  public void testTwoFeatureEventsForSameUserGenerateOnlyOneIndexEvent() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag1 = new FeatureFlagBuilder("flagkey1").version(11).trackEvents(true).build();
    FeatureFlag flag2 = new FeatureFlagBuilder("flagkey2").version(22).trackEvents(true).build();
    Event fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    Event fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe1);
    ep.sendEvent(fe2);
    
    JsonArray output = flushAndGetEvents();
    assertEquals(3, output.size());
    assertIndexEventMatches(output.get(0), fe1);
    assertFeatureEventMatches(output.get(1), fe1, flag1, false);    
    assertFeatureEventMatches(output.get(2), fe2, flag2, false);
  }
  
  @Test
  public void nonTrackedEventsAreSummarized() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag1 = new FeatureFlagBuilder("flagkey1").version(11).build();
    FeatureFlag flag2 = new FeatureFlagBuilder("flagkey2").version(22).build();
    Event fe1 = EventFactory.DEFAULT.newFeatureRequestEvent(flag1, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    Event fe2 = EventFactory.DEFAULT.newFeatureRequestEvent(flag2, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe1);
    ep.sendEvent(fe2);
    
    JsonArray output = flushAndGetEvents();
    assertEquals(2, output.size());
    assertIndexEventMatches(output.get(0), fe1);
    
    JsonObject seo = output.get(1).getAsJsonObject();
    assertEquals("summary", seo.get("kind").getAsString());
    assertEquals(fe1.creationDate, seo.get("startDate").getAsLong());
    assertEquals(fe2.creationDate, seo.get("endDate").getAsLong());
    JsonArray counters = seo.get("counters").getAsJsonArray();
    assertEquals(2, counters.size());
  }
  
  @Test
  public void customEventIsQueuedWithUser() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    JsonObject data = new JsonObject();
    data.addProperty("thing", "stuff");
    Event ce = EventFactory.DEFAULT.newCustomEvent("eventkey", user, data);
    ep.sendEvent(ce);

    JsonArray output = flushAndGetEvents();
    assertEquals(2, output.size());
    assertIndexEventMatches(output.get(0), ce);
    
    JsonObject expected = new JsonObject();
    expected.addProperty("kind", "custom");
    expected.addProperty("creationDate", ce.creationDate);
    expected.addProperty("key", "eventkey");
    expected.addProperty("userKey", user.getKeyAsString());
    expected.add("data", data);
    assertEquals(expected, output.get(1));
  }
  
  @Test
  public void sdkKeyIsSent() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    Event e = EventFactory.DEFAULT.newIdentifyEvent(user);
    ep.sendEvent(e);
    
    server.enqueue(new MockResponse());
    ep.flush();
    RecordedRequest req = server.takeRequest();
    
    assertEquals(SDK_KEY, req.getHeader("Authorization"));
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
  
  private void assertUserMatches(LDUser user, JsonElement userJson) {
    assertEquals(makeUserJson(user), userJson);
  }

  private void assertIndexEventMatches(JsonElement eventOutput, Event sourceEvent) {
    JsonObject o = eventOutput.getAsJsonObject();
    assertEquals("index", o.get("kind").getAsString());
    assertEquals(sourceEvent.creationDate, o.get("creationDate").getAsLong());
    assertUserMatches(sourceEvent.user, o.get("user"));
  }
  
  private void assertFeatureEventMatches(JsonElement eventOutput, Event sourceEvent, FeatureFlag flag, boolean debug) {
    JsonObject o = eventOutput.getAsJsonObject();
    assertEquals("feature", o.get("kind").getAsString());
    assertEquals(sourceEvent.creationDate, o.get("creationDate").getAsLong());
    assertEquals(flag.getKey(), o.get("key").getAsString());
    assertEquals(flag.getVersion(), o.get("version").getAsInt());
    assertEquals("value", o.get("value").getAsString());
    assertEquals(sourceEvent.user.getKeyAsString(), o.get("userKey").getAsString());
    if (debug) {
      assertTrue(o.get("debug").getAsBoolean());
    } else {
      assertNull(o.get("debug"));
    }
  }
}
