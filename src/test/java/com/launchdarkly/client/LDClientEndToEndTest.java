package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.launchdarkly.client.TestHttpUtil.baseConfig;
import static com.launchdarkly.client.TestHttpUtil.makeStartedServer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

public class LDClientEndToEndTest {
  private static final Gson gson = new Gson();
  private static final String sdkKey = "sdk-key";
  private static final String flagKey = "flag1";
  private static final FeatureFlag flag = new FeatureFlagBuilder(flagKey)
      .offVariation(0).variations(new JsonPrimitive(true))
      .build();
  private static final LDUser user = new LDUser("user-key");
  
  @Test
  public void clientStartsInPollingMode() throws Exception {
    MockResponse resp = new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(makeAllDataJson());
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = baseConfig(server)
          .stream(false)
          .sendEvents(false)
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientFailsInPollingModeWith401Error() throws Exception {
    MockResponse resp = new MockResponse().setResponseCode(401);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = baseConfig(server)
          .stream(false)
          .sendEvents(false)
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertFalse(client.initialized());
        assertFalse(client.boolVariation(flagKey, user, false));
      }
    }
  }
  
  @Test
  public void clientStartsInStreamingMode() throws Exception {
    String eventData = "event: put\n" +
        "data: {\"data\":" + makeAllDataJson() + "}\n\n";
    
    MockResponse resp = new MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setChunkedBody(eventData, 1000)
        .setSocketPolicy(SocketPolicy.KEEP_OPEN);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = baseConfig(server)
          .sendEvents(false)
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientFailsInStreamingModeWith401Error() throws Exception {
    MockResponse resp = new MockResponse().setResponseCode(401);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = baseConfig(server)
          .sendEvents(false)
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertFalse(client.initialized());
        assertFalse(client.boolVariation(flagKey, user, false));
      }
    }
  }
  
  public String makeAllDataJson() {
    JsonObject flagsData = new JsonObject();
    flagsData.add(flagKey, gson.toJsonTree(flag));
    JsonObject allData = new JsonObject();
    allData.add("flags", flagsData);
    allData.add("segments", new JsonObject());
    return gson.toJson(allData);
  }
}
