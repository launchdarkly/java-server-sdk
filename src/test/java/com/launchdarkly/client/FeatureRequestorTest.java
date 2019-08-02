package com.launchdarkly.client;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.launchdarkly.client.TestHttpUtil.baseConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

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
    MockResponse resp = new MockResponse();
    resp.setHeader("Content-Type", "application/json");
    resp.setBody(allDataJson);
    
    try (MockWebServer server = TestHttpUtil.makeStartedServer(resp)) {
      FeatureRequestor r = new FeatureRequestor(sdkKey, basePollingConfig(server).build());
      
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
  
  @Test
  public void requestFlag() throws Exception {
    MockResponse resp = new MockResponse();
    resp.setHeader("Content-Type", "application/json");
    resp.setBody(flag1Json);
    
    try (MockWebServer server = TestHttpUtil.makeStartedServer(resp)) {
      FeatureRequestor r = new FeatureRequestor(sdkKey, basePollingConfig(server).build());
      
      FeatureFlag flag = r.getFlag(flag1Key);
      
      RecordedRequest req = server.takeRequest();
      assertEquals("/sdk/latest-flags/" + flag1Key, req.getPath());
      verifyHeaders(req);
      
      verifyFlag(flag, flag1Key);
    }
  }

  @Test
  public void requestSegment() throws Exception {
    MockResponse resp = new MockResponse();
    resp.setHeader("Content-Type", "application/json");
    resp.setBody(segment1Json);
    
    try (MockWebServer server = TestHttpUtil.makeStartedServer(resp)) {
      FeatureRequestor r = new FeatureRequestor(sdkKey, basePollingConfig(server).build());
      
      Segment segment = r.getSegment(segment1Key);
      
      RecordedRequest req = server.takeRequest();
      assertEquals("/sdk/latest-segments/" + segment1Key, req.getPath());
      verifyHeaders(req);
      
      verifySegment(segment, segment1Key);
    }
  }
  
  @Test
  public void requestFlagNotFound() throws Exception {
    MockResponse notFoundResp = new MockResponse().setResponseCode(404);
    
    try (MockWebServer server = TestHttpUtil.makeStartedServer(notFoundResp)) {
      FeatureRequestor r = new FeatureRequestor(sdkKey, basePollingConfig(server).build());
      
      try {
        r.getFlag(flag1Key);
        Assert.fail("expected exception");
      } catch (HttpErrorException e) {
        assertEquals(404, e.getStatus());
      }
    }
  }

  @Test
  public void requestSegmentNotFound() throws Exception {
    MockResponse notFoundResp = new MockResponse().setResponseCode(404);
    
    try (MockWebServer server = TestHttpUtil.makeStartedServer(notFoundResp)) {
      FeatureRequestor r = new FeatureRequestor(sdkKey, basePollingConfig(server).build());
      
      try {
        r.getSegment(segment1Key);
        Assert.fail("expected exception");
      } catch (HttpErrorException e) {
        assertEquals(404, e.getStatus());
      }
    }
  }

  @Test
  public void requestsAreCached() throws Exception {
    MockResponse cacheableResp = new MockResponse();
    cacheableResp.setHeader("Content-Type", "application/json");
    cacheableResp.setHeader("ETag", "aaa");
    cacheableResp.setHeader("Cache-Control", "max-age=1000");
    cacheableResp.setBody(flag1Json);
    
    try (MockWebServer server = TestHttpUtil.makeStartedServer(cacheableResp)) {
      FeatureRequestor r = new FeatureRequestor(sdkKey, basePollingConfig(server).build());
      
      FeatureFlag flag1a = r.getFlag(flag1Key);
      
      RecordedRequest req1 = server.takeRequest();
      assertEquals("/sdk/latest-flags/" + flag1Key, req1.getPath());
      verifyHeaders(req1);
      
      verifyFlag(flag1a, flag1Key);
      
      FeatureFlag flag1b = r.getFlag(flag1Key);
      verifyFlag(flag1b, flag1Key);
      assertNull(server.takeRequest(0, TimeUnit.SECONDS)); // there was no second request, due to the cache hit
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
  
  private void verifyFlag(FeatureFlag flag, String key) {
    assertNotNull(flag);
    assertEquals(key, flag.getKey());
  }
  
  private void verifySegment(Segment segment, String key) {
    assertNotNull(segment);
    assertEquals(key, segment.getKey());
  }
}
