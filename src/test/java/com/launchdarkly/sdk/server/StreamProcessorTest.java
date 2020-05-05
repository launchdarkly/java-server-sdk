package com.launchdarkly.sdk.server;

import com.launchdarkly.eventsource.ConnectionErrorHandler;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.UnsuccessfulResponseException;
import com.launchdarkly.sdk.server.TestComponents.MockDataStoreStatusProvider;
import com.launchdarkly.sdk.server.TestComponents.MockEventSourceCreator;
import com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLHandshakeException;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.JsonHelpers.gsonInstance;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.dataStoreThatThrowsException;
import static com.launchdarkly.sdk.server.TestComponents.dataStoreUpdates;
import static com.launchdarkly.sdk.server.TestHttpUtil.eventStreamResponse;
import static com.launchdarkly.sdk.server.TestHttpUtil.makeStartedServer;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static com.launchdarkly.sdk.server.TestUtil.upsertSegment;
import static com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder.DEFAULT_INITIAL_RECONNECT_DELAY;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;

@SuppressWarnings("javadoc")
public class StreamProcessorTest extends EasyMockSupport {

  private static final String SDK_KEY = "sdk_key";
  private static final URI STREAM_URI = URI.create("http://stream.test.com");
  private static final String FEATURE1_KEY = "feature1";
  private static final int FEATURE1_VERSION = 11;
  private static final DataModel.FeatureFlag FEATURE = flagBuilder(FEATURE1_KEY).version(FEATURE1_VERSION).build();
  private static final String SEGMENT1_KEY = "segment1";
  private static final int SEGMENT1_VERSION = 22;
  private static final DataModel.Segment SEGMENT = segmentBuilder(SEGMENT1_KEY).version(SEGMENT1_VERSION).build();
  private static final String STREAM_RESPONSE_WITH_EMPTY_DATA =
      "event: put\n" +
      "data: {\"data\":{\"flags\":{},\"segments\":{}}}\n\n";

  private InMemoryDataStore dataStore;
  private FeatureRequestor mockRequestor;
  private EventSource mockEventSource;
  private MockEventSourceCreator mockEventSourceCreator;

  @Before
  public void setup() {
    dataStore = new InMemoryDataStore();
    mockRequestor = createStrictMock(FeatureRequestor.class);
    mockEventSource = createMock(EventSource.class);
    mockEventSourceCreator = new MockEventSourceCreator(mockEventSource);
  }

  @Test
  public void builderHasDefaultConfiguration() throws Exception {
    DataSourceFactory f = Components.streamingDataSource();
    try (StreamProcessor sp = (StreamProcessor)f.createDataSource(clientContext(SDK_KEY, LDConfig.DEFAULT),
        dataStoreUpdates(dataStore))) {
      assertThat(sp.initialReconnectDelay, equalTo(StreamingDataSourceBuilder.DEFAULT_INITIAL_RECONNECT_DELAY));
      assertThat(sp.streamUri, equalTo(LDConfig.DEFAULT_STREAM_URI));
      assertThat(((DefaultFeatureRequestor)sp.requestor).baseUri, equalTo(LDConfig.DEFAULT_BASE_URI));
    }
  }

  @Test
  public void builderCanSpecifyConfiguration() throws Exception {
    URI streamUri = URI.create("http://fake");
    URI pollUri = URI.create("http://also-fake");
    DataSourceFactory f = Components.streamingDataSource()
        .baseURI(streamUri)
        .initialReconnectDelay(Duration.ofMillis(5555))
        .pollingBaseURI(pollUri);
    try (StreamProcessor sp = (StreamProcessor)f.createDataSource(clientContext(SDK_KEY, LDConfig.DEFAULT),
        dataStoreUpdates(dataStore))) {
      assertThat(sp.initialReconnectDelay, equalTo(Duration.ofMillis(5555)));
      assertThat(sp.streamUri, equalTo(streamUri));      
      assertThat(((DefaultFeatureRequestor)sp.requestor).baseUri, equalTo(pollUri));
    }
  }
  
