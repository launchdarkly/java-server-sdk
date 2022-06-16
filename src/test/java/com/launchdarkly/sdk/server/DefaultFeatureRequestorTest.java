package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.testhelpers.httptest.Handler;
import com.launchdarkly.testhelpers.httptest.Handlers;
import com.launchdarkly.testhelpers.httptest.HttpServer;
import com.launchdarkly.testhelpers.httptest.RequestInfo;

import org.junit.Test;

import java.net.URI;
import java.util.Map;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class DefaultFeatureRequestorTest {
  private static final String sdkKey = "sdk-key";
  private static final String flag1Key = "flag1";
  private static final String flag1Json = "{\"key\":\"" + flag1Key + "\"}";
  private static final String flagsJson = "{\"" + flag1Key + "\":" + flag1Json + "}";
  private static final String segment1Key = "segment1";
  private static final String segment1Json = "{\"key\":\"" + segment1Key + "\"}";
  private static final String segmentsJson = "{\"" + segment1Key + "\":" + segment1Json + "}";
  private static final String allDataJson = "{\"flags\":" + flagsJson + ",\"segments\":" + segmentsJson + "}";
  
  private DefaultFeatureRequestor makeRequestor(HttpServer server) {
    return makeRequestor(server, LDConfig.DEFAULT);
    // We can always use LDConfig.DEFAULT unless we need to modify HTTP properties, since DefaultFeatureRequestor
    // no longer uses the deprecated LDConfig.baseUri property. 
  }

  private DefaultFeatureRequestor makeRequestor(HttpServer server, LDConfig config) {
    return new DefaultFeatureRequestor(makeHttpConfig(config), server.getUri());
  }

  private HttpConfiguration makeHttpConfig(LDConfig config) {
    return config.httpConfigFactory.createHttpConfiguration(new ClientContext(sdkKey));
  }

  private void verifyExpectedData(FeatureRequestor.AllData data) {
    assertNotNull(data);
    assertNotNull(data.flags);
    assertNotNull(data.segments);
    assertEquals(1, data.flags.size());
    assertEquals(1, data.flags.size());
    verifyFlag(data.flags.get(flag1Key), flag1Key);
    verifySegment(data.segments.get(segment1Key), segment1Key);
  }
  
  @Test
  public void requestAllData() throws Exception {
    Handler resp = Handlers.bodyJson(allDataJson);
    
    try (HttpServer server = HttpServer.start(resp)) {
      try (DefaultFeatureRequestor r = makeRequestor(server)) {
        FeatureRequestor.AllData data = r.getAllData(true);
        
        RequestInfo req = server.getRecorder().requireRequest();
        assertEquals("/sdk/latest-all", req.getPath());
        verifyHeaders(req);
        
        verifyExpectedData(data);
      }
    }
  }
  
  @Test
  public void responseIsCached() throws Exception {
    Handler cacheableResp = Handlers.all(
        Handlers.header("ETag", "aaa"),
        Handlers.header("Cache-Control", "max-age=0"),
        Handlers.bodyJson(allDataJson)
        );
    Handler cachedResp = Handlers.status(304);
    Handler cacheableThenCached = Handlers.sequential(cacheableResp, cachedResp); 
    
    try (HttpServer server = HttpServer.start(cacheableThenCached)) {
      try (DefaultFeatureRequestor r = makeRequestor(server)) {
        FeatureRequestor.AllData data1 = r.getAllData(true);
        verifyExpectedData(data1);
         
        RequestInfo req1 = server.getRecorder().requireRequest();
        assertEquals("/sdk/latest-all", req1.getPath());
        verifyHeaders(req1);
        assertNull(req1.getHeader("If-None-Match"));
         
        FeatureRequestor.AllData data2 = r.getAllData(false);
        assertNull(data2);

        RequestInfo req2 = server.getRecorder().requireRequest();
        assertEquals("/sdk/latest-all", req2.getPath());
        verifyHeaders(req2);
        assertEquals("aaa", req2.getHeader("If-None-Match"));
      }
    }
  }

  @Test
  public void responseIsCachedButWeWantDataAnyway() throws Exception {
    Handler cacheableResp = Handlers.all(
        Handlers.header("ETag", "aaa"),
        Handlers.header("Cache-Control", "max-age=0"),
        Handlers.bodyJson(allDataJson)
        );
    Handler cachedResp = Handlers.status(304);
    Handler cacheableThenCached = Handlers.sequential(cacheableResp, cachedResp); 
    
    try (HttpServer server = HttpServer.start(cacheableThenCached)) {
      try (DefaultFeatureRequestor r = makeRequestor(server)) {
        FeatureRequestor.AllData data1 = r.getAllData(true);
        verifyExpectedData(data1);
         
        RequestInfo req1 = server.getRecorder().requireRequest();
        assertEquals("/sdk/latest-all", req1.getPath());
        verifyHeaders(req1);
        assertNull(req1.getHeader("If-None-Match"));
         
        FeatureRequestor.AllData data2 = r.getAllData(true);
        verifyExpectedData(data2);

        RequestInfo req2 = server.getRecorder().requireRequest();
        assertEquals("/sdk/latest-all", req2.getPath());
        verifyHeaders(req2);
        assertEquals("aaa", req2.getHeader("If-None-Match"));
      }
    }
  }

  @Test
  public void testSpecialHttpConfigurations() throws Exception {
    Handler handler = Handlers.bodyJson(allDataJson);
    
    TestHttpUtil.testWithSpecialHttpConfigurations(handler,
        (targetUri, goodHttpConfig) -> {
          LDConfig config = new LDConfig.Builder().http(goodHttpConfig).build();
          try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(config), targetUri)) {
            try {
              FeatureRequestor.AllData data = r.getAllData(false);
              verifyExpectedData(data);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        },
        
        (targetUri, badHttpConfig) -> {
          LDConfig config = new LDConfig.Builder().http(badHttpConfig).build();
          try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(config), targetUri)) {
            try {
              r.getAllData(false);
              fail("expected exception");
            } catch (Exception e) {
            }
          }
        }
        );
  }
  
  @Test
  public void baseUriDoesNotNeedTrailingSlash() throws Exception {
    Handler resp = Handlers.bodyJson(allDataJson);
    
    try (HttpServer server = HttpServer.start(resp)) {
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(LDConfig.DEFAULT), server.getUri())) {
        FeatureRequestor.AllData data = r.getAllData(true);
        
        RequestInfo req = server.getRecorder().requireRequest();
        assertEquals("/sdk/latest-all", req.getPath());
        verifyHeaders(req);
        
        verifyExpectedData(data);
      }
    }
  }

  @Test
  public void baseUriCanHaveContextPath() throws Exception {
    Handler resp = Handlers.bodyJson(allDataJson);
    
    try (HttpServer server = HttpServer.start(resp)) {
      URI uri = server.getUri().resolve("/context/path");
      
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(LDConfig.DEFAULT), uri)) {
        FeatureRequestor.AllData data = r.getAllData(true);
        
        RequestInfo req = server.getRecorder().requireRequest();
        assertEquals("/context/path/sdk/latest-all", req.getPath());
        verifyHeaders(req);
        
        verifyExpectedData(data);
      }
    }
  }
  
  private void verifyHeaders(RequestInfo req) {
    HttpConfiguration httpConfig = clientContext(sdkKey, LDConfig.DEFAULT).getHttp();
    for (Map.Entry<String, String> kv: httpConfig.getDefaultHeaders()) {
      assertThat(req.getHeader(kv.getKey()), equalTo(kv.getValue()));
    }
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
