package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class EventProcessorTest {
  private static final String SDK_KEY = "SDK_KEY";
  private static final LDUser user = new LDUser.Builder("userkey").name("Red").build();
  private static final Gson gson = new Gson();
  private static final Type listOfMapsType = new TypeToken<List<Map<String, JsonElement>>>() {
  }.getType();

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
    
    List<Map<String, JsonElement>> output = flushAndGetEvents();
    assertEquals(1, output.size());
    Map<String, JsonElement> ieo = output.get(0);
    assertEquals(new JsonPrimitive("identify"), ieo.get("kind"));
    assertEquals(new JsonPrimitive((double)e.creationDate), ieo.get("creationDate"));
    assertUserMatches(user, ieo.get("user"));
  }
  
  @Test
  public void testIndividualFeatureEventIsQueuedWithIndexEvent() throws Exception {
    ep = new EventProcessor(SDK_KEY, configBuilder.build());
    FeatureFlag flag = new FeatureFlagBuilder("flagkey").version(11).trackEvents(true).build();
    Event fe = EventFactory.DEFAULT.newFeatureRequestEvent(flag, user,
        new FeatureFlag.VariationAndValue(new Integer(1), new JsonPrimitive("value")), null);
    ep.sendEvent(fe);
    
    List<Map<String, JsonElement>> output = flushAndGetEvents();
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
    
    List<Map<String, JsonElement>> output = flushAndGetEvents();
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
    
    List<Map<String, JsonElement>> output = flushAndGetEvents();
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
    
    List<Map<String, JsonElement>> output = flushAndGetEvents();
    assertEquals(2, output.size());
    assertIndexEventMatches(output.get(0), fe1);
    Map<String, JsonElement> seo = output.get(1);
    assertEquals(new JsonPrimitive("summary"), seo.get("kind"));
    assertEquals(new JsonPrimitive((double)fe1.creationDate), seo.get("startDate"));
    assertEquals(new JsonPrimitive((double)fe2.creationDate), seo.get("endDate"));
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

    List<Map<String, JsonElement>> output = flushAndGetEvents();
    assertEquals(2, output.size());
    assertIndexEventMatches(output.get(0), ce);
    Map<String, JsonElement> ceo = output.get(1);
    assertEquals(new JsonPrimitive("custom"), ceo.get("kind"));
    assertEquals(new JsonPrimitive((double)ce.creationDate), ceo.get("creationDate"));
    assertEquals(new JsonPrimitive("eventkey"), ceo.get("key"));
    assertEquals(new JsonPrimitive(user.getKeyAsString()), ceo.get("userKey"));
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
  
  private List<Map<String, JsonElement>> flushAndGetEvents() throws Exception {
    server.enqueue(new MockResponse());
    ep.flush();
    RecordedRequest req = server.takeRequest();
    return gson.fromJson(req.getBody().readUtf8(), listOfMapsType);
  }
  
  private void assertUserMatches(LDUser user, JsonElement userJson) {
    assertEquals(configBuilder.build().gson.toJsonTree(user), userJson);
  }

  private void assertIndexEventMatches(Map<String, JsonElement> eventOutput, Event sourceEvent) {
    assertEquals(new JsonPrimitive("index"), eventOutput.get("kind"));
    assertEquals(new JsonPrimitive((double)sourceEvent.creationDate), eventOutput.get("creationDate"));
    assertUserMatches(sourceEvent.user, eventOutput.get("user"));
  }
  
  private void assertFeatureEventMatches(Map<String, JsonElement> eventOutput, Event sourceEvent, FeatureFlag flag, boolean debug) {
    assertEquals(new JsonPrimitive("feature"), eventOutput.get("kind"));
    assertEquals(new JsonPrimitive((double)sourceEvent.creationDate), eventOutput.get("creationDate"));
    assertEquals(new JsonPrimitive(flag.getKey()), eventOutput.get("key"));
    assertEquals(new JsonPrimitive((double)flag.getVersion()), eventOutput.get("version"));
    assertEquals(new JsonPrimitive("value"), eventOutput.get("value"));
    assertEquals(new JsonPrimitive(sourceEvent.user.getKeyAsString()), eventOutput.get("userKey"));
    if (debug) {
      assertEquals(new JsonPrimitive(true), eventOutput.get("debug"));
    } else {
      assertNull(eventOutput.get("debug"));
    }
  }
}