  @Test
  public void streamUriHasCorrectEndpoint() {
    createStreamProcessor(STREAM_URI).start();
    assertEquals(URI.create(STREAM_URI.toString() + "/all"),
        mockEventSourceCreator.getNextReceivedParams().streamUri);
  }
  
  @Test
  public void headersHaveAuthorization() {
    createStreamProcessor(STREAM_URI).start();
    assertEquals(SDK_KEY,
        mockEventSourceCreator.getNextReceivedParams().headers.get("Authorization"));
  }
  
  @Test
  public void headersHaveUserAgent() {
    createStreamProcessor(STREAM_URI).start();
    assertEquals("JavaClient/" + LDClient.CLIENT_VERSION,
        mockEventSourceCreator.getNextReceivedParams().headers.get("User-Agent"));
  }

  @Test
  public void headersHaveAccept() {
    createStreamProcessor(STREAM_URI).start();
    assertEquals("text/event-stream",
        mockEventSourceCreator.getNextReceivedParams().headers.get("Accept"));
  }

  @Test
  public void headersHaveWrapperWhenSet() {
    LDConfig config = new LDConfig.Builder()
        .http(Components.httpConfiguration().wrapper("Scala", "0.1.0"))
        .build();
    createStreamProcessor(config, STREAM_URI).start();
    assertEquals("Scala/0.1.0",
        mockEventSourceCreator.getNextReceivedParams().headers.get("X-LaunchDarkly-Wrapper"));
  }

  @Test
  public void putCausesFeatureToBeStored() throws Exception {
    expectNoStreamRestart();
    
    createStreamProcessor(STREAM_URI).start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    
    MessageEvent event = new MessageEvent("{\"data\":{\"flags\":{\"" +
        FEATURE1_KEY + "\":" + featureJson(FEATURE1_KEY, FEATURE1_VERSION) + "}," +
        "\"segments\":{}}}");
    handler.onMessage("put", event);
    
    assertFeatureInStore(FEATURE);
  }

  @Test
  public void putCausesSegmentToBeStored() throws Exception {
    expectNoStreamRestart();
    
    createStreamProcessor(STREAM_URI).start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    
    MessageEvent event = new MessageEvent("{\"data\":{\"flags\":{},\"segments\":{\"" +
        SEGMENT1_KEY + "\":" + segmentJson(SEGMENT1_KEY, SEGMENT1_VERSION) + "}}}");
    handler.onMessage("put", event);
    
    assertSegmentInStore(SEGMENT);
  }
  
  @Test
  public void storeNotInitializedByDefault() throws Exception {
    createStreamProcessor(STREAM_URI).start();
    assertFalse(dataStore.isInitialized());
  }
  
  @Test
  public void putCausesStoreToBeInitialized() throws Exception {
    createStreamProcessor(STREAM_URI).start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("put", emptyPutEvent());
    assertTrue(dataStore.isInitialized());
  }

  @Test
  public void processorNotInitializedByDefault() throws Exception {
    StreamProcessor sp = createStreamProcessor(STREAM_URI);
    sp.start();
    assertFalse(sp.isInitialized());
  }
  
  @Test
  public void putCausesProcessorToBeInitialized() throws Exception {
    StreamProcessor sp = createStreamProcessor(STREAM_URI);
    sp.start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("put", emptyPutEvent());
    assertTrue(sp.isInitialized());
  }

  @Test
  public void futureIsNotSetByDefault() throws Exception {
    StreamProcessor sp = createStreamProcessor(STREAM_URI);
    Future<Void> future = sp.start();
    assertFalse(future.isDone());
  }

  @Test
  public void putCausesFutureToBeSet() throws Exception {
    StreamProcessor sp = createStreamProcessor(STREAM_URI);
    Future<Void> future = sp.start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("put", emptyPutEvent());
    assertTrue(future.isDone());
  }

