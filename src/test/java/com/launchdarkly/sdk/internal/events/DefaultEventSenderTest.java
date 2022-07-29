package com.launchdarkly.sdk.internal.events;

import com.launchdarkly.sdk.internal.http.HttpProperties;
import com.launchdarkly.testhelpers.httptest.Handler;
import com.launchdarkly.testhelpers.httptest.Handlers;
import com.launchdarkly.testhelpers.httptest.HttpServer;
import com.launchdarkly.testhelpers.httptest.RequestInfo;

import org.junit.Test;

import java.net.URI;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class DefaultEventSenderTest extends BaseEventTest {
  private static final String FAKE_DATA = "some data";
  private static final byte[] FAKE_DATA_BYTES = FAKE_DATA.getBytes(Charset.forName("UTF-8"));
  private static final SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",
      Locale.US);
  private static final long BRIEF_RETRY_DELAY_MILLIS = 50;
  
  private EventSender makeEventSender() {
    return makeEventSender(defaultHttpProperties());
  }

  private EventSender makeEventSender(HttpProperties httpProperties) {
    return new DefaultEventSender(httpProperties, BRIEF_RETRY_DELAY_MILLIS, testLogger);
  }
  
  @Test
  public void analyticsDataIsDelivered() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
        
        assertTrue(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();   
      assertEquals("/bulk", req.getPath());
      assertThat(req.getHeader("content-type"), equalToIgnoringCase("application/json; charset=utf-8"));
      assertEquals(FAKE_DATA, req.getBody());
    }
  }

  @Test
  public void diagnosticDataIsDelivered() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendDiagnosticEvent(FAKE_DATA_BYTES, server.getUri());
        
        assertTrue(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();      
      assertEquals("/diagnostic", req.getPath());
      assertThat(req.getHeader("content-type"), equalToIgnoringCase("application/json; charset=utf-8"));
      assertEquals(FAKE_DATA, req.getBody());
    }
  }

  @Test
  public void headersAreSentForAnalytics() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("name1", "value1");
    headers.put("name2", "value2");
    HttpProperties httpProperties = new HttpProperties(0, headers, null, null, null, 0, null, null);
    
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender(httpProperties)) {
        es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();
      for (Map.Entry<String, String> kv: headers.entrySet()) {
        assertThat(req.getHeader(kv.getKey()), equalTo(kv.getValue()));
      }
    }
  }

  @Test
  public void headersAreSentForDiagnostics() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("name1", "value1");
    headers.put("name2", "value2");
    HttpProperties httpProperties = new HttpProperties(0, headers, null, null, null, 0, null, null);
    
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender(httpProperties)) {
        es.sendDiagnosticEvent(FAKE_DATA_BYTES, server.getUri());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();      
      for (Map.Entry<String, String> kv: headers.entrySet()) {
        assertThat(req.getHeader(kv.getKey()), equalTo(kv.getValue()));
      }
    }
  }

  @Test
  public void eventSchemaIsSentForAnalytics() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
      }

      RequestInfo req = server.getRecorder().requireRequest();
      assertThat(req.getHeader("X-LaunchDarkly-Event-Schema"), equalTo("4"));
    }
  }

  @Test
  public void eventPayloadIdIsSentForAnalytics() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
      }

      RequestInfo req = server.getRecorder().requireRequest();
      String payloadHeaderValue = req.getHeader("X-LaunchDarkly-Payload-ID");
      assertThat(payloadHeaderValue, notNullValue(String.class));
      assertThat(UUID.fromString(payloadHeaderValue), notNullValue(UUID.class));
    }
  }

  @Test
  public void eventPayloadIdReusedOnRetry() throws Exception {
    Handler errorResponse = Handlers.status(429);
    Handler errorThenSuccess = Handlers.sequential(errorResponse, eventsSuccessResponse(), eventsSuccessResponse());

    try (HttpServer server = HttpServer.start(errorThenSuccess)) {
      try (EventSender es = makeEventSender()) {
        es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
        es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
      }

      // Failed response request
      RequestInfo req = server.getRecorder().requireRequest();
      String payloadId = req.getHeader("X-LaunchDarkly-Payload-ID");
      // Retry request has same payload ID as failed request
      req = server.getRecorder().requireRequest();
      String retryId = req.getHeader("X-LaunchDarkly-Payload-ID");
      assertThat(retryId, equalTo(payloadId));
      // Second request has different payload ID from first request
      req = server.getRecorder().requireRequest();
      payloadId = req.getHeader("X-LaunchDarkly-Payload-ID");
      assertThat(retryId, not(equalTo(payloadId)));
    }
  }
  
  @Test
  public void eventSchemaNotSetOnDiagnosticEvents() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        es.sendDiagnosticEvent(FAKE_DATA_BYTES, server.getUri());
      }

      RequestInfo req = server.getRecorder().requireRequest();
      assertNull(req.getHeader("X-LaunchDarkly-Event-Schema"));
    }
  }

  @Test
  public void http400ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(400);
  }

  @Test
  public void http401ErrorIsUnrecoverable() throws Exception {
    testUnrecoverableHttpError(401);
  }

  @Test
  public void http403ErrorIsUnrecoverable() throws Exception {
    testUnrecoverableHttpError(403);
  }

  // Cannot test our retry logic for 408, because OkHttp insists on doing its own retry on 408 so that
  // we never actually see that response status.
