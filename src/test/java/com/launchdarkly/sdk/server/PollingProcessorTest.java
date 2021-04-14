package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.TestComponents.MockDataSourceUpdates;
import com.launchdarkly.sdk.server.TestComponents.MockDataStoreStatusProvider;
import com.launchdarkly.sdk.server.TestUtil.ActionCanThrowAnyException;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.SerializationException;

import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.dataStoreThatThrowsException;
import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.sdk.server.TestUtil.awaitValue;
import static com.launchdarkly.sdk.server.TestUtil.expectNoMoreValues;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatus;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatusEventually;
import static com.launchdarkly.sdk.server.TestUtil.shouldNotTimeOut;
import static com.launchdarkly.sdk.server.TestUtil.shouldTimeOut;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class PollingProcessorTest {
  private static final String SDK_KEY = "sdk-key";
  private static final Duration LENGTHY_INTERVAL = Duration.ofSeconds(60);
  private static final Duration BRIEF_INTERVAL = Duration.ofMillis(20);

  private MockDataSourceUpdates dataSourceUpdates;
  private MockFeatureRequestor requestor;

  @Before
  public void setup() {
    DataStore store = new InMemoryDataStore();
    dataSourceUpdates = TestComponents.dataSourceUpdates(store, new MockDataStoreStatusProvider());
    requestor = new MockFeatureRequestor();
  }

  private PollingProcessor makeProcessor() {
    return makeProcessor(LENGTHY_INTERVAL);
  }

  private PollingProcessor makeProcessor(Duration pollInterval) {
    return new PollingProcessor(requestor, dataSourceUpdates, sharedExecutor, pollInterval);
  }

  @Test
  public void builderHasDefaultConfiguration() throws Exception {
    DataSourceFactory f = Components.pollingDataSource();
    try (PollingProcessor pp = (PollingProcessor)f.createDataSource(clientContext(SDK_KEY, LDConfig.DEFAULT), null)) {
      assertThat(((DefaultFeatureRequestor)pp.requestor).baseUri, equalTo(LDConfig.DEFAULT_BASE_URI));
      assertThat(pp.pollInterval, equalTo(PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL));
    }
  }

  @Test
  public void builderCanSpecifyConfiguration() throws Exception {
    URI uri = URI.create("http://fake");
    DataSourceFactory f = Components.pollingDataSource()
        .baseURI(uri)
        .pollInterval(LENGTHY_INTERVAL);
    try (PollingProcessor pp = (PollingProcessor)f.createDataSource(clientContext(SDK_KEY, LDConfig.DEFAULT), null)) {
      assertThat(((DefaultFeatureRequestor)pp.requestor).baseUri, equalTo(uri));
      assertThat(pp.pollInterval, equalTo(LENGTHY_INTERVAL));
    }
  }
  
  @Test
  public void successfulPolls() throws Exception {
    FeatureFlag flagv1 = ModelBuilders.flagBuilder("flag").version(1).build();
    FeatureFlag flagv2 = ModelBuilders.flagBuilder(flagv1.getKey()).version(2).build();
    FeatureRequestor.AllData datav1 = new FeatureRequestor.AllData(Collections.singletonMap(flagv1.getKey(), flagv1),
        Collections.emptyMap()); 
    FeatureRequestor.AllData datav2 = new FeatureRequestor.AllData(Collections.singletonMap(flagv1.getKey(), flagv2),
        Collections.emptyMap()); 

    requestor.gate = new Semaphore(0);
    requestor.allData = datav1;
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (PollingProcessor pollingProcessor = makeProcessor(Duration.ofMillis(100))) {    
      Future<Void> initFuture = pollingProcessor.start();
      
      // allow first poll to complete
      requestor.gate.release();
      
      initFuture.get(1000, TimeUnit.MILLISECONDS);

      assertTrue(pollingProcessor.isInitialized());
      assertEquals(datav1.toFullDataSet(), dataSourceUpdates.awaitInit());
      
      // allow second poll to complete - should return new data
      requestor.allData = datav2;
      requestor.gate.release();

      requireDataSourceStatus(statuses, State.VALID);

      assertEquals(datav2.toFullDataSet(), dataSourceUpdates.awaitInit());
    }
  }

  @Test
  public void testConnectionProblem() throws Exception {
    requestor.ioException = new IOException("This exception is part of a test and yes you should be seeing it.");

    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (PollingProcessor pollingProcessor = makeProcessor()) {
      Future<Void> initFuture = pollingProcessor.start();
      try {
        initFuture.get(200L, TimeUnit.MILLISECONDS);
        fail("Expected Timeout, instead initFuture.get() returned.");
      } catch (TimeoutException ignored) {
      }
      assertFalse(initFuture.isDone());
      assertFalse(pollingProcessor.isInitialized());
      assertEquals(0, dataSourceUpdates.receivedInits.size());
      
      Status status = requireDataSourceStatus(statuses, State.INITIALIZING);
      assertNotNull(status.getLastError());
      assertEquals(ErrorKind.NETWORK_ERROR, status.getLastError().getKind());
    }
  }

  @Test
  public void testDataStoreFailure() throws Exception {
    DataStore badStore = dataStoreThatThrowsException(new RuntimeException("sorry"));
    DataStoreStatusProvider badStoreStatusProvider = new MockDataStoreStatusProvider(false);
    dataSourceUpdates = TestComponents.dataSourceUpdates(badStore, badStoreStatusProvider);

    requestor.allData = new FeatureRequestor.AllData(new HashMap<>(), new HashMap<>());
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (PollingProcessor pollingProcessor = makeProcessor()) {  
      pollingProcessor.start();
      
      assertEquals(requestor.allData.toFullDataSet(), dataSourceUpdates.awaitInit());

      assertFalse(pollingProcessor.isInitialized());
      
      Status status = requireDataSourceStatus(statuses, State.INITIALIZING);
      assertNotNull(status.getLastError());
      assertEquals(ErrorKind.STORE_ERROR, status.getLastError().getKind());
    }
  }
  
  @Test
  public void testMalformedData() throws Exception {
    requestor.runtimeException = new SerializationException(new Exception("the JSON was displeasing"));
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (PollingProcessor pollingProcessor = makeProcessor()) {  
      pollingProcessor.start();
      
      Status status = requireDataSourceStatus(statuses, State.INITIALIZING);
      assertNotNull(status.getLastError());
      assertEquals(ErrorKind.INVALID_DATA, status.getLastError().getKind());
      assertEquals(requestor.runtimeException.toString(), status.getLastError().getMessage());

      assertFalse(pollingProcessor.isInitialized());
    }
  }

  @Test
  public void testUnknownException() throws Exception {
    requestor.runtimeException = new RuntimeException("everything is displeasing");
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (PollingProcessor pollingProcessor = makeProcessor()) {  
      pollingProcessor.start();
      
      Status status = requireDataSourceStatus(statuses, State.INITIALIZING);
      assertNotNull(status.getLastError());
      assertEquals(ErrorKind.UNKNOWN, status.getLastError().getKind());
      assertEquals(requestor.runtimeException.toString(), status.getLastError().getMessage());

      assertFalse(pollingProcessor.isInitialized());
    }
  }
  
  @Test
  public void startingWhenAlreadyStartedDoesNothing() throws Exception {
    requestor.allData = new FeatureRequestor.AllData(new HashMap<>(), new HashMap<>());

    try (PollingProcessor pollingProcessor = makeProcessor(Duration.ofMillis(500))) {  
      Future<Void> initFuture1 = pollingProcessor.start();
      
      awaitValue(requestor.queries, Duration.ofMillis(100)); // a poll request was made
      
      Future<Void> initFuture2 = pollingProcessor.start();
      assertSame(initFuture1, initFuture2);
      
      
      expectNoMoreValues(requestor.queries, Duration.ofMillis(100)); // we did NOT start another polling task
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
  
  private void testUnrecoverableHttpError(int statusCode) throws Exception {
    HttpErrorException httpError = new HttpErrorException(statusCode);

    // Test a scenario where the very first request gets this error
    withStatusQueue(statuses -> {
      requestor.httpException = httpError;
      
      try (PollingProcessor pollingProcessor = makeProcessor()) {
        long startTime = System.currentTimeMillis();
        Future<Void> initFuture = pollingProcessor.start();
        
        shouldNotTimeOut(initFuture, Duration.ofSeconds(2));
        assertTrue((System.currentTimeMillis() - startTime) < 9000);
        assertTrue(initFuture.isDone());
        assertFalse(pollingProcessor.isInitialized());
        
        verifyHttpErrorCausedShutdown(statuses, statusCode);
      }
    });
    
    // Now test a scenario where we have a successful startup, but the next poll gets the error
    dataSourceUpdates = TestComponents.dataSourceUpdates(new InMemoryDataStore(), new MockDataStoreStatusProvider());
    withStatusQueue(statuses -> {
      requestor = new MockFeatureRequestor();    
      requestor.allData = new FeatureRequestor.AllData(new HashMap<>(), new HashMap<>());

      try (PollingProcessor pollingProcessor = makeProcessor(BRIEF_INTERVAL)) {
        Future<Void> initFuture = pollingProcessor.start();
        
        shouldNotTimeOut(initFuture, Duration.ofSeconds(20000));
        assertTrue(initFuture.isDone());
        assertTrue(pollingProcessor.isInitialized());
        requireDataSourceStatusEventually(statuses, State.VALID, State.INITIALIZING);
  
        // cause the next poll to get an error
        requestor.httpException = httpError;
        
        verifyHttpErrorCausedShutdown(statuses, statusCode);
      }
    });
  }
  
  private void verifyHttpErrorCausedShutdown(BlockingQueue<DataSourceStatusProvider.Status> statuses, int statusCode) {
    Status status = requireDataSourceStatusEventually(statuses, State.OFF, State.VALID);
    assertNotNull(status.getLastError());
    assertEquals(ErrorKind.ERROR_RESPONSE, status.getLastError().getKind());
    assertEquals(statusCode, status.getLastError().getStatusCode());
  }
  
  private void testRecoverableHttpError(int statusCode) throws Exception {
    HttpErrorException httpError = new HttpErrorException(statusCode);

    // Test a scenario where the very first request gets this error
    withStatusQueue(statuses -> {
      requestor.httpException = httpError;
      
      try (PollingProcessor pollingProcessor = makeProcessor(BRIEF_INTERVAL)) {
        Future<Void> initFuture = pollingProcessor.start();
        
        // first poll gets an error
        shouldTimeOut(initFuture, Duration.ofMillis(200));
        assertFalse(initFuture.isDone());
        assertFalse(pollingProcessor.isInitialized());
        
        Status status0 = requireDataSourceStatus(statuses, State.INITIALIZING);
        assertNotNull(status0.getLastError());
        assertEquals(ErrorKind.ERROR_RESPONSE, status0.getLastError().getKind());
        assertEquals(statusCode, status0.getLastError().getStatusCode());

        verifyHttpErrorWasRecoverable(statuses, statusCode, false);
        
        shouldNotTimeOut(initFuture, Duration.ofSeconds(2));
        assertTrue(initFuture.isDone());
        assertTrue(pollingProcessor.isInitialized());
      }
    });
    
    // Now test a scenario where we have a successful startup, but the next poll gets the error
    dataSourceUpdates = TestComponents.dataSourceUpdates(new InMemoryDataStore(), new MockDataStoreStatusProvider());
    withStatusQueue(statuses -> {
      requestor = new MockFeatureRequestor();
      requestor.allData = new FeatureRequestor.AllData(new HashMap<>(), new HashMap<>());
      
      try (PollingProcessor pollingProcessor = makeProcessor(BRIEF_INTERVAL)) {
        Future<Void> initFuture = pollingProcessor.start();
        
        shouldNotTimeOut(initFuture, Duration.ofSeconds(20000));
        assertTrue(initFuture.isDone());
        assertTrue(pollingProcessor.isInitialized());
        requireDataSourceStatusEventually(statuses, State.VALID, State.INITIALIZING);
  
        // cause the next poll to get an error
        requestor.httpException = httpError;
       
        Status status0 = requireDataSourceStatusEventually(statuses, State.INTERRUPTED, State.VALID);
        assertEquals(ErrorKind.ERROR_RESPONSE, status0.getLastError().getKind());
        assertEquals(statusCode, status0.getLastError().getStatusCode());
        
        verifyHttpErrorWasRecoverable(statuses, statusCode, true);
      }
    });
  }

  private void verifyHttpErrorWasRecoverable(
      BlockingQueue<DataSourceStatusProvider.Status> statuses,
      int statusCode,
      boolean didAlreadyConnect
      ) throws Exception {
    long startTime = System.currentTimeMillis();
    
    // first make it so the requestor will succeed after the previous error
    requestor.allData = new FeatureRequestor.AllData(new HashMap<>(), new HashMap<>());
    requestor.httpException = null;
    
    // status should now be VALID (although there might have been more failed polls before that)
    Status status1 = requireDataSourceStatusEventually(statuses, State.VALID,
        didAlreadyConnect ? State.INTERRUPTED : State.INITIALIZING);
    assertNotNull(status1.getLastError());
    assertEquals(ErrorKind.ERROR_RESPONSE, status1.getLastError().getKind());
    assertEquals(statusCode, status1.getLastError().getStatusCode());
    
    // simulate another error of the same kind - the state will be INTERRUPTED
    requestor.httpException = new HttpErrorException(statusCode);
    
    Status status2 = requireDataSourceStatusEventually(statuses, State.INTERRUPTED, State.VALID);
    assertNotNull(status2.getLastError());
    assertEquals(ErrorKind.ERROR_RESPONSE, status2.getLastError().getKind());
    assertEquals(statusCode, status2.getLastError().getStatusCode());
    MatcherAssert.assertThat(status2.getLastError().getTime().toEpochMilli(), greaterThanOrEqualTo(startTime));
  }
  
  private void withStatusQueue(ActionCanThrowAnyException<BlockingQueue<DataSourceStatusProvider.Status>> action) throws Exception {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    DataSourceStatusProvider.StatusListener addStatus = statuses::add;
    dataSourceUpdates.statusBroadcaster.register(addStatus);
    try {
      action.apply(statuses);
    } finally {
      dataSourceUpdates.statusBroadcaster.unregister(addStatus);
    }
  }
  
  private static class MockFeatureRequestor implements FeatureRequestor {
    volatile AllData allData;
    volatile HttpErrorException httpException;
    volatile IOException ioException;
    volatile RuntimeException runtimeException;
    volatile Semaphore gate;
    final BlockingQueue<Boolean> queries = new LinkedBlockingQueue<>();
    
    public void close() throws IOException {}

    public AllData getAllData(boolean returnDataEvenIfCached) throws IOException, HttpErrorException {
      queries.add(true);
      if (gate != null) {
        try {
          gate.acquire();
        } catch (InterruptedException e) {}
      }
      if (httpException != null) {
        throw httpException;
      }
      if (ioException != null) {
        throw ioException;
      }
      if (runtimeException != null) {
        throw runtimeException;
      }
      return allData;
    }
  }
}