  @Test
  public void patchUpdatesFeature() throws Exception {
    expectNoStreamRestart();
    
    createStreamProcessor(STREAM_URI).start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("put", emptyPutEvent());
    
    String path = "/flags/" + FEATURE1_KEY;
    MessageEvent event = new MessageEvent("{\"path\":\"" + path + "\",\"data\":" +
        featureJson(FEATURE1_KEY, FEATURE1_VERSION) + "}");
    handler.onMessage("patch", event);
    
    assertFeatureInStore(FEATURE);
  }

  @Test
  public void patchUpdatesSegment() throws Exception {
    expectNoStreamRestart();
    
    createStreamProcessor(STREAM_URI).start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("put", emptyPutEvent());
    
    String path = "/segments/" + SEGMENT1_KEY;
    MessageEvent event = new MessageEvent("{\"path\":\"" + path + "\",\"data\":" +
        segmentJson(SEGMENT1_KEY, SEGMENT1_VERSION) + "}");
    handler.onMessage("patch", event);
    
    assertSegmentInStore(SEGMENT);
  }

  @Test
  public void deleteDeletesFeature() throws Exception {
    expectNoStreamRestart();
    
    createStreamProcessor(STREAM_URI).start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("put", emptyPutEvent());
    upsertFlag(dataStore, FEATURE);
    
    String path = "/flags/" + FEATURE1_KEY;
    MessageEvent event = new MessageEvent("{\"path\":\"" + path + "\",\"version\":" +
        (FEATURE1_VERSION + 1) + "}");
    handler.onMessage("delete", event);
    
    assertEquals(ItemDescriptor.deletedItem(FEATURE1_VERSION + 1), dataStore.get(FEATURES, FEATURE1_KEY));
  }
  
  @Test
  public void deleteDeletesSegment() throws Exception {
    expectNoStreamRestart();
    
    createStreamProcessor(STREAM_URI).start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("put", emptyPutEvent());
    upsertSegment(dataStore, SEGMENT);
    
    String path = "/segments/" + SEGMENT1_KEY;
    MessageEvent event = new MessageEvent("{\"path\":\"" + path + "\",\"version\":" +
        (SEGMENT1_VERSION + 1) + "}");
    handler.onMessage("delete", event);
    
    assertEquals(ItemDescriptor.deletedItem(SEGMENT1_VERSION + 1), dataStore.get(SEGMENTS, SEGMENT1_KEY));
  }
  
  @Test
  public void indirectPutRequestsAndStoresFeature() throws Exception {
    setupRequestorToReturnAllDataWithFlag(FEATURE);
    expectNoStreamRestart();    
    replayAll();
    
    try (StreamProcessor sp = createStreamProcessor(STREAM_URI)) {
      sp.start();

      EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
      handler.onMessage("indirect/put", new MessageEvent(""));
    
      assertFeatureInStore(FEATURE);
    }
  }

  @Test
  public void indirectPutInitializesStore() throws Exception {
    createStreamProcessor(STREAM_URI).start();
    setupRequestorToReturnAllDataWithFlag(FEATURE);
    replayAll();
    
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("indirect/put", new MessageEvent(""));
    
    assertTrue(dataStore.isInitialized());
  }

  @Test
  public void indirectPutInitializesProcessor() throws Exception {
    StreamProcessor sp = createStreamProcessor(STREAM_URI);
    sp.start();
    setupRequestorToReturnAllDataWithFlag(FEATURE);
    replayAll();
    
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("indirect/put", new MessageEvent(""));
    
    assertTrue(dataStore.isInitialized());
  }

  @Test
  public void indirectPutSetsFuture() throws Exception {
    StreamProcessor sp = createStreamProcessor(STREAM_URI);
    Future<Void> future = sp.start();
    setupRequestorToReturnAllDataWithFlag(FEATURE);
    replayAll();
    
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("indirect/put", new MessageEvent(""));
    
    assertTrue(future.isDone());
  }
  
