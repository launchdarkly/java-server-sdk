package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.BasicConfiguration;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.junit.Test;

import java.net.URI;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.SSLHandshakeException;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestUtil.makeSocketFactorySingleHost;
import static com.launchdarkly.sdk.server.TestHttpUtil.httpsServerWithSelfSignedCert;
import static com.launchdarkly.sdk.server.TestHttpUtil.jsonResponse;
import static com.launchdarkly.sdk.server.TestHttpUtil.makeStartedServer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

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
  
  private DefaultFeatureRequestor makeRequestor(MockWebServer server) {
    return makeRequestor(server, LDConfig.DEFAULT);
    // We can always use LDConfig.DEFAULT unless we need to modify HTTP properties, since DefaultFeatureRequestor
    // no longer uses the deprecated LDConfig.baseUri property. 
  }

  private DefaultFeatureRequestor makeRequestor(MockWebServer server, LDConfig config) {
    URI uri = server.url("/").uri();
    return new DefaultFeatureRequestor(makeHttpConfig(config), uri);
  }

  private HttpConfiguration makeHttpConfig(LDConfig config) {
    return config.httpConfigFactory.createHttpConfiguration(new BasicConfiguration(sdkKey, false, 0));
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
    MockResponse resp = jsonResponse(allDataJson);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      try (DefaultFeatureRequestor r = makeRequestor(server)) {
        FeatureRequestor.AllData data = r.getAllData(true);
        
        RecordedRequest req = server.takeRequest();
        assertEquals("/sdk/latest-all", req.getPath());
        verifyHeaders(req);
        
        verifyExpectedData(data);
      }
    }
  }
  
  @Test
  public void responseIsCached() throws Exception {
    MockResponse cacheableResp = jsonResponse(allDataJson)
        .setHeader("ETag", "aaa")
        .setHeader("Cache-Control", "max-age=0");
    MockResponse cachedResp = new MockResponse().setResponseCode(304);
    
    try (MockWebServer server = makeStartedServer(cacheableResp, cachedResp)) {
      try (DefaultFeatureRequestor r = makeRequestor(server)) {
        FeatureRequestor.AllData data1 = r.getAllData(true);
        verifyExpectedData(data1);
         
        RecordedRequest req1 = server.takeRequest();
        assertEquals("/sdk/latest-all", req1.getPath());
        verifyHeaders(req1);
        assertNull(req1.getHeader("If-None-Match"));
         
        FeatureRequestor.AllData data2 = r.getAllData(false);
        assertNull(data2);

        RecordedRequest req2 = server.takeRequest();
        assertEquals("/sdk/latest-all", req2.getPath());
        verifyHeaders(req2);
        assertEquals("aaa", req2.getHeader("If-None-Match"));
      }
    }
  }

  @Test
  public void responseIsCachedButWeWantDataAnyway() throws Exception {
    MockResponse cacheableResp = jsonResponse(allDataJson)
        .setHeader("ETag", "aaa")
        .setHeader("Cache-Control", "max-age=0");
    MockResponse cachedResp = new MockResponse().setResponseCode(304);
    
    try (MockWebServer server = makeStartedServer(cacheableResp, cachedResp)) {
      try (DefaultFeatureRequestor r = makeRequestor(server)) {
        FeatureRequestor.AllData data1 = r.getAllData(true);
        verifyExpectedData(data1);
         
        RecordedRequest req1 = server.takeRequest();
        assertEquals("/sdk/latest-all", req1.getPath());
        verifyHeaders(req1);
        assertNull(req1.getHeader("If-None-Match"));
         
        FeatureRequestor.AllData data2 = r.getAllData(true);
        verifyExpectedData(data2);

        RecordedRequest req2 = server.takeRequest();
        assertEquals("/sdk/latest-all", req2.getPath());
        verifyHeaders(req2);
        assertEquals("aaa", req2.getHeader("If-None-Match"));
      }
    }
  }
  
  @Test
  public void httpClientDoesNotAllowSelfSignedCertByDefault() throws Exception {
    MockResponse resp = jsonResponse(allDataJson);
    
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(resp)) {
      try (DefaultFeatureRequestor r = makeRequestor(serverWithCert.server)) {
        try {
          r.getAllData(false);
          fail("expected exception");
        } catch (SSLHandshakeException e) {
        }
        
        assertEquals(0, serverWithCert.server.getRequestCount());
      }
    }
  }
  
  @Test
  public void httpClientCanUseCustomTlsConfig() throws Exception {
    MockResponse resp = jsonResponse(allDataJson);
    
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(resp)) {
      LDConfig config = new LDConfig.Builder()
          .http(Components.httpConfiguration().sslSocketFactory(serverWithCert.socketFactory, serverWithCert.trustManager))
          // allows us to trust the self-signed cert
          .build();

      try (DefaultFeatureRequestor r = makeRequestor(serverWithCert.server, config)) {
        FeatureRequestor.AllData data = r.getAllData(false);
        verifyExpectedData(data);
      }
    }
  }
  
  @Test
  public void httpClientCanUseCustomSocketFactory() throws Exception {
    try (MockWebServer server = makeStartedServer(jsonResponse(allDataJson))) {
      HttpUrl serverUrl = server.url("/");
      LDConfig config = new LDConfig.Builder()
        .http(Components.httpConfiguration().socketFactory(makeSocketFactorySingleHost(serverUrl.host(), serverUrl.port())))
        .build();

      URI uriWithWrongPort = URI.create("http://localhost:1");
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(config), uriWithWrongPort)) {
        FeatureRequestor.AllData data = r.getAllData(false);
        verifyExpectedData(data);
        
        assertEquals(1, server.getRequestCount());
      }
    }
  }
  
  @Test
  public void httpClientCanUseProxyConfig() throws Exception {
    URI fakeBaseUri = URI.create("http://not-a-real-host");
    try (MockWebServer server = makeStartedServer(jsonResponse(allDataJson))) {
      HttpUrl serverUrl = server.url("/");
      LDConfig config = new LDConfig.Builder()
          .http(Components.httpConfiguration().proxyHostAndPort(serverUrl.host(), serverUrl.port()))
          .build();
      
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(config), fakeBaseUri)) {
        FeatureRequestor.AllData data = r.getAllData(false);
        verifyExpectedData(data);
        
        assertEquals(1, server.getRequestCount());
      }
    }
  }
  
  @Test
  public void baseUriDoesNotNeedTrailingSlash() throws Exception {
    MockResponse resp = jsonResponse(allDataJson);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      URI uri = server.url("").uri();
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(LDConfig.DEFAULT), uri)) {
        FeatureRequestor.AllData data = r.getAllData(true);
        
        RecordedRequest req = server.takeRequest();
        assertEquals("/sdk/latest-all", req.getPath());
        verifyHeaders(req);
        
        verifyExpectedData(data);
      }
    }
  }

  @Test
  public void baseUriCanHaveContextPath() throws Exception {
    MockResponse resp = jsonResponse(allDataJson);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      URI uri = server.url("/context/path").uri();
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(LDConfig.DEFAULT), uri)) {
        FeatureRequestor.AllData data = r.getAllData(true);
        
        RecordedRequest req = server.takeRequest();
        assertEquals("/context/path/sdk/latest-all", req.getPath());
        verifyHeaders(req);
        
        verifyExpectedData(data);
      }
    }
  }
  
  private void verifyHeaders(RecordedRequest req) {
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
