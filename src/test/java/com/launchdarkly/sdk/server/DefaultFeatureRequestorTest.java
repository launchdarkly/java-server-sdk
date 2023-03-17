package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.internal.http.HttpErrors.HttpErrorException;
import com.launchdarkly.sdk.internal.http.HttpProperties;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.sdk.server.subsystems.SerializationException;
import com.launchdarkly.testhelpers.httptest.Handler;
import com.launchdarkly.testhelpers.httptest.Handlers;
import com.launchdarkly.testhelpers.httptest.HttpServer;
import com.launchdarkly.testhelpers.httptest.RequestInfo;
import com.launchdarkly.testhelpers.httptest.SpecialHttpConfigurations;

import org.junit.Test;

import java.net.URI;
import java.util.Map;

import static com.launchdarkly.sdk.server.JsonHelpers.serialize;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestUtil.assertDataSetEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class DefaultFeatureRequestorTest extends BaseTest {
  private static final String sdkKey = "sdk-key";
  private static final String flag1Key = "flag1";
  private static final FeatureFlag flag1 = flagBuilder(flag1Key).version(1000).build();
  private static final String flag1Json = serialize(flag1);
  private static final String flagsJson = "{\"" + flag1Key + "\":" + flag1Json + "}";
  private static final String segment1Key = "segment1";
  private static final Segment segment1 = segmentBuilder(segment1Key).version(2000).build();
  private static final String segment1Json = serialize(segment1);
  private static final String segmentsJson = "{\"" + segment1Key + "\":" + segment1Json + "}";
  private static final String allDataJson = "{\"flags\":" + flagsJson + ",\"segments\":" + segmentsJson + "}";
  
  private DefaultFeatureRequestor makeRequestor(HttpServer server) {
    return makeRequestor(server, LDConfig.DEFAULT);
    // We can always use LDConfig.DEFAULT unless we need to modify HTTP properties, since DefaultFeatureRequestor
    // no longer uses the deprecated LDConfig.baseUri property. 
  }

  private DefaultFeatureRequestor makeRequestor(HttpServer server, LDConfig config) {
    return new DefaultFeatureRequestor(makeHttpConfig(config), server.getUri(), null, testLogger);
  }

  private HttpProperties makeHttpConfig(LDConfig config) {
    return ComponentsImpl.toHttpProperties(config.http.build(new ClientContext(sdkKey)));
  }

  private void verifyExpectedData(FullDataSet<ItemDescriptor> data) {
    assertNotNull(data);
    assertDataSetEquals(DataBuilder.forStandardTypes()
        .addAny(DataModel.FEATURES, flag1).addAny(DataModel.SEGMENTS, segment1).build(),
        data);
  }
  
  @Test
  public void requestAllData() throws Exception {
    Handler resp = Handlers.bodyJson(allDataJson);
    
    try (HttpServer server = HttpServer.start(resp)) {
      try (DefaultFeatureRequestor r = makeRequestor(server)) {
        FullDataSet<ItemDescriptor> data = r.getAllData(true);
        
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
        FullDataSet<ItemDescriptor> data1 = r.getAllData(true);
        verifyExpectedData(data1);
         
        RequestInfo req1 = server.getRecorder().requireRequest();
        assertEquals("/sdk/latest-all", req1.getPath());
        verifyHeaders(req1);
        assertNull(req1.getHeader("If-None-Match"));
         
        FullDataSet<ItemDescriptor> data2 = r.getAllData(false);
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
        FullDataSet<ItemDescriptor> data1 = r.getAllData(true);
        verifyExpectedData(data1);
         
        RequestInfo req1 = server.getRecorder().requireRequest();
        assertEquals("/sdk/latest-all", req1.getPath());
        verifyHeaders(req1);
        assertNull(req1.getHeader("If-None-Match"));
         
        FullDataSet<ItemDescriptor> data2 = r.getAllData(true);
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
    
    SpecialHttpConfigurations.testAll(handler,
        (URI serverUri, SpecialHttpConfigurations.Params params) -> {
          LDConfig config = new LDConfig.Builder().http(TestUtil.makeHttpConfigurationFromTestParams(params)).build();
          try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(config), serverUri, null, testLogger)) {
            FullDataSet<ItemDescriptor> data = r.getAllData(false);
            verifyExpectedData(data);
            return true;
          } catch (SerializationException e) {
            throw new SpecialHttpConfigurations.UnexpectedResponseException(e.toString());
          } catch (HttpErrorException e) {
            throw new SpecialHttpConfigurations.UnexpectedResponseException(e.toString());
          }
        });
  }
  
  @Test
  public void baseUriDoesNotNeedTrailingSlash() throws Exception {
    Handler resp = Handlers.bodyJson(allDataJson);
    
    try (HttpServer server = HttpServer.start(resp)) {
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(LDConfig.DEFAULT), server.getUri(), null, testLogger)) {
        FullDataSet<ItemDescriptor> data = r.getAllData(true);
 
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
      
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(LDConfig.DEFAULT), uri, null, testLogger)) {
        FullDataSet<ItemDescriptor> data = r.getAllData(true);
 
        RequestInfo req = server.getRecorder().requireRequest();
        assertEquals("/context/path/sdk/latest-all", req.getPath());
        verifyHeaders(req);
        
        verifyExpectedData(data);
      }
    }
  }

  @Test
  public void pollingUriCanHavePayload() throws Exception {
    Handler resp = Handlers.bodyJson(allDataJson);
    
    try (HttpServer server = HttpServer.start(resp)) {
      URI uri = server.getUri().resolve("/context/path");
      
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(LDConfig.DEFAULT), uri, "myFilter", testLogger)) {
        FullDataSet<ItemDescriptor> data = r.getAllData(true);
 
        RequestInfo req = server.getRecorder().requireRequest();
        assertEquals("?filter=myFilter", req.getQuery());
        verifyHeaders(req);
        
        verifyExpectedData(data);
      }
    }
  }

  @Test
  public void ignoreEmptyFilter()  throws Exception {
    Handler resp = Handlers.bodyJson(allDataJson);
    
    try (HttpServer server = HttpServer.start(resp)) {
      URI uri = server.getUri().resolve("/context/path");
      
      try (DefaultFeatureRequestor r = new DefaultFeatureRequestor(makeHttpConfig(LDConfig.DEFAULT), uri, "", testLogger)) {
        FullDataSet<ItemDescriptor> data = r.getAllData(true);
 
        RequestInfo req = server.getRecorder().requireRequest();
        assertNull(req.getQuery());
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
}