//  @Test
//  public void http408ErrorIsRecoverable() throws Exception {
//    testRecoverableHttpError(408);
//  }

  @Test
  public void http429ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(429);
  }

  @Test
  public void http500ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(500);
  }
 
  @Test
  public void serverDateIsParsed() throws Exception {
    long fakeTime = ((new Date().getTime() - 100000) / 1000) * 1000; // don't expect millisecond precision
    Handler resp = Handlers.all(eventsSuccessResponse(), addDateHeader(new Date(fakeTime)));

    try (HttpServer server = HttpServer.start(resp)) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
        
        assertNotNull(result.getTimeFromServer());
        assertEquals(fakeTime, result.getTimeFromServer().getTime());
      }
    }
  }

  @Test
  public void invalidServerDateIsIgnored() throws Exception {
    Handler resp = Handlers.all(eventsSuccessResponse(), Handlers.header("Date", "not a date"));

    try (HttpServer server = HttpServer.start(resp)) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
        
        assertTrue(result.isSuccess());
        assertNull(result.getTimeFromServer());
      }
    }
  }

//  @Test
//  public void testSpecialHttpConfigurations() throws Exception {
//    Handler handler = eventsSuccessResponse();
//    
//    TestHttpUtil.testWithSpecialHttpConfigurations(handler,
//        (targetUri, goodHttpConfig) -> {
//          HttpConfiguration config = goodHttpConfig.createHttpConfiguration(clientContext("", LDConfig.DEFAULT));
//          
//          try (EventSender es = makeEventSender(ComponentsImpl.toHttpProperties(config))) {
//            EventSender.Result result = es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, targetUri);
//            
//            assertTrue(result.isSuccess());
//            assertFalse(result.isMustShutDown());
//          }
//        },
//        
//        (targetUri, badHttpConfig) -> {
//          HttpConfiguration config = badHttpConfig.createHttpConfiguration(clientContext("", LDConfig.DEFAULT));
// 
//          try (EventSender es = makeEventSender(ComponentsImpl.toHttpProperties(config))) {
//            EventSender.Result result = es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, targetUri);
//            
//            assertFalse(result.isSuccess());
//            assertFalse(result.isMustShutDown());
//          }
//        }
//        );
//  }
  
  @Test
  public void baseUriDoesNotNeedTrailingSlash() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        URI uriWithoutSlash = URI.create(server.getUri().toString().replaceAll("/$", ""));
        EventSender.Result result = es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, uriWithoutSlash);
        
        assertTrue(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();   
      assertEquals("/bulk", req.getPath());
      assertThat(req.getHeader("content-type"), equalToIgnoringCase("application/json; charset=utf-8"));
      assertEquals(FAKE_DATA, req.getBody());
    }
  }

  @Test
  public void baseUriCanHaveContextPath() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        URI baseUri = server.getUri().resolve("/context/path");
        EventSender.Result result = es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, baseUri);
        
        assertTrue(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();   
      assertEquals("/context/path/bulk", req.getPath());
      assertThat(req.getHeader("content-type"), equalToIgnoringCase("application/json; charset=utf-8"));
      assertEquals(FAKE_DATA, req.getBody());
    }
  }

  @Test
  public void nothingIsSentForNullData() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result1 = es.sendAnalyticsEvents(null, 0, server.getUri());
        EventSender.Result result2 = es.sendDiagnosticEvent(null, server.getUri());
        
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertEquals(0, server.getRecorder().count());
      }
    }
  }

  @Test
  public void nothingIsSentForEmptyData() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result1 = es.sendAnalyticsEvents(new byte[0], 0, server.getUri());
        EventSender.Result result2 = es.sendDiagnosticEvent(new byte[0], server.getUri());
        
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertEquals(0, server.getRecorder().count());
      }
    }
  }
  
  private void testUnrecoverableHttpError(int status) throws Exception {
    Handler errorResponse = Handlers.status(status);
    
    try (HttpServer server = HttpServer.start(errorResponse)) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
        
        assertFalse(result.isSuccess());
        assertTrue(result.isMustShutDown());
      }

      server.getRecorder().requireRequest();
      
      // it does not retry after this type of error, so there are no more requests
      server.getRecorder().requireNoRequests(Duration.ofMillis(100));
    }
  }
  
  private void testRecoverableHttpError(int status) throws Exception {
    Handler errorResponse = Handlers.status(status);
    Handler errorsThenSuccess = Handlers.sequential(errorResponse, errorResponse, eventsSuccessResponse());
    // send two errors in a row, because the flush will be retried one time
    
    try (HttpServer server = HttpServer.start(errorsThenSuccess)) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendAnalyticsEvents(FAKE_DATA_BYTES, 1, server.getUri());
        
        assertFalse(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }

      server.getRecorder().requireRequest();
      server.getRecorder().requireRequest();
      server.getRecorder().requireNoRequests(Duration.ofMillis(100)); // only 2 requests total
    }
  }

  private Handler eventsSuccessResponse() {
    return Handlers.status(202);
  }
  
  private Handler addDateHeader(Date date) {
    return Handlers.header("Date", httpDateFormat.format(date));
  }
}