  @Test
  public void indirectPatchRequestsAndUpdatesFeature() throws Exception {    
    expect(mockRequestor.getFlag(FEATURE1_KEY)).andReturn(FEATURE);
    expectNoStreamRestart();
    replayAll();

    try (StreamProcessor sp = createStreamProcessor(STREAM_URI)) {
      sp.start();

      EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
      handler.onMessage("put", emptyPutEvent());
      handler.onMessage("indirect/patch", new MessageEvent("/flags/" + FEATURE1_KEY));
      
      assertFeatureInStore(FEATURE);
    }
  }

  @Test
  public void indirectPatchRequestsAndUpdatesSegment() throws Exception {
    expect(mockRequestor.getSegment(SEGMENT1_KEY)).andReturn(SEGMENT);
    expectNoStreamRestart();
    replayAll();
    
    try (StreamProcessor sp = createStreamProcessor(STREAM_URI)) {
      sp.start();

      EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
      handler.onMessage("put", emptyPutEvent());
      handler.onMessage("indirect/patch", new MessageEvent("/segments/" + SEGMENT1_KEY));
      
      assertSegmentInStore(SEGMENT);
    }
  }
  
  @Test
  public void unknownEventTypeDoesNotThrowException() throws Exception {
    createStreamProcessor(STREAM_URI).start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("what", new MessageEvent(""));
  }
  
  @Test
  public void streamWillReconnectAfterGeneralIOException() throws Exception {
    createStreamProcessor(STREAM_URI).start();
    ConnectionErrorHandler errorHandler = mockEventSourceCreator.getNextReceivedParams().errorHandler;
    ConnectionErrorHandler.Action action = errorHandler.onConnectionError(new IOException());
    assertEquals(ConnectionErrorHandler.Action.PROCEED, action);
  }

  @Test
  public void streamInitDiagnosticRecordedOnOpen() throws Exception {
    DiagnosticAccumulator acc = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
    long startTime = System.currentTimeMillis();
    createStreamProcessor(LDConfig.DEFAULT, STREAM_URI, acc).start();
    EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
    handler.onMessage("put", emptyPutEvent());
    long timeAfterOpen = System.currentTimeMillis();
    DiagnosticEvent.Statistics event = acc.createEventAndReset(0, 0);
    assertEquals(1, event.streamInits.size());
    DiagnosticEvent.StreamInit init = event.streamInits.get(0);
    assertFalse(init.failed);
    assertThat(init.timestamp, greaterThanOrEqualTo(startTime));
    assertThat(init.timestamp, lessThanOrEqualTo(timeAfterOpen));
    assertThat(init.durationMillis, lessThanOrEqualTo(timeAfterOpen - startTime));
  }

  @Test
  public void streamInitDiagnosticRecordedOnErrorDuringInit() throws Exception {
    DiagnosticAccumulator acc = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
    long startTime = System.currentTimeMillis();
    createStreamProcessor(LDConfig.DEFAULT, STREAM_URI, acc).start();
    ConnectionErrorHandler errorHandler = mockEventSourceCreator.getNextReceivedParams().errorHandler;
    errorHandler.onConnectionError(new IOException());
    long timeAfterOpen = System.currentTimeMillis();
    DiagnosticEvent.Statistics event = acc.createEventAndReset(0, 0);
    assertEquals(1, event.streamInits.size());
    DiagnosticEvent.StreamInit init = event.streamInits.get(0);
    assertTrue(init.failed);
    assertThat(init.timestamp, greaterThanOrEqualTo(startTime));
    assertThat(init.timestamp, lessThanOrEqualTo(timeAfterOpen));
    assertThat(init.durationMillis, lessThanOrEqualTo(timeAfterOpen - startTime));
  }

