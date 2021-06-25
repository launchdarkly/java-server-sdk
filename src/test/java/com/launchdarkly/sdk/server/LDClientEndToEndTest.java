package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;

import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.launchdarkly.sdk.server.Components.externalUpdatesOnly;
import static com.launchdarkly.sdk.server.Components.noEvents;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestHttpUtil.basePollingConfig;
import static com.launchdarkly.sdk.server.TestHttpUtil.baseStreamingConfig;
import static com.launchdarkly.sdk.server.TestHttpUtil.httpsServerWithSelfSignedCert;
import static com.launchdarkly.sdk.server.TestHttpUtil.jsonResponse;
import static com.launchdarkly.sdk.server.TestHttpUtil.makeStartedServer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@SuppressWarnings("javadoc")
public class LDClientEndToEndTest {
  private static final Gson gson = new Gson();
  private static final String sdkKey = "sdk-key";
  private static final String flagKey = "flag1";
  private static final DataModel.FeatureFlag flag = flagBuilder(flagKey)
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
        assertTrue(client.isInitialized());
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
        assertFalse(client.isInitialized());
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
        assertTrue(client.isInitialized());
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
        assertTrue(client.isInitialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientStartsInStreamingModeAfterRecoverableError() throws Exception {
    MockResponse errorResp = new MockResponse().setResponseCode(503);

    String streamData = "event: put\n" +
        "data: {\"data\":" + makeAllDataJson() + "}\n\n";    
    MockResponse streamResp = TestHttpUtil.eventStreamResponse(streamData);
    
    try (MockWebServer server = makeStartedServer(errorResp, streamResp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(baseStreamingConfig(server))
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.isInitialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientFailsInStreamingModeWith401Error() throws Exception {
    MockResponse resp = new MockResponse().setResponseCode(401);
    
    try (MockWebServer server = makeStartedServer(resp, resp, resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(baseStreamingConfig(server).initialReconnectDelay(Duration.ZERO))
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertFalse(client.isInitialized());
        assertFalse(client.boolVariation(flagKey, user, false));
        
        BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
        client.getDataSourceStatusProvider().addStatusListener(statuses::add);

        Thread.sleep(100); // make sure it didn't retry the connection
        assertThat(client.getDataSourceStatusProvider().getStatus().getState(),
            equalTo(DataSourceStatusProvider.State.OFF));
        while (!statuses.isEmpty()) {
          // The status listener may or may not have been registered early enough to receive
          // the OFF notification, but we should at least not see any *other* statuses.
          assertThat(statuses.take().getState(), equalTo(DataSourceStatusProvider.State.OFF)); 
        }
        assertThat(statuses.isEmpty(), equalTo(true));
        assertThat(server.getRequestCount(), equalTo(1)); // no retries
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
        assertTrue(client.isInitialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientUsesProxy() throws Exception {
    URI fakeBaseUri = URI.create("http://not-a-real-host");
    MockResponse resp = jsonResponse(makeAllDataJson());
    
    try (MockWebServer server = makeStartedServer(resp)) {
      HttpUrl serverUrl = server.url("/");
      LDConfig config = new LDConfig.Builder()
          .http(Components.httpConfiguration()
              .proxyHostAndPort(serverUrl.host(), serverUrl.port()))
          .dataSource(Components.pollingDataSource().baseURI(fakeBaseUri))
          .events(Components.noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.isInitialized());
        
        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestLine(), startsWith("GET " + fakeBaseUri + "/sdk/latest-all"));
        assertThat(req.getHeader("Proxy-Authorization"), nullValue());
      }
    }
  }

  @Test
  public void clientUsesProxyWithBasicAuth() throws Exception {
    URI fakeBaseUri = URI.create("http://not-a-real-host");
    MockResponse challengeResp = new MockResponse().setResponseCode(407).setHeader("Proxy-Authenticate", "Basic realm=x");
    MockResponse resp = jsonResponse(makeAllDataJson());
    
    try (MockWebServer server = makeStartedServer(challengeResp, resp)) {
      HttpUrl serverUrl = server.url("/");
      LDConfig config = new LDConfig.Builder()
          .http(Components.httpConfiguration()
              .proxyHostAndPort(serverUrl.host(), serverUrl.port())
              .proxyAuth(Components.httpBasicAuthentication("user", "pass")))
          .dataSource(Components.pollingDataSource().baseURI(fakeBaseUri))
          .events(Components.noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.isInitialized());
        
        RecordedRequest req1 = server.takeRequest();
        assertThat(req1.getRequestLine(), startsWith("GET " + fakeBaseUri + "/sdk/latest-all"));
        assertThat(req1.getHeader("Proxy-Authorization"), nullValue());
        
        RecordedRequest req2 = server.takeRequest();
        assertThat(req2.getRequestLine(), equalTo(req1.getRequestLine()));
        assertThat(req2.getHeader("Proxy-Authorization"), equalTo("Basic dXNlcjpwYXNz"));
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
        assertTrue(client.isInitialized());
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
        assertTrue(client.isInitialized());

        RecordedRequest req = server.takeRequest();
        assertEquals("/diagnostic", req.getPath());
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
