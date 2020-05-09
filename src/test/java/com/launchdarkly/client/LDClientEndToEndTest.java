package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import static com.launchdarkly.client.Components.externalUpdatesOnly;
import static com.launchdarkly.client.Components.noEvents;
import static com.launchdarkly.client.TestHttpUtil.basePollingConfig;
import static com.launchdarkly.client.TestHttpUtil.baseStreamingConfig;
import static com.launchdarkly.client.TestHttpUtil.httpsServerWithSelfSignedCert;
import static com.launchdarkly.client.TestHttpUtil.jsonResponse;
import static com.launchdarkly.client.TestHttpUtil.makeStartedServer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@SuppressWarnings("javadoc")
public class LDClientEndToEndTest {
  private static final Gson gson = new Gson();
  private static final String sdkKey = "sdk-key";
  private static final String flagKey = "flag1";
  private static final FeatureFlag flag = new FeatureFlagBuilder(flagKey)
      .offVariation(0).variations(LDValue.of(true))
      .build();
  private static final LDUser user = new LDUser("user-key");
  
  @Test
  public void clientStartsInPollingMode() throws Exception {
    MockResponse resp = jsonResponse(makeAllDataJson());
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(basePollingConfig(server))
          .events(noEvents())
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
      LDConfig config = new LDConfig.Builder()
          .dataSource(basePollingConfig(server))
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertFalse(client.initialized());
        assertFalse(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientStartsInPollingModeWithSelfSignedCert() throws Exception {
    MockResponse resp = jsonResponse(makeAllDataJson());
    
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(basePollingConfig(serverWithCert.server))
          .events(noEvents())
          .http(Components.httpConfiguration().sslSocketFactory(serverWithCert.socketFactory, serverWithCert.trustManager))
          // allows us to trust the self-signed cert
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientStartsInStreamingMode() throws Exception {
    String streamData = "event: put\n" +
        "data: {\"data\":" + makeAllDataJson() + "}\n\n";    
    MockResponse resp = TestHttpUtil.eventStreamResponse(streamData);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(baseStreamingConfig(server))
          .events(noEvents())
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
      LDConfig config = new LDConfig.Builder()
          .dataSource(baseStreamingConfig(server))
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertFalse(client.initialized());
        assertFalse(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientStartsInStreamingModeWithSelfSignedCert() throws Exception {
    String streamData = "event: put\n" +
        "data: {\"data\":" + makeAllDataJson() + "}\n\n";    
    MockResponse resp = TestHttpUtil.eventStreamResponse(streamData);
    
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(baseStreamingConfig(serverWithCert.server))
          .events(noEvents())
          .http(Components.httpConfiguration().sslSocketFactory(serverWithCert.socketFactory, serverWithCert.trustManager))
          // allows us to trust the self-signed cert
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientSendsAnalyticsEvent() throws Exception {
    MockResponse resp = new MockResponse().setResponseCode(202);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(externalUpdatesOnly())
          .events(Components.sendEvents().baseURI(server.url("/").uri()))
          .diagnosticOptOut(true)
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
        client.identify(new LDUser("userkey"));
      }
      
      RecordedRequest req = server.takeRequest();
      assertEquals("/bulk", req.getPath());
    }
  }

  @Test
  public void clientSendsDiagnosticEvent() throws Exception {
    MockResponse resp = new MockResponse().setResponseCode(202);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(externalUpdatesOnly())
          .events(Components.sendEvents().baseURI(server.url("/").uri()))
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
      }
      
      RecordedRequest req = server.takeRequest();
      assertEquals("/diagnostic", req.getPath());
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
