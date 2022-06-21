package com.launchdarkly.sdk.server;

import com.launchdarkly.eventsource.ConnectionErrorHandler;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.TestComponents.DelegatingDataStore;
import com.launchdarkly.sdk.server.TestComponents.MockDataSourceUpdates;
import com.launchdarkly.sdk.server.TestComponents.MockDataSourceUpdates.UpsertParams;
import com.launchdarkly.sdk.server.TestComponents.MockDataStoreStatusProvider;
import com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;
import com.launchdarkly.testhelpers.httptest.Handler;
import com.launchdarkly.testhelpers.httptest.Handlers;
import com.launchdarkly.testhelpers.httptest.HttpServer;
import com.launchdarkly.testhelpers.httptest.RequestInfo;

import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;

import java.io.EOFException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.dataSourceUpdates;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatus;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertFutureIsCompleted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class StreamProcessorTest {

  private static final String SDK_KEY = "sdk_key";
  private static final Duration BRIEF_RECONNECT_DELAY = Duration.ofMillis(10);
  private static final String FEATURE1_KEY = "feature1";
  private static final int FEATURE1_VERSION = 11;
  private static final DataModel.FeatureFlag FEATURE = flagBuilder(FEATURE1_KEY).version(FEATURE1_VERSION).build();
  private static final String SEGMENT1_KEY = "segment1";
  private static final int SEGMENT1_VERSION = 22;
  private static final DataModel.Segment SEGMENT = segmentBuilder(SEGMENT1_KEY).version(SEGMENT1_VERSION).build();
  private static final String EMPTY_DATA_EVENT = makePutEvent(new DataBuilder().addAny(FEATURES).addAny(SEGMENTS));

  private InMemoryDataStore dataStore;
  private MockDataSourceUpdates dataSourceUpdates;
  private MockDataStoreStatusProvider dataStoreStatusProvider;

  private static Handler streamResponse(String data) {
    return Handlers.all(
        Handlers.SSE.start(),
        Handlers.SSE.event(data),
        Handlers.SSE.leaveOpen()
        );
  }

  private static Handler closableStreamResponse(String data, Semaphore closeSignal) {
    return Handlers.all(
        Handlers.SSE.start(),
        Handlers.SSE.event(data),
        Handlers.waitFor(closeSignal)
        );
  }
  
  private static Handler streamResponseFromQueue(BlockingQueue<String> events) {
    return Handlers.all(
        Handlers.SSE.start(),
        ctx -> {
          while (true) {
            try {
              String event = events.take();
              Handlers.SSE.event(event).apply(ctx);
            } catch (InterruptedException e) {
              break;
            }
          }
        }
        );
  }
  
  private static String makeEvent(String type, String data) {
    return "event: " + type + "\ndata: " + data;
  }
  
  private static String makePutEvent(DataBuilder data) {
    return makeEvent("put", "{\"data\":" + data.buildJson().toJsonString() + "}");
  }
  
  private static String makePatchEvent(String path, DataKind kind, VersionedData item) {
    String json = kind.serialize(new ItemDescriptor(item.getVersion(), item));
    return makeEvent("patch", "{\"path\":\"" + path + "\",\"data\":" + json + "}");
  }

  private static String makeDeleteEvent(String path, int version) {
    return makeEvent("delete", "{\"path\":\"" + path + "\",\"version\":" + version + "}");
  }
  
  @Before
  public void setup() {
    dataStore = new InMemoryDataStore();
    dataStoreStatusProvider = new MockDataStoreStatusProvider();
    dataSourceUpdates = TestComponents.dataSourceUpdates(dataStore, dataStoreStatusProvider);
  }

  @Test
  public void builderHasDefaultConfiguration() throws Exception {
    DataSourceFactory f = Components.streamingDataSource();
    try (StreamProcessor sp = (StreamProcessor)f.createDataSource(clientContext(SDK_KEY, LDConfig.DEFAULT),
        dataSourceUpdates)) {
      assertThat(sp.initialReconnectDelay, equalTo(StreamingDataSourceBuilder.DEFAULT_INITIAL_RECONNECT_DELAY));
      assertThat(sp.streamUri, equalTo(StandardEndpoints.DEFAULT_STREAMING_BASE_URI));
    }
  }

  @Test
  public void builderCanSpecifyConfiguration() throws Exception {
    DataSourceFactory f = Components.streamingDataSource()
        .initialReconnectDelay(Duration.ofMillis(5555));
    try (StreamProcessor sp = (StreamProcessor)f.createDataSource(clientContext(SDK_KEY, LDConfig.DEFAULT),
        dataSourceUpdates(dataStore))) {
      assertThat(sp.initialReconnectDelay, equalTo(Duration.ofMillis(5555)));
    }
  }
  
  @Test
  public void verifyStreamRequestProperties() throws Exception {
    HttpConfiguration httpConfig = clientContext(SDK_KEY, LDConfig.DEFAULT).getHttp();
    
    try (HttpServer server = HttpServer.start(streamResponse(EMPTY_DATA_EVENT))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        sp.start();
        
        RequestInfo req = server.getRecorder().requireRequest();
        assertThat(req.getMethod(), equalTo("GET"));
        assertThat(req.getPath(), equalTo("/all"));
        
        for (Map.Entry<String, String> kv: httpConfig.getDefaultHeaders()) {
          assertThat(req.getHeader(kv.getKey()), equalTo(kv.getValue()));
        }
        assertThat(req.getHeader("Accept"), equalTo("text/event-stream"));
      }
    }
  }
  
  @Test
  public void streamBaseUriDoesNotNeedTrailingSlash() throws Exception {
    try (HttpServer server = HttpServer.start(streamResponse(EMPTY_DATA_EVENT))) {
      URI baseUri = server.getUri();
      MatcherAssert.assertThat(baseUri.toString(), endsWith("/"));
      URI trimmedUri = URI.create(server.getUri().toString().substring(0, server.getUri().toString().length() - 1));
      try (StreamProcessor sp = createStreamProcessor(null, trimmedUri)) {
        sp.start();
        
        RequestInfo req = server.getRecorder().requireRequest();
        assertThat(req.getPath(), equalTo("/all"));
      }
    }
  }

  @Test
  public void streamBaseUriCanHaveContextPath() throws Exception {
    try (HttpServer server = HttpServer.start(streamResponse(EMPTY_DATA_EVENT))) {
      URI baseUri = server.getUri().resolve("/context/path");
      try (StreamProcessor sp = createStreamProcessor(null, baseUri)) {
        sp.start();
        
        RequestInfo req = server.getRecorder().requireRequest();
        assertThat(req.getPath(), equalTo("/context/path/all"));
      }
    }
  }
  
  @Test
  public void putCausesFeatureToBeStored() throws Exception {
    FeatureFlag flag = flagBuilder(FEATURE1_KEY).version(FEATURE1_VERSION).build();
    DataBuilder data = new DataBuilder().addAny(FEATURES, flag).addAny(SEGMENTS);
    Handler streamHandler = streamResponse(makePutEvent(data));
    
    try (HttpServer server = HttpServer.start(streamHandler)) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        sp.start();
        
        dataSourceUpdates.awaitInit();
        assertFeatureInStore(flag);
      }
    }
  }

  @Test
  public void putCausesSegmentToBeStored() throws Exception {
    Segment segment = ModelBuilders.segmentBuilder(SEGMENT1_KEY).version(SEGMENT1_VERSION).build();
    DataBuilder data = new DataBuilder().addAny(FEATURES).addAny(SEGMENTS, segment);
    Handler streamHandler = streamResponse(makePutEvent(data));

    try (HttpServer server = HttpServer.start(streamHandler)) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        sp.start();
        
        dataSourceUpdates.awaitInit();
        assertSegmentInStore(SEGMENT);
      }
    }
  }
  
  @Test
  public void storeNotInitializedByDefault() throws Exception {
    try (HttpServer server = HttpServer.start(streamResponse(""))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        sp.start();
        assertFalse(dataStore.isInitialized());
      }
    }
  }

  @Test
  public void processorNotInitializedByDefault() throws Exception {
    try (HttpServer server = HttpServer.start(streamResponse(""))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        sp.start();
        assertFalse(sp.isInitialized());
      }
    }
  }

  @Test
  public void futureIsNotSetByDefault() throws Exception {
    try (HttpServer server = HttpServer.start(streamResponse(""))) {
      try (StreamProcessor sp = createStreamProcessor(server.getUri())) {
        Future<Void> future = sp.start();
        assertFalse(future.isDone());
      }
    }
  }

  @Test
  public void putCausesStoreAndProcessorToBeInitialized() throws Exception {
    try (HttpServer server = HttpServer.start(streamResponse(EMPTY_DATA_EVENT))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        Future<Void> future = sp.start();
        
        dataSourceUpdates.awaitInit();
        assertFutureIsCompleted(future, 1, TimeUnit.SECONDS);
        assertTrue(dataStore.isInitialized());
        assertTrue(sp.isInitialized());
        assertTrue(future.isDone());
      }
    }
  }

  @Test
  public void patchUpdatesFeature() throws Exception {
    doPatchSuccessTest(FEATURES, FEATURE, "/flags/" + FEATURE.getKey());
  }

  @Test
  public void patchUpdatesSegment() throws Exception {
    doPatchSuccessTest(SEGMENTS, SEGMENT, "/segments/" + SEGMENT.getKey());
  }

  private void doPatchSuccessTest(DataKind kind, VersionedData item, String path) throws Exception {
    BlockingQueue<String> events = new LinkedBlockingQueue<>();
    events.add(EMPTY_DATA_EVENT);
    
    try (HttpServer server = HttpServer.start(streamResponseFromQueue(events))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        sp.start();
        dataSourceUpdates.awaitInit();
        
        events.add(makePatchEvent(path, kind, item));
        UpsertParams gotUpsert = dataSourceUpdates.awaitUpsert();
        
        assertThat(gotUpsert.kind, equalTo(kind));
        assertThat(gotUpsert.key, equalTo(item.getKey()));
        assertThat(gotUpsert.item.getVersion(), equalTo(item.getVersion()));
        
        ItemDescriptor result = dataStore.get(kind, item.getKey());
        assertNotNull(result.getItem());
        assertEquals(item.getVersion(), result.getVersion());
      }
    }
  }
  
  @Test
  public void deleteDeletesFeature() throws Exception {
    doDeleteSuccessTest(FEATURES, FEATURE, "/flags/" + FEATURE.getKey());
  }
  
  @Test
  public void deleteDeletesSegment() throws Exception {
    doDeleteSuccessTest(SEGMENTS, SEGMENT, "/segments/" + SEGMENT.getKey());
  }
  
  private void doDeleteSuccessTest(DataKind kind, VersionedData item, String path) throws Exception {
    BlockingQueue<String> events = new LinkedBlockingQueue<>();
    events.add(EMPTY_DATA_EVENT);
    
    try (HttpServer server = HttpServer.start(streamResponseFromQueue(events))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        sp.start();
        dataSourceUpdates.awaitInit();
        
        dataStore.upsert(kind, item.getKey(), new ItemDescriptor(item.getVersion(), item));
        
        events.add(makeDeleteEvent(path, item.getVersion() + 1));
        UpsertParams gotUpsert = dataSourceUpdates.awaitUpsert();
        
        assertThat(gotUpsert.kind, equalTo(kind));
        assertThat(gotUpsert.key, equalTo(item.getKey()));
        assertThat(gotUpsert.item.getVersion(), equalTo(item.getVersion() + 1));
        
        assertEquals(ItemDescriptor.deletedItem(item.getVersion() + 1), dataStore.get(kind, item.getKey()));
      }
    }
  }
  
  @Test
  public void unknownEventTypeDoesNotCauseError() throws Exception {
    verifyEventCausesNoStreamRestart("what", "");
  }
  
  @Test
  public void streamWillReconnectAfterGeneralIOException() throws Exception {
    Handler errorHandler = Handlers.malformedResponse();
    Handler streamHandler = streamResponse(EMPTY_DATA_EVENT);
    Handler errorThenSuccess = Handlers.sequential(errorHandler, streamHandler);
    
    try (HttpServer server = HttpServer.start(errorThenSuccess)) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        startAndWait(sp);

        assertThat(server.getRecorder().count(), equalTo(2));
        assertThat(dataSourceUpdates.getLastStatus().getLastError(), notNullValue());
        assertThat(dataSourceUpdates.getLastStatus().getLastError().getKind(), equalTo(ErrorKind.NETWORK_ERROR));
      }
    }
  }

  @Test
  public void streamInitDiagnosticRecordedOnOpen() throws Exception {
    DiagnosticAccumulator acc = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
    long startTime = System.currentTimeMillis();
    
    try (HttpServer server = HttpServer.start(streamResponse(EMPTY_DATA_EVENT))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri(), acc)) {
        startAndWait(sp);
        
        long timeAfterOpen = System.currentTimeMillis();
        DiagnosticEvent.Statistics event = acc.createEventAndReset(0, 0);
        assertEquals(1, event.streamInits.size());
        DiagnosticEvent.StreamInit init = event.streamInits.get(0);
        assertFalse(init.failed);
        assertThat(init.timestamp, greaterThanOrEqualTo(startTime));
        assertThat(init.timestamp, lessThanOrEqualTo(timeAfterOpen));
        assertThat(init.durationMillis, lessThanOrEqualTo(timeAfterOpen - startTime));
      }
    }
  }

  @Test
  public void streamInitDiagnosticRecordedOnErrorDuringInit() throws Exception {
    DiagnosticAccumulator acc = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
    long startTime = System.currentTimeMillis();
    
    Handler errorHandler = Handlers.status(503);
    Handler streamHandler = streamResponse(EMPTY_DATA_EVENT);
    Handler errorThenSuccess = Handlers.sequential(errorHandler, streamHandler);
    
    try (HttpServer server = HttpServer.start(errorThenSuccess)) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri(), acc)) {
        startAndWait(sp);
        
        long timeAfterOpen = System.currentTimeMillis();
        DiagnosticEvent.Statistics event = acc.createEventAndReset(0, 0);
        
        assertEquals(2, event.streamInits.size());
        DiagnosticEvent.StreamInit init0 = event.streamInits.get(0);
        assertTrue(init0.failed);
        assertThat(init0.timestamp, greaterThanOrEqualTo(startTime));
        assertThat(init0.timestamp, lessThanOrEqualTo(timeAfterOpen));
        assertThat(init0.durationMillis, lessThanOrEqualTo(timeAfterOpen - startTime));

        DiagnosticEvent.StreamInit init1 = event.streamInits.get(1);
        assertFalse(init1.failed);
        assertThat(init1.timestamp, greaterThanOrEqualTo(init0.timestamp));
        assertThat(init1.timestamp, lessThanOrEqualTo(timeAfterOpen));
      }
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

  @Test
  public void http408ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(408);
  }

  @Test
  public void http429ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(429);
  }

  @Test
  public void http500ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(500);
  }
  
  @Test
  public void putEventWithInvalidJsonCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestart("put", "{sorry", ErrorKind.INVALID_DATA);
  }

  @Test
  public void putEventWithWellFormedJsonButInvalidDataCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestart("put", "{\"data\":{\"flags\":3}}", ErrorKind.INVALID_DATA);
  }

  @Test
  public void patchEventWithInvalidJsonCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestart("patch", "{sorry", ErrorKind.INVALID_DATA);
  }

  @Test
  public void patchEventWithWellFormedJsonButInvalidDataCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestart("patch", "{\"path\":\"/flags/flagkey\", \"data\":{\"rules\":3}}", ErrorKind.INVALID_DATA);
  }

  @Test
  public void patchEventWithInvalidPathCausesNoStreamRestart() throws Exception {
    verifyEventCausesNoStreamRestart("patch", "{\"path\":\"/wrong\", \"data\":{\"key\":\"flagkey\"}}");
  }

  @Test
  public void patchEventWithNullPathCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestart("patch", "{\"path\":null, \"data\":{\"key\":\"flagkey\"}}", ErrorKind.INVALID_DATA);
  }

  @Test
  public void deleteEventWithInvalidJsonCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestart("delete", "{sorry", ErrorKind.INVALID_DATA);
  }

  @Test
  public void deleteEventWithInvalidPathCausesNoStreamRestart() throws Exception {
    verifyEventCausesNoStreamRestart("delete", "{\"path\":\"/wrong\", \"version\":1}");
  }

  @Test
  public void indirectPatchEventWithInvalidPathDoesNotCauseStreamRestart() throws Exception {
    verifyEventCausesNoStreamRestart("indirect/patch", "/wrong");
  }

  @Test
  public void restartsStreamIfStoreNeedsRefresh() throws Exception {
    try (HttpServer server = HttpServer.start(streamResponse(EMPTY_DATA_EVENT))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        startAndWait(sp);
        dataSourceUpdates.awaitInit();
        server.getRecorder().requireRequest();
        
        dataStoreStatusProvider.updateStatus(new DataStoreStatusProvider.Status(false, false));
        dataStoreStatusProvider.updateStatus(new DataStoreStatusProvider.Status(true, true));

        dataSourceUpdates.awaitInit();
        server.getRecorder().requireRequest();
        server.getRecorder().requireNoRequests(Duration.ofMillis(100));
      }
    }
  }

  @Test
  public void doesNotRestartStreamIfStoreHadOutageButDoesNotNeedRefresh() throws Exception {
    try (HttpServer server = HttpServer.start(streamResponse(EMPTY_DATA_EVENT))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        startAndWait(sp);
        dataSourceUpdates.awaitInit();
        server.getRecorder().requireRequest();
        
        dataStoreStatusProvider.updateStatus(new DataStoreStatusProvider.Status(false, false));
        dataStoreStatusProvider.updateStatus(new DataStoreStatusProvider.Status(true, false));

        server.getRecorder().requireNoRequests(Duration.ofMillis(100));
      }
    }
  }

  private void verifyStoreErrorCausesStreamRestart(String eventName, String eventData) throws Exception {
    AtomicInteger updateCount = new AtomicInteger(0);
    Runnable preUpdateHook = () -> {
      int count = updateCount.incrementAndGet();
      if (count == 2) {
        // only fail on the 2nd update - the first is the one caused by the initial "put" in the test setup
        throw new RuntimeException("sorry");
      }
    };
    DelegatingDataStore delegatingStore = new DelegatingDataStore(dataStore, preUpdateHook);
    dataStoreStatusProvider = new MockDataStoreStatusProvider(false); // false = the store does not provide status monitoring
    dataSourceUpdates = TestComponents.dataSourceUpdates(delegatingStore, dataStoreStatusProvider);
    
    verifyEventCausesStreamRestart(eventName, eventData, ErrorKind.STORE_ERROR);
  }
  
  @Test
  public void storeFailureOnPutCausesStreamRestart() throws Exception {
    verifyStoreErrorCausesStreamRestart("put", emptyPutEvent().getData());
  }

  @Test
  public void storeFailureOnPatchCausesStreamRestart() throws Exception {
    String patchData = "{\"path\":\"/flags/flagkey\",\"data\":{\"key\":\"flagkey\",\"version\":1}}";
    verifyStoreErrorCausesStreamRestart("patch", patchData);
  }

  @Test
  public void storeFailureOnDeleteCausesStreamRestart() throws Exception {
    String deleteData = "{\"path\":\"/flags/flagkey\",\"version\":1}";
    verifyStoreErrorCausesStreamRestart("delete", deleteData);
  }

  @Test
  public void sseCommentIsIgnored() throws Exception {
    BlockingQueue<String> events = new LinkedBlockingQueue<>();
    events.add(EMPTY_DATA_EVENT);
    
    try (HttpServer server = HttpServer.start(streamResponseFromQueue(events))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        startAndWait(sp);
        
        events.add(": this is a comment");
        
        // Do something after the comment, just to verify that the stream is still working
        events.add(makePatchEvent("/flags/" + FEATURE.getKey(), FEATURES, FEATURE));
        dataSourceUpdates.awaitUpsert();
      }
      assertThat(server.getRecorder().count(), equalTo(1)); // did not restart
      assertThat(dataSourceUpdates.getLastStatus().getLastError(), nullValue());
    }
  }
  
  private void verifyEventCausesNoStreamRestart(String eventName, String eventData) throws Exception {
    BlockingQueue<String> events = new LinkedBlockingQueue<>();
    events.add(EMPTY_DATA_EVENT);
    
    try (HttpServer server = HttpServer.start(streamResponseFromQueue(events))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        startAndWait(sp);
        
        events.add(makeEvent(eventName, eventData));
        
        // Do something after the test event, just to verify that the stream is still working
        events.add(makePatchEvent("/flags/" + FEATURE.getKey(), FEATURES, FEATURE));
        dataSourceUpdates.awaitUpsert();
      }
      assertThat(server.getRecorder().count(), equalTo(1)); // did not restart
      assertThat(dataSourceUpdates.getLastStatus().getLastError(), nullValue());
    }
  }

  private void verifyEventCausesStreamRestart(String eventName, String eventData, ErrorKind expectedError) throws Exception {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);
    
    BlockingQueue<String> events = new LinkedBlockingQueue<>();
    events.add(EMPTY_DATA_EVENT);
    
    try (HttpServer server = HttpServer.start(streamResponseFromQueue(events))) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        sp.start();
        dataSourceUpdates.awaitInit();
        server.getRecorder().requireRequest();
       
        requireDataSourceStatus(statuses, State.VALID);
         
        events.add(makeEvent(eventName, eventData));
        events.add(EMPTY_DATA_EVENT);
        
        server.getRecorder().requireRequest();
        dataSourceUpdates.awaitInit();
        
        Status status = requireDataSourceStatus(statuses, State.INTERRUPTED);
        assertThat(status.getLastError(), notNullValue());
        assertThat(status.getLastError().getKind(), equalTo(expectedError));

        requireDataSourceStatus(statuses, State.VALID);
      }
    }
  }
  
  @Test
  public void testSpecialHttpConfigurations() throws Exception {
    Handler handler = streamResponse(EMPTY_DATA_EVENT);
    
    TestHttpUtil.testWithSpecialHttpConfigurations(handler,
        (targetUri, goodHttpConfig) -> {
          LDConfig config = new LDConfig.Builder().http(goodHttpConfig).build();
          ConnectionErrorSink errorSink = new ConnectionErrorSink();
          
          try (StreamProcessor sp = createStreamProcessor(config, targetUri)) {
            sp.connectionErrorHandler = errorSink;
            startAndWait(sp);
            assertNull(errorSink.errors.peek());
          }
        },
        (targetUri, badHttpConfig) -> {
          LDConfig config = new LDConfig.Builder().http(badHttpConfig).build();
          ConnectionErrorSink errorSink = new ConnectionErrorSink();
          
          try (StreamProcessor sp = createStreamProcessor(config, targetUri)) {
            sp.connectionErrorHandler = errorSink;
            startAndWait(sp);
            
            Throwable error = errorSink.errors.peek();
            assertNotNull(error);
          }
        }
        );
  }
  
  static class ConnectionErrorSink implements ConnectionErrorHandler {
    final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();
    
    public Action onConnectionError(Throwable t) {
      if (!(t instanceof EOFException)) {
        errors.add(t);
      }
      return Action.SHUTDOWN;
    }
  }
  
  private void testUnrecoverableHttpError(int statusCode) throws Exception {
    Handler errorResp = Handlers.status(statusCode);
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (HttpServer server = HttpServer.start(errorResp)) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        Future<Void> initFuture = sp.start();       
        assertFutureIsCompleted(initFuture, 2, TimeUnit.SECONDS);
        
        assertFalse(sp.isInitialized());
        
        Status newStatus = requireDataSourceStatus(statuses, State.OFF);
        assertEquals(ErrorKind.ERROR_RESPONSE, newStatus.getLastError().getKind());
        assertEquals(statusCode, newStatus.getLastError().getStatusCode());
        
        server.getRecorder().requireRequest();
        server.getRecorder().requireNoRequests(Duration.ofMillis(50));
      }
    }
  }
  
  private void testRecoverableHttpError(int statusCode) throws Exception {
    Semaphore closeFirstStreamSignal = new Semaphore(0);
    Handler errorResp = Handlers.status(statusCode);
    Handler stream1Resp = closableStreamResponse(EMPTY_DATA_EVENT, closeFirstStreamSignal);
    Handler stream2Resp = streamResponse(EMPTY_DATA_EVENT);
    
    // Set up the sequence of responses that we'll receive below.
    Handler seriesOfResponses = Handlers.sequential(errorResp, stream1Resp, errorResp, stream2Resp);
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (HttpServer server = HttpServer.start(seriesOfResponses)) {
      try (StreamProcessor sp = createStreamProcessor(null, server.getUri())) {
        Future<Void> initFuture = sp.start();       
        assertFutureIsCompleted(initFuture, 2, TimeUnit.SECONDS);
        
        assertTrue(sp.isInitialized());
        
        // The first stream request receives an error response (errorResp).
        Status failureStatus1 = requireDataSourceStatus(statuses, State.INITIALIZING);
        assertEquals(ErrorKind.ERROR_RESPONSE, failureStatus1.getLastError().getKind());
        assertEquals(statusCode, failureStatus1.getLastError().getStatusCode());

        // It tries to reconnect, and gets a valid response (stream1Resp). Now the stream is active.
        Status successStatus1 = requireDataSourceStatus(statuses, State.VALID);
        assertSame(failureStatus1.getLastError(), successStatus1.getLastError());
       
        // Now we'll trigger a disconnection of the stream. The SDK detects that as a
        // NETWORK_ERROR. The state changes to INTERRUPTED because it was previously connected.
        closeFirstStreamSignal.release();
        Status failureStatus2 = requireDataSourceStatus(statuses, State.INTERRUPTED);
        assertEquals(ErrorKind.NETWORK_ERROR, failureStatus2.getLastError().getKind());
        
        // It tries to reconnect, and gets another errorResp. The state is still INTERRUPTED.
        Status failureStatus3 = requireDataSourceStatus(statuses, State.INTERRUPTED);
        assertEquals(ErrorKind.ERROR_RESPONSE, failureStatus3.getLastError().getKind());
        assertEquals(statusCode, failureStatus3.getLastError().getStatusCode());
 
        // It tries again, and finally gets a valid response (stream2Resp).
        Status successStatus2 = requireDataSourceStatus(statuses, State.VALID);
        assertSame(failureStatus3.getLastError(), successStatus2.getLastError());
      }
    }
  }
  
  private StreamProcessor createStreamProcessor(URI streamUri) {
    return createStreamProcessor(LDConfig.DEFAULT, streamUri, null);
  }

  private StreamProcessor createStreamProcessor(LDConfig config, URI streamUri, DiagnosticAccumulator acc) {
    return new StreamProcessor(
        clientContext(SDK_KEY, config == null ? LDConfig.DEFAULT : config).getHttp(),
        dataSourceUpdates,
        Thread.MIN_PRIORITY,
        acc,
        streamUri,
        BRIEF_RECONNECT_DELAY
        );
  }

  private StreamProcessor createStreamProcessor(LDConfig config, URI streamUri) {
    return createStreamProcessor(config, streamUri, null);
  }

  private static void startAndWait(StreamProcessor sp) {
    Future<Void> ready = sp.start();
    try {
      ready.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  private MessageEvent emptyPutEvent() {
    return new MessageEvent("{\"data\":{\"flags\":{},\"segments\":{}}}");
  }
  
  private void assertFeatureInStore(DataModel.FeatureFlag feature) {
    assertEquals(feature.getVersion(), dataStore.get(FEATURES, feature.getKey()).getVersion());
  }
  
  private void assertSegmentInStore(DataModel.Segment segment) {
    assertEquals(segment.getVersion(), dataStore.get(SEGMENTS, segment.getKey()).getVersion());
  }
}
