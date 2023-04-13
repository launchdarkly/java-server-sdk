package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.TestComponents.MockDataSourceUpdates;
import com.launchdarkly.sdk.server.TestComponents.MockDataStoreStatusProvider;
import com.launchdarkly.sdk.server.TestUtil.ActionCanThrowAnyException;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.testhelpers.ConcurrentHelpers;
import com.launchdarkly.testhelpers.httptest.Handler;
import com.launchdarkly.testhelpers.httptest.Handlers;
import com.launchdarkly.testhelpers.httptest.HttpServer;
import com.launchdarkly.testhelpers.httptest.RequestContext;
import com.launchdarkly.testhelpers.tcptest.TcpHandler;
import com.launchdarkly.testhelpers.tcptest.TcpHandlers;
import com.launchdarkly.testhelpers.tcptest.TcpServer;

import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.dataStoreThatThrowsException;
import static com.launchdarkly.sdk.server.TestComponents.defaultHttpProperties;
import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.sdk.server.TestUtil.assertDataSetEquals;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatus;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatusEventually;
import static com.launchdarkly.testhelpers.ConcurrentHelpers.assertFutureIsCompleted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class PollingProcessorTest extends BaseTest {
  private static final String SDK_KEY = "sdk-key";
  private static final Duration LENGTHY_INTERVAL = Duration.ofSeconds(60);
  private static final Duration BRIEF_INTERVAL = Duration.ofMillis(20);

  private MockDataSourceUpdates dataSourceUpdates;

  @Before
  public void setup() {
    DataStore store = new InMemoryDataStore();
    dataSourceUpdates = TestComponents.dataSourceUpdates(store, new MockDataStoreStatusProvider());
  }

  private PollingProcessor makeProcessor(URI baseUri, Duration pollInterval) {
    FeatureRequestor requestor = new DefaultFeatureRequestor(defaultHttpProperties(), baseUri, null, testLogger);
    return new PollingProcessor(requestor, dataSourceUpdates, sharedExecutor, pollInterval, testLogger);
  }

  private static class TestPollHandler implements Handler {
    private final String data;
    private volatile int errorStatus;

    public TestPollHandler() {
      this(DataBuilder.forStandardTypes()); 
    }
    
    public TestPollHandler(DataBuilder data) {
      this.data = data.buildJson().toJsonString(); 
    }
    
    @Override
    public void apply(RequestContext context) {
      int err = errorStatus;
      if (err == 0) {
        Handlers.bodyJson(data).apply(context);
      } else {
        context.setStatus(err);
      }
    }

    public void setError(int status) {
      this.errorStatus = status;
    }
  }
  
  @Test
  public void builderHasDefaultConfiguration() throws Exception {
    ComponentConfigurer<DataSource> f = Components.pollingDataSource();
    try (PollingProcessor pp = (PollingProcessor)f.build(clientContext(SDK_KEY, baseConfig().build()))) {
      assertThat(((DefaultFeatureRequestor)pp.requestor).pollingUri.toString(), containsString(StandardEndpoints.DEFAULT_POLLING_BASE_URI.toString()));
      assertThat(pp.pollInterval, equalTo(PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL));
    }
  }

  @Test
  public void builderCanSpecifyConfiguration() throws Exception {

    ComponentConfigurer<DataSource> f = Components.pollingDataSource()
        .pollInterval(LENGTHY_INTERVAL)
        .payloadFilter("myFilter");

    try (PollingProcessor pp = (PollingProcessor) f.build(
        clientContext(
            SDK_KEY,
            baseConfig().build()))) {
      assertThat(pp.pollInterval, equalTo(LENGTHY_INTERVAL));
      assertThat(((DefaultFeatureRequestor) pp.requestor).pollingUri.toString(), containsString("filter=myFilter"));
    }
  }
  
  @Test
  public void successfulPolls() throws Exception {
    FeatureFlag flagv1 = ModelBuilders.flagBuilder("flag").version(1).build();
    FeatureFlag flagv2 = ModelBuilders.flagBuilder(flagv1.getKey()).version(2).build();
    DataBuilder datav1 = DataBuilder.forStandardTypes().addAny(DataModel.FEATURES, flagv1);
    DataBuilder datav2 = DataBuilder.forStandardTypes().addAny(DataModel.FEATURES, flagv2);

    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    Semaphore allowSecondPollToProceed = new Semaphore(0);
    
    Handler pollingHandler = Handlers.sequential(
        new TestPollHandler(datav1),
        Handlers.all(
            Handlers.waitFor(allowSecondPollToProceed),
            new TestPollHandler(datav2)
            ),
        Handlers.hang() // we don't want any more polls to complete after the second one
        );
    
    try (HttpServer server = HttpServer.start(pollingHandler)) {
      try (PollingProcessor pollingProcessor = makeProcessor(server.getUri(), Duration.ofMillis(100))) {
        Future<Void> initFuture = pollingProcessor.start();
        assertFutureIsCompleted(initFuture, 1, TimeUnit.SECONDS);
       
        assertTrue(pollingProcessor.isInitialized());
        assertDataSetEquals(datav1.build(), dataSourceUpdates.awaitInit());

        allowSecondPollToProceed.release();
        
        assertDataSetEquals(datav2.build(), dataSourceUpdates.awaitInit());
      }
    }
  }

  @Test
  public void testTimeoutFromConnectionProblem() throws Exception {
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    Handler successHandler = new TestPollHandler(); // it should time out before reaching this
    
    try (HttpServer server = HttpServer.start(successHandler)) {
      TcpHandler errorThenSuccess = TcpHandlers.sequential(
          TcpHandlers.noResponse(), // this will cause an IOException due to closing the connection without a response
          TcpHandlers.forwardToPort(server.getPort())
          );
      try (TcpServer forwardingServer = TcpServer.start(errorThenSuccess)) {
        try (PollingProcessor pollingProcessor = makeProcessor(forwardingServer.getHttpUri(), LENGTHY_INTERVAL)) {
          Future<Void> initFuture = pollingProcessor.start();
          ConcurrentHelpers.assertFutureIsNotCompleted(initFuture, 200, TimeUnit.MILLISECONDS);
          assertFalse(initFuture.isDone());
          assertFalse(pollingProcessor.isInitialized());
          assertEquals(0, dataSourceUpdates.receivedInits.size());
          
          Status status = requireDataSourceStatus(statuses, State.INITIALIZING);
          assertNotNull(status.getLastError());
          assertEquals(ErrorKind.NETWORK_ERROR, status.getLastError().getKind());
        }
      }
    }
  }

  @Test
  public void testDataStoreFailure() throws Exception {
    DataStore badStore = dataStoreThatThrowsException(new RuntimeException("sorry"));
    DataStoreStatusProvider badStoreStatusProvider = new MockDataStoreStatusProvider(false);
    dataSourceUpdates = TestComponents.dataSourceUpdates(badStore, badStoreStatusProvider);

    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (HttpServer server = HttpServer.start(new TestPollHandler())) {
      try (PollingProcessor pollingProcessor = makeProcessor(server.getUri(), LENGTHY_INTERVAL)) {
        pollingProcessor.start();
        
        assertDataSetEquals(DataBuilder.forStandardTypes().build(), dataSourceUpdates.awaitInit());

        assertFalse(pollingProcessor.isInitialized());
 
        Status status = requireDataSourceStatus(statuses, State.INITIALIZING);
        assertNotNull(status.getLastError());
        assertEquals(ErrorKind.STORE_ERROR, status.getLastError().getKind());
      }
    }
  }
  
  @Test
  public void testMalformedData() throws Exception {
    Handler badDataHandler = Handlers.bodyJson("{bad");
    
    BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (HttpServer server = HttpServer.start(badDataHandler)) {
      try (PollingProcessor pollingProcessor = makeProcessor(server.getUri(), LENGTHY_INTERVAL)) {
        pollingProcessor.start();
              
        Status status = requireDataSourceStatus(statuses, State.INITIALIZING);
        assertNotNull(status.getLastError());
        assertEquals(ErrorKind.INVALID_DATA, status.getLastError().getKind());
  
        assertFalse(pollingProcessor.isInitialized());
      }
    }
  }

  @Test
  public void startingWhenAlreadyStartedDoesNothing() throws Exception {
    try (HttpServer server = HttpServer.start(new TestPollHandler())) {
      try (PollingProcessor pollingProcessor = makeProcessor(server.getUri(), LENGTHY_INTERVAL)) {
        Future<?> initFuture1 = pollingProcessor.start();
        assertFutureIsCompleted(initFuture1, 1, TimeUnit.SECONDS);
        server.getRecorder().requireRequest();
        
        Future<Void> initFuture2 = pollingProcessor.start();
        assertSame(initFuture1, initFuture2);
        server.getRecorder().requireNoRequests(100, TimeUnit.MILLISECONDS);
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
  
  private void testUnrecoverableHttpError(int statusCode) throws Exception {
    TestPollHandler handler = new TestPollHandler();

    // Test a scenario where the very first request gets this error
    handler.setError(statusCode);
    withStatusQueue(statuses -> {
      try (HttpServer server = HttpServer.start(handler)) {
        try (PollingProcessor pollingProcessor = makeProcessor(server.getUri(), BRIEF_INTERVAL)) {
          long startTime = System.currentTimeMillis();
          Future<Void> initFuture = pollingProcessor.start();
           
          assertFutureIsCompleted(initFuture, 2, TimeUnit.SECONDS);
          assertTrue((System.currentTimeMillis() - startTime) < 9000);
          assertTrue(initFuture.isDone());
          assertFalse(pollingProcessor.isInitialized());
          
          verifyHttpErrorCausedShutdown(statuses, statusCode);
          
          server.getRecorder().requireRequest();
          server.getRecorder().requireNoRequests(100, TimeUnit.MILLISECONDS);
        }
      }
    });
    
    // Now test a scenario where we have a successful startup, but a subsequent poll gets the error
    handler.setError(0);
    dataSourceUpdates = TestComponents.dataSourceUpdates(new InMemoryDataStore(), new MockDataStoreStatusProvider());
    withStatusQueue(statuses -> {
      try (HttpServer server = HttpServer.start(handler)) {
        try (PollingProcessor pollingProcessor = makeProcessor(server.getUri(), BRIEF_INTERVAL)) {
          Future<Void> initFuture = pollingProcessor.start();
         
          assertFutureIsCompleted(initFuture, 2, TimeUnit.SECONDS);
          assertTrue(initFuture.isDone());
          assertTrue(pollingProcessor.isInitialized());
          requireDataSourceStatus(statuses, State.VALID);

          // now make it so polls fail
          handler.setError(statusCode);
          
          verifyHttpErrorCausedShutdown(statuses, statusCode);
          while (server.getRecorder().count() > 0) {
            server.getRecorder().requireRequest();
          }
          server.getRecorder().requireNoRequests(100, TimeUnit.MILLISECONDS);
        }
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
    TestPollHandler handler = new TestPollHandler();

    // Test a scenario where the very first request gets this error
    handler.setError(statusCode);
    withStatusQueue(statuses -> {
      try (HttpServer server = HttpServer.start(handler)) {
        try (PollingProcessor pollingProcessor = makeProcessor(server.getUri(), BRIEF_INTERVAL)) {
          Future<Void> initFuture = pollingProcessor.start();
          
          // make sure it's done a couple of polls (which will have failed)
          server.getRecorder().requireRequest();
          server.getRecorder().requireRequest();
 
          // now make it so polls will succeed
          handler.setError(0);
       
          assertFutureIsCompleted(initFuture, 1, TimeUnit.SECONDS);
          
          // verify that it got the error
          Status status0 = requireDataSourceStatus(statuses, State.INITIALIZING);
          assertNotNull(status0.getLastError());
          assertEquals(ErrorKind.ERROR_RESPONSE, status0.getLastError().getKind());
          assertEquals(statusCode, status0.getLastError().getStatusCode());

          // and then that it succeeded
          requireDataSourceStatusEventually(statuses, State.VALID, State.INITIALIZING);
        }
      }
    });
    
    // Now test a scenario where we have a successful startup, but then it gets the error.
    // The result is a bit different because it will report an INTERRUPTED state.
    handler.setError(0);
    dataSourceUpdates = TestComponents.dataSourceUpdates(new InMemoryDataStore(), new MockDataStoreStatusProvider());
    withStatusQueue(statuses -> {
      try (HttpServer server = HttpServer.start(handler)) {
        try (PollingProcessor pollingProcessor = makeProcessor(server.getUri(), BRIEF_INTERVAL)) {
          Future<Void> initFuture = pollingProcessor.start();
          assertFutureIsCompleted(initFuture, 1, TimeUnit.SECONDS);
          assertTrue(pollingProcessor.isInitialized());
          
          // first poll succeeded
          requireDataSourceStatus(statuses, State.VALID);
          
          // now make it so polls will fail
          handler.setError(statusCode);
          
          Status status1 = requireDataSourceStatus(statuses, State.INTERRUPTED);
          assertEquals(ErrorKind.ERROR_RESPONSE, status1.getLastError().getKind());
          assertEquals(statusCode, status1.getLastError().getStatusCode());

          // and then succeed again
          handler.setError(0);
          requireDataSourceStatusEventually(statuses, State.VALID, State.INTERRUPTED);
        }
      }
    });
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
}
