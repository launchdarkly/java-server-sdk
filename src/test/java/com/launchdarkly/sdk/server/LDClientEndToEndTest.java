package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.testhelpers.httptest.Handler;
import com.launchdarkly.testhelpers.httptest.Handlers;
import com.launchdarkly.testhelpers.httptest.HttpServer;
import com.launchdarkly.testhelpers.httptest.RequestInfo;
import com.launchdarkly.testhelpers.httptest.SpecialHttpConfigurations;

import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.launchdarkly.sdk.server.Components.externalUpdatesOnly;
import static com.launchdarkly.sdk.server.Components.noEvents;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.testhelpers.httptest.Handlers.bodyJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientEndToEndTest extends BaseTest {
  private static final Gson gson = new Gson();
  private static final String sdkKey = "sdk-key";
  private static final String flagKey = "flag1";
  private static final DataModel.FeatureFlag flag = flagBuilder(flagKey)
      .offVariation(0).variations(LDValue.of(true))
      .build();
  private static final LDContext user = LDContext.create("user-key");
  
  private static Handler makePollingSuccessResponse() {
    return bodyJson(makeAllDataJson());
  }
  
  private static Handler makeStreamingSuccessResponse() {
    String streamData = "event: put\n" +
        "data: {\"data\":" + makeAllDataJson() + "}";
    return Handlers.all(Handlers.SSE.start(),
        Handlers.SSE.event(streamData), Handlers.SSE.leaveOpen());
  }
  
  private static Handler makeInvalidSdkKeyResponse() {
    return Handlers.status(401);
  }
  
  private static Handler makeServiceUnavailableResponse() {
    return Handlers.status(503);
  }
  
  @Test
  public void clientStartsInPollingMode() throws Exception {
    try (HttpServer server = HttpServer.start(makePollingSuccessResponse())) {
      LDConfig config = baseConfig()
          .serviceEndpoints(Components.serviceEndpoints().polling(server.getUri()))
          .dataSource(Components.pollingDataSource())
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.isInitialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientStartsInPollingModeAfterRecoverableError() throws Exception {
    Handler errorThenSuccess = Handlers.sequential(
        makeServiceUnavailableResponse(),
        makePollingSuccessResponse()
        );

    try (HttpServer server = HttpServer.start(errorThenSuccess)) {
      LDConfig config = baseConfig()
          .serviceEndpoints(Components.serviceEndpoints().polling(server.getUri()))
          .dataSource(Components.pollingDataSourceInternal()
              .pollIntervalWithNoMinimum(Duration.ofMillis(5))) // use small interval because we expect it to retry
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
    try (HttpServer server = HttpServer.start(makeInvalidSdkKeyResponse())) {
      LDConfig config = baseConfig()
          .serviceEndpoints(Components.serviceEndpoints().polling(server.getUri()))
          .dataSource(Components.pollingDataSourceInternal()
              .pollIntervalWithNoMinimum(Duration.ofMillis(5))) // use small interval so we'll know if it does not stop permanently
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertFalse(client.isInitialized());
        assertFalse(client.boolVariation(flagKey, user, false));
        
        server.getRecorder().requireRequest();
        server.getRecorder().requireNoRequests(100, TimeUnit.MILLISECONDS);
      }
    }
  }

  @Test
  public void testPollingModeSpecialHttpConfigurations() throws Exception {
    testWithSpecialHttpConfigurations(
        makePollingSuccessResponse(),
        (serverUri, httpConfig) ->
          baseConfig()
            .serviceEndpoints(Components.serviceEndpoints().polling(serverUri))
            .dataSource(Components.pollingDataSource())
            .events(noEvents())
            .http(httpConfig));
  }
  
  @Test
  public void clientStartsInStreamingMode() throws Exception {    
    try (HttpServer server = HttpServer.start(makeStreamingSuccessResponse())) {
      LDConfig config = baseConfig()
          .dataSource(Components.streamingDataSource())
          .serviceEndpoints(Components.serviceEndpoints().streaming(server.getUri()))
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
    Handler errorThenStream = Handlers.sequential(
        makeServiceUnavailableResponse(),
        makeStreamingSuccessResponse()
        );
    
    try (HttpServer server = HttpServer.start(errorThenStream)) {
      LDConfig config = baseConfig()
          .serviceEndpoints(Components.serviceEndpoints().streaming(server.getUri()))
          .dataSource(Components.streamingDataSource().initialReconnectDelay(Duration.ZERO))
          // use zero reconnect delay so we'll know if it does not stop permanently
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.isInitialized());
        assertTrue(client.boolVariation(flagKey, user, false));
        
        server.getRecorder().requireRequest();
        server.getRecorder().requireRequest();
        server.getRecorder().requireNoRequests(100, TimeUnit.MILLISECONDS);
      }
    }
  }

  @Test
  public void clientFailsInStreamingModeWith401Error() throws Exception {
    try (HttpServer server = HttpServer.start(makeInvalidSdkKeyResponse())) {
      LDConfig config = baseConfig()
          .serviceEndpoints(Components.serviceEndpoints().streaming(server.getUri()))
          .dataSource(Components.streamingDataSource().initialReconnectDelay(Duration.ZERO))
          // use zero reconnect delay so we'll know if it does not stop permanently
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
        
        server.getRecorder().requireRequest();
        server.getRecorder().requireNoRequests(100, TimeUnit.MILLISECONDS);
      }
    }
  }

  @Test
  public void testStreamingModeSpecialHttpConfigurations() throws Exception {
    testWithSpecialHttpConfigurations(
        makeStreamingSuccessResponse(),
        (serverUri, httpConfig) ->
          baseConfig()
            .serviceEndpoints(Components.serviceEndpoints().streaming(serverUri))
            .dataSource(Components.streamingDataSource())
            .events(noEvents())
            .http(httpConfig));
  }
  
  @Test
  public void clientSendsAnalyticsEvent() throws Exception {
    Handler resp = Handlers.status(202);
    
    try (HttpServer server = HttpServer.start(resp)) {
      LDConfig config = baseConfig()
          .serviceEndpoints(Components.serviceEndpoints().events(server.getUri()))
          .dataSource(externalUpdatesOnly())
          .diagnosticOptOut(true)
          .events(Components.sendEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.isInitialized());
        client.identify(user);
      }
      
      RequestInfo req = server.getRecorder().requireRequest();
      assertEquals("/bulk", req.getPath());
    }
  }

  @Test
  public void clientSendsDiagnosticEvent() throws Exception {
    Handler resp = Handlers.status(202);
    
    try (HttpServer server = HttpServer.start(resp)) {
      LDConfig config = baseConfig()
          .serviceEndpoints(Components.serviceEndpoints().events(server.getUri()))
          .dataSource(externalUpdatesOnly())
          .events(Components.sendEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.isInitialized());

        RequestInfo req = server.getRecorder().requireRequest();
        assertEquals("/diagnostic", req.getPath());
      }      
    }
  }

  private static void testWithSpecialHttpConfigurations(Handler handler,
      BiFunction<URI, ComponentConfigurer<HttpConfiguration>, LDConfig.Builder> makeConfig) throws Exception {
    SpecialHttpConfigurations.testAll(handler,
        (URI serverUri, SpecialHttpConfigurations.Params params) -> {
          LDConfig config = makeConfig.apply(serverUri, TestUtil.makeHttpConfigurationFromTestParams(params))
              .startWait(Duration.ofSeconds(10)) // allow extra time to be sure it can connect
              .build();
          try (LDClient client = new LDClient(sdkKey, config)) {
            if (!client.isInitialized()) {
              throw new IOException("client did not initialize successfully");
            }
            if (!client.boolVariation(flagKey, user, false)) {
              throw new IOException("client said it initialized, but did not have correct flag data");
            }
          }
          return true;
        }
        );
  }

  private static String makeAllDataJson() {
    JsonObject flagsData = new JsonObject();
    flagsData.add(flagKey, gson.toJsonTree(flag));
    JsonObject allData = new JsonObject();
    allData.add("flags", flagsData);
    allData.add("segments", new JsonObject());
    return gson.toJson(allData);
  }
}
