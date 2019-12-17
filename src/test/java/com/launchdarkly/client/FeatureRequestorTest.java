package com.launchdarkly.client;

import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLHandshakeException;

import static com.launchdarkly.client.TestHttpUtil.baseConfig;
import static com.launchdarkly.client.TestHttpUtil.httpsServerWithSelfSignedCert;
import static com.launchdarkly.client.TestHttpUtil.jsonResponse;
import static com.launchdarkly.client.TestHttpUtil.makeStartedServer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@SuppressWarnings("javadoc")
public class FeatureRequestorTest {
  private static final String sdkKey = "sdk-key";
  private static final String flag1Key = "flag1";
  private static final String flag1Json = "{\"key\":\"" + flag1Key + "\"}";
  private static final String flagsJson = "{\"" + flag1Key + "\":" + flag1Json + "}";
  private static final String segment1Key = "segment1";
  private static final String segment1Json = "{\"key\":\"" + segment1Key + "\"}";
  private static final String segmentsJson = "{\"" + segment1Key + "\":" + segment1Json + "}";
  private static final String allDataJson = "{\"flags\":" + flagsJson + ",\"segments\":" + segmentsJson + "}";
  
  @Test
  public void requestAllData() throws Exception {
    MockResponse resp = jsonResponse(allDataJson);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(sdkKey, basePollingConfig(server).build())) {
        FeatureRequestor.AllData data = r.getAllData();
        
        RecordedRequest req = server.takeRequest();
        assertEquals("/sdk/latest-all", req.getPath());
        verifyHeaders(req);
        
        assertNotNull(data);
        assertNotNull(data.flags);
        assertNotNull(data.segments);
        assertEquals(1, data.flags.size());
        assertEquals(1, data.flags.size());
        verifyFlag(data.flags.get(flag1Key), flag1Key);
        verifySegment(data.segments.get(segment1Key), segment1Key);
      }
    }
  }
  
  @Test
  public void requestFlag() throws Exception {
    MockResponse resp = jsonResponse(flag1Json);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(sdkKey, basePollingConfig(server).build())) {      
        DataModel.FeatureFlag flag = r.getFlag(flag1Key);
        
        RecordedRequest req = server.takeRequest();
        assertEquals("/sdk/latest-flags/" + flag1Key, req.getPath());
        verifyHeaders(req);
        
        verifyFlag(flag, flag1Key);
      }
    }
  }

  @Test
  public void requestSegment() throws Exception {
    MockResponse resp = jsonResponse(segment1Json);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(sdkKey, basePollingConfig(server).build())) {     
        DataModel.Segment segment = r.getSegment(segment1Key);
        
        RecordedRequest req = server.takeRequest();
        assertEquals("/sdk/latest-segments/" + segment1Key, req.getPath());
        verifyHeaders(req);
        
        verifySegment(segment, segment1Key);
      }
    }
  }
  
  @Test
  public void requestFlagNotFound() throws Exception {
    MockResponse notFoundResp = new MockResponse().setResponseCode(404);
    
    try (MockWebServer server = makeStartedServer(notFoundResp)) {
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(sdkKey, basePollingConfig(server).build())) {     
        try {
          r.getFlag(flag1Key);
          Assert.fail("expected exception");
        } catch (HttpErrorException e) {
          assertEquals(404, e.getStatus());
        }
      }
    }
  }

  @Test
  public void requestSegmentNotFound() throws Exception {
    MockResponse notFoundResp = new MockResponse().setResponseCode(404);
    
    try (MockWebServer server = makeStartedServer(notFoundResp)) {
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(sdkKey, basePollingConfig(server).build())) {      
        try {
          r.getSegment(segment1Key);
          fail("expected exception");
        } catch (HttpErrorException e) {
          assertEquals(404, e.getStatus());
        }
      }
    }
  }

  @Test
  public void requestsAreCached() throws Exception {
    MockResponse cacheableResp = jsonResponse(flag1Json)
        .setHeader("ETag", "aaa")
        .setHeader("Cache-Control", "max-age=1000");
    
    try (MockWebServer server = makeStartedServer(cacheableResp)) {
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(sdkKey, basePollingConfig(server).build())) {
        DataModel.FeatureFlag flag1a = r.getFlag(flag1Key);
        
        RecordedRequest req1 = server.takeRequest();
        assertEquals("/sdk/latest-flags/" + flag1Key, req1.getPath());
        verifyHeaders(req1);
        
        verifyFlag(flag1a, flag1Key);
        
        DataModel.FeatureFlag flag1b = r.getFlag(flag1Key);
        verifyFlag(flag1b, flag1Key);
        assertNull(server.takeRequest(0, TimeUnit.SECONDS)); // there was no second request, due to the cache hit
      }
    }
  }
  
  @Test
  public void httpClientDoesNotAllowSelfSignedCertByDefault() throws Exception {
    MockResponse resp = jsonResponse(flag1Json);
    
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(resp)) {
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(sdkKey, basePollingConfig(serverWithCert.server).build())) {
        try {
          r.getFlag(flag1Key);
          fail("expected exception");
        } catch (SSLHandshakeException e) {
        }
        
        assertEquals(0, serverWithCert.server.getRequestCount());
      }
    }
  }
  
  @Test
  public void httpClientCanUseCustomTlsConfig() throws Exception {
    MockResponse resp = jsonResponse(flag1Json);
    
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(resp)) {
      LDConfig config = basePollingConfig(serverWithCert.server)
          .sslSocketFactory(serverWithCert.sslClient.socketFactory, serverWithCert.sslClient.trustManager) // allows us to trust the self-signed cert
          .build();

      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(sdkKey, config)) {
        DataModel.FeatureFlag flag = r.getFlag(flag1Key);
        verifyFlag(flag, flag1Key);
      }
    }
  }
  
  @Test
  public void httpClientCanUseProxyConfig() throws Exception {
    URI fakeBaseUri = URI.create("http://not-a-real-host");
    try (MockWebServer server = makeStartedServer(jsonResponse(flag1Json))) {
      HttpUrl serverUrl = server.url("/");
      LDConfig config = new LDConfig.Builder()
          .baseURI(fakeBaseUri)
          .proxyHost(serverUrl.host())
          .proxyPort(serverUrl.port())
          .build();
      
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(sdkKey, config)) {
        DataModel.FeatureFlag flag = r.getFlag(flag1Key);
        verifyFlag(flag, flag1Key);
        
        assertEquals(1, server.getRequestCount());
      }
    }
  }
  
  private LDConfig.Builder basePollingConfig(MockWebServer server) {
    return baseConfig(server)
        .stream(false);
  }
  
  private void verifyHeaders(RecordedRequest req) {
    assertEquals(sdkKey, req.getHeader("Authorization"));
    assertEquals("JavaClient/" + LDClient.CLIENT_VERSION, req.getHeader("User-Agent"));
  }
  
  private void verifyFlag(DataModel.FeatureFlag flag, String key) {
    assertNotNull(flag);
    assertEquals(key, flag.getKey());
  }
  
  private void verifySegment(DataModel.Segment segment, String key) {
    assertNotNull(segment);
    assertEquals(key, segment.getKey());
  }
}