  @Test
  public void streamInitDiagnosticNotRecordedOnErrorAfterInit() throws Exception {
    DiagnosticAccumulator acc = new DiagnosticAccumulator(new DiagnosticId(SDK_KEY));
    createStreamProcessor(LDConfig.DEFAULT, STREAM_URI, acc).start();
    StreamProcessor.EventSourceParams params = mockEventSourceCreator.getNextReceivedParams(); 
    params.handler.onMessage("put", emptyPutEvent());
    // Drop first stream init from stream open
    acc.createEventAndReset(0, 0);
    params.errorHandler.onConnectionError(new IOException());
    DiagnosticEvent.Statistics event = acc.createEventAndReset(0, 0);
    assertEquals(0, event.streamInits.size());
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
    verifyEventCausesStreamRestartWithInMemoryStore("put", "{sorry");
  }

  @Test
  public void putEventWithWellFormedJsonButInvalidDataCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestartWithInMemoryStore("put", "{\"data\":{\"flags\":3}}");
  }

  @Test
  public void patchEventWithInvalidJsonCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestartWithInMemoryStore("patch", "{sorry");
  }

  @Test
  public void patchEventWithWellFormedJsonButInvalidDataCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestartWithInMemoryStore("patch", "{\"path\":\"/flags/flagkey\", \"data\":{\"rules\":3}}");
  }

  @Test
  public void patchEventWithInvalidPathCausesNoStreamRestart() throws Exception {
    verifyEventCausesNoStreamRestart("patch", "{\"path\":\"/wrong\", \"data\":{\"key\":\"flagkey\"}}");
  }

  @Test
  public void deleteEventWithInvalidJsonCausesStreamRestart() throws Exception {
    verifyEventCausesStreamRestartWithInMemoryStore("delete", "{sorry");
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
  public void indirectPutWithFailedPollCausesStreamRestart() throws Exception {
    expect(mockRequestor.getAllData()).andThrow(new IOException("sorry"));
    verifyEventCausesStreamRestartWithInMemoryStore("indirect/put", "");
  }

  @Test
  public void indirectPatchWithFailedPollCausesStreamRestart() throws Exception {
    expect(mockRequestor.getFlag("flagkey")).andThrow(new IOException("sorry"));
    verifyEventCausesStreamRestartWithInMemoryStore("indirect/patch", "/flags/flagkey");
  }
  
  @Test
  public void restartsStreamIfStoreNeedsRefresh() throws Exception {
    MockDataStoreStatusProvider dataStoreStatusProvider = new MockDataStoreStatusProvider();
    DataStoreUpdates storeUpdates = new DataStoreUpdatesImpl(dataStore, null, dataStoreStatusProvider);
    
    CompletableFuture<Void> restarted = new CompletableFuture<>();
    mockEventSource.start();
    expectLastCall();
    mockEventSource.restart();
    expectLastCall().andAnswer(() -> {
      restarted.complete(null);
      return null;
    });
    mockEventSource.close();
    expectLastCall();
    mockRequestor.close();
    expectLastCall();
    
    replayAll();
    
    try (StreamProcessor sp = createStreamProcessorWithStoreUpdates(storeUpdates)) {
      sp.start();
      
      dataStoreStatusProvider.updateStatus(new DataStoreStatusProvider.Status(false, false));
      dataStoreStatusProvider.updateStatus(new DataStoreStatusProvider.Status(true, true));

      restarted.get();
    }
  }

  @Test
  public void doesNotRestartStreamIfStoreHadOutageButDoesNotNeedRefresh() throws Exception {
    MockDataStoreStatusProvider dataStoreStatusProvider = new MockDataStoreStatusProvider();
    DataStoreUpdates storeUpdates = new DataStoreUpdatesImpl(dataStore, null, dataStoreStatusProvider);
    
    CompletableFuture<Void> restarted = new CompletableFuture<>();
    mockEventSource.start();
    expectLastCall();
    mockEventSource.restart();
    expectLastCall().andAnswer(() -> {
      restarted.complete(null);
      return null;
    });
    mockEventSource.close();
    expectLastCall();
    mockRequestor.close();
    expectLastCall();
    
    replayAll();
    
    try (StreamProcessor sp = createStreamProcessorWithStoreUpdates(storeUpdates)) {
      sp.start();
      
      dataStoreStatusProvider.updateStatus(new DataStoreStatusProvider.Status(false, false));
      dataStoreStatusProvider.updateStatus(new DataStoreStatusProvider.Status(true, false));

      Thread.sleep(500);
      assertFalse(restarted.isDone());
    }
  }

  @Test
  public void storeFailureOnPutCausesStreamRestart() throws Exception {
    DataStore badStore = dataStoreThatThrowsException(new RuntimeException("sorry"));
    expectStreamRestart();
    replayAll();

    try (StreamProcessor sp = createStreamProcessorWithStore(badStore)) {
      sp.start();
      EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
      handler.onMessage("put", emptyPutEvent());
    }    
    verifyAll();
  }

  @Test
  public void storeFailureOnPatchCausesStreamRestart() throws Exception {
    DataStore badStore = dataStoreThatThrowsException(new RuntimeException("sorry"));
    expectStreamRestart();
    replayAll();
    
    try (StreamProcessor sp = createStreamProcessorWithStore(badStore)) {
      sp.start();
      EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
      handler.onMessage("patch",
          new MessageEvent("{\"path\":\"/flags/flagkey\",\"data\":{\"key\":\"flagkey\",\"version\":1}}"));
    }    
    verifyAll();
  }

  @Test
  public void storeFailureOnDeleteCausesStreamRestart() throws Exception {
    DataStore badStore = dataStoreThatThrowsException(new RuntimeException("sorry"));    
    expectStreamRestart();
    replayAll();
    
    try (StreamProcessor sp = createStreamProcessorWithStore(badStore)) {
      sp.start();
      EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
      handler.onMessage("delete",
          new MessageEvent("{\"path\":\"/flags/flagkey\",\"version\":1}"));
    }    
    verifyAll();
  }

  @Test
  public void storeFailureOnIndirectPutCausesStreamRestart() throws Exception {
    DataStore badStore = dataStoreThatThrowsException(new RuntimeException("sorry"));
    setupRequestorToReturnAllDataWithFlag(FEATURE);
    expectStreamRestart();
    replayAll();
    
    try (StreamProcessor sp = createStreamProcessorWithStore(badStore)) {
      sp.start();
      EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
      handler.onMessage("indirect/put", new MessageEvent(""));
    }    
    verifyAll();
  }

  @Test
  public void storeFailureOnIndirectPatchCausesStreamRestart() throws Exception {
    DataStore badStore = dataStoreThatThrowsException(new RuntimeException("sorry"));
    setupRequestorToReturnAllDataWithFlag(FEATURE);
    
    expectStreamRestart();
    replayAll();
    
    try (StreamProcessor sp = createStreamProcessorWithStore(badStore)) {
      sp.start();
      EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
      handler.onMessage("indirect/put", new MessageEvent(""));
    }    
    verifyAll();
  }

  private void verifyEventCausesNoStreamRestart(String eventName, String eventData) throws Exception {
    expectNoStreamRestart();
    verifyEventBehavior(eventName, eventData);
  }
  
  private void verifyEventCausesStreamRestartWithInMemoryStore(String eventName, String eventData) throws Exception {
    expectStreamRestart();
    verifyEventBehavior(eventName, eventData);
  }
  
  private void verifyEventBehavior(String eventName, String eventData) throws Exception {
    replayAll();
    try (StreamProcessor sp = createStreamProcessor(LDConfig.DEFAULT, STREAM_URI, null)) {
      sp.start();
      EventHandler handler = mockEventSourceCreator.getNextReceivedParams().handler;
      handler.onMessage(eventName, new MessageEvent(eventData));
    }    
    verifyAll();
  }
  
  private void expectNoStreamRestart() throws Exception {
    mockEventSource.start();
    expectLastCall().times(1);
    mockEventSource.close();
    expectLastCall().times(1);
    mockRequestor.close();
    expectLastCall().times(1);
  }
  
  private void expectStreamRestart() throws Exception {
    mockEventSource.start();
    expectLastCall().times(1);
    mockEventSource.restart();
    expectLastCall().times(1);
    mockEventSource.close();
    expectLastCall().times(1);
    mockRequestor.close();
    expectLastCall().times(1);
  }
  
  // There are already end-to-end tests against an HTTP server in okhttp-eventsource, so we won't retest the
  // basic stream mechanism in detail. However, we do want to make sure that the LDConfig options are correctly
  // applied to the EventSource for things like TLS configuration.
  
  @Test
  public void httpClientDoesNotAllowSelfSignedCertByDefault() throws Exception {
    final ConnectionErrorSink errorSink = new ConnectionErrorSink();
    try (TestHttpUtil.ServerWithCert server = new TestHttpUtil.ServerWithCert()) {
      server.server.enqueue(eventStreamResponse(STREAM_RESPONSE_WITH_EMPTY_DATA));

      try (StreamProcessor sp = createStreamProcessorWithRealHttp(LDConfig.DEFAULT, server.uri())) {
        sp.connectionErrorHandler = errorSink;
        Future<Void> ready = sp.start();
        ready.get();
        
        Throwable error = errorSink.errors.peek();
        assertNotNull(error);
        assertEquals(SSLHandshakeException.class, error.getClass());
      }
    }
  }
  
  @Test
  public void httpClientCanUseCustomTlsConfig() throws Exception {
    final ConnectionErrorSink errorSink = new ConnectionErrorSink();
    try (TestHttpUtil.ServerWithCert server = new TestHttpUtil.ServerWithCert()) {
      server.server.enqueue(eventStreamResponse(STREAM_RESPONSE_WITH_EMPTY_DATA));
      
      LDConfig config = new LDConfig.Builder()
          .http(Components.httpConfiguration().sslSocketFactory(server.socketFactory, server.trustManager))
          // allows us to trust the self-signed cert
          .build();
      
      try (StreamProcessor sp = createStreamProcessorWithRealHttp(config, server.uri())) {
        sp.connectionErrorHandler = errorSink;
        Future<Void> ready = sp.start();
        ready.get();
        
        assertNull(errorSink.errors.peek());
      }
    }
  }

  @Test
  public void httpClientCanUseProxyConfig() throws Exception {
    final ConnectionErrorSink errorSink = new ConnectionErrorSink();
    URI fakeStreamUri = URI.create("http://not-a-real-host");
    try (MockWebServer server = makeStartedServer(eventStreamResponse(STREAM_RESPONSE_WITH_EMPTY_DATA))) {
      HttpUrl serverUrl = server.url("/");
      LDConfig config = new LDConfig.Builder()
          .http(Components.httpConfiguration().proxyHostAndPort(serverUrl.host(), serverUrl.port()))
          .build();
      
      try (StreamProcessor sp = createStreamProcessorWithRealHttp(config, fakeStreamUri)) {
        sp.connectionErrorHandler = errorSink;
        Future<Void> ready = sp.start();
        ready.get();
        
        assertNull(errorSink.errors.peek());
        assertEquals(1, server.getRequestCount());
      }
    }
  }
  
  static class ConnectionErrorSink implements ConnectionErrorHandler {
    final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();
    
    public Action onConnectionError(Throwable t) {
      errors.add(t);
      return Action.SHUTDOWN;
    }
  }
  
  private void testUnrecoverableHttpError(int status) throws Exception {
    UnsuccessfulResponseException e = new UnsuccessfulResponseException(status);
    long startTime = System.currentTimeMillis();
    StreamProcessor sp = createStreamProcessor(STREAM_URI);
    Future<Void> initFuture = sp.start();
    
    ConnectionErrorHandler errorHandler = mockEventSourceCreator.getNextReceivedParams().errorHandler;
    ConnectionErrorHandler.Action action = errorHandler.onConnectionError(e);
    assertEquals(ConnectionErrorHandler.Action.SHUTDOWN, action);
    
    try {
      initFuture.get(10, TimeUnit.SECONDS);
    } catch (TimeoutException ignored) {
      fail("Should not have timed out");
    }
    assertTrue((System.currentTimeMillis() - startTime) < 9000);
    assertTrue(initFuture.isDone());
    assertFalse(sp.isInitialized());
  }
  
  private void testRecoverableHttpError(int status) throws Exception {
    UnsuccessfulResponseException e = new UnsuccessfulResponseException(status);
    long startTime = System.currentTimeMillis();
    StreamProcessor sp = createStreamProcessor(STREAM_URI);
    Future<Void> initFuture = sp.start();
    
    ConnectionErrorHandler errorHandler = mockEventSourceCreator.getNextReceivedParams().errorHandler;
    ConnectionErrorHandler.Action action = errorHandler.onConnectionError(e);
    assertEquals(ConnectionErrorHandler.Action.PROCEED, action);
    
    try {
      initFuture.get(200, TimeUnit.MILLISECONDS);
      fail("Expected timeout");
    } catch (TimeoutException ignored) {
    }
    assertTrue((System.currentTimeMillis() - startTime) >= 200);
    assertFalse(initFuture.isDone());
    assertFalse(sp.isInitialized());
  }
  
  private StreamProcessor createStreamProcessor(URI streamUri) {
    return createStreamProcessor(LDConfig.DEFAULT, streamUri, null);
  }

  private StreamProcessor createStreamProcessor(LDConfig config, URI streamUri) {
    return createStreamProcessor(config, streamUri, null);
  }

  private StreamProcessor createStreamProcessor(LDConfig config, URI streamUri, DiagnosticAccumulator diagnosticAccumulator) {
    return new StreamProcessor(SDK_KEY, config.httpConfig, mockRequestor, dataStoreUpdates(dataStore),
        mockEventSourceCreator, diagnosticAccumulator,
        streamUri, DEFAULT_INITIAL_RECONNECT_DELAY);
  }

  private StreamProcessor createStreamProcessorWithRealHttp(LDConfig config, URI streamUri) {
    return new StreamProcessor(SDK_KEY, config.httpConfig, mockRequestor, dataStoreUpdates(dataStore), null, null,
        streamUri, DEFAULT_INITIAL_RECONNECT_DELAY);
  }

  private StreamProcessor createStreamProcessorWithStore(DataStore store) {
    return createStreamProcessorWithStoreUpdates(dataStoreUpdates(store));
  }

  private StreamProcessor createStreamProcessorWithStoreUpdates(DataStoreUpdates storeUpdates) {
    return new StreamProcessor(SDK_KEY, LDConfig.DEFAULT.httpConfig, mockRequestor, storeUpdates,
        mockEventSourceCreator, null, STREAM_URI, DEFAULT_INITIAL_RECONNECT_DELAY);
  }

  private String featureJson(String key, int version) {
    return gsonInstance().toJson(flagBuilder(key).version(version).build());
  }
  
  private String segmentJson(String key, int version) {
    return gsonInstance().toJson(ModelBuilders.segmentBuilder(key).version(version).build());
  }
  
  private MessageEvent emptyPutEvent() {
    return new MessageEvent("{\"data\":{\"flags\":{},\"segments\":{}}}");
  }
  
  private void setupRequestorToReturnAllDataWithFlag(DataModel.FeatureFlag feature) throws Exception {
    FeatureRequestor.AllData data = new FeatureRequestor.AllData(
        Collections.singletonMap(feature.getKey(), feature), Collections.emptyMap());
    expect(mockRequestor.getAllData()).andReturn(data);
  }
  
  private void assertFeatureInStore(DataModel.FeatureFlag feature) {
    assertEquals(feature.getVersion(), dataStore.get(FEATURES, feature.getKey()).getVersion());
  }
  
  private void assertSegmentInStore(DataModel.Segment segment) {
    assertEquals(segment.getVersion(), dataStore.get(SEGMENTS, segment.getKey()).getVersion());
  }
}
