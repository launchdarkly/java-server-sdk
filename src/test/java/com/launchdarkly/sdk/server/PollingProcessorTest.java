package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.TestComponents.MockDataSourceUpdates;
import com.launchdarkly.sdk.server.TestComponents.MockDataStoreStatusProvider;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatus;
import static com.launchdarkly.sdk.server.TestUtil.requireDataSourceStatusEventually;
import static com.launchdarkly.sdk.server.TestUtil.shouldNotTimeOut;
import static com.launchdarkly.sdk.server.TestUtil.shouldTimeOut;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class PollingProcessorTest {
  private static final String SDK_KEY = "sdk-key";
  private static final Duration LENGTHY_INTERVAL = Duration.ofSeconds(60);

  private InMemoryDataStore store;
  private MockDataSourceUpdates dataSourceUpdates;
  private MockFeatureRequestor requestor;

  @Before
  public void setup() {
    store = new InMemoryDataStore();
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
  public void testConnectionOk() throws Exception {
    requestor.allData = new FeatureRequestor.AllData(new HashMap<>(), new HashMap<>());
    
    BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (PollingProcessor pollingProcessor = makeProcessor()) {    
      Future<Void> initFuture = pollingProcessor.start();
      initFuture.get(1000, TimeUnit.MILLISECONDS);
      assertTrue(pollingProcessor.isInitialized());
      assertTrue(store.isInitialized());
      
      requireDataSourceStatus(statuses, DataSourceStatusProvider.State.VALID);
    }
  }

  @Test
  public void testConnectionProblem() throws Exception {
    requestor.ioException = new IOException("This exception is part of a test and yes you should be seeing it.");

    BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
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
      assertFalse(store.isInitialized());
      
      DataSourceStatusProvider.Status status = requireDataSourceStatus(statuses, DataSourceStatusProvider.State.INITIALIZING);
      assertNotNull(status.getLastError());
      assertEquals(DataSourceStatusProvider.ErrorKind.NETWORK_ERROR, status.getLastError().getKind());
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
    requestor.httpException = new HttpErrorException(statusCode);
    
    BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (PollingProcessor pollingProcessor = makeProcessor()) {  
      long startTime = System.currentTimeMillis();
      Future<Void> initFuture = pollingProcessor.start();
      
      shouldNotTimeOut(initFuture, Duration.ofSeconds(2));
      assertTrue((System.currentTimeMillis() - startTime) < 9000);
      assertTrue(initFuture.isDone());
      assertFalse(pollingProcessor.isInitialized());
      
      DataSourceStatusProvider.Status status = requireDataSourceStatus(statuses, DataSourceStatusProvider.State.OFF);
      assertNotNull(status.getLastError());
      assertEquals(DataSourceStatusProvider.ErrorKind.ERROR_RESPONSE, status.getLastError().getKind());
      assertEquals(statusCode, status.getLastError().getStatusCode());
    }
  }
  
  private void testRecoverableHttpError(int statusCode) throws Exception {
    HttpErrorException httpError = new HttpErrorException(statusCode);
    Duration shortInterval = Duration.ofMillis(20);
    requestor.httpException = httpError;
    
    BlockingQueue<DataSourceStatusProvider.Status> statuses = new LinkedBlockingQueue<>();
    dataSourceUpdates.statusBroadcaster.register(statuses::add);

    try (PollingProcessor pollingProcessor = makeProcessor(shortInterval)) {
      Future<Void> initFuture = pollingProcessor.start();
      
      // first poll gets an error
      shouldTimeOut(initFuture, Duration.ofMillis(200));
      assertFalse(initFuture.isDone());
      assertFalse(pollingProcessor.isInitialized());
      
      DataSourceStatusProvider.Status status1 = requireDataSourceStatus(statuses, DataSourceStatusProvider.State.INITIALIZING);
      assertNotNull(status1.getLastError());
      assertEquals(DataSourceStatusProvider.ErrorKind.ERROR_RESPONSE, status1.getLastError().getKind());
      assertEquals(statusCode, status1.getLastError().getStatusCode());

      // now make it so the requestor will succeed
      requestor.allData = new FeatureRequestor.AllData(new HashMap<>(), new HashMap<>());
      requestor.httpException = null;
      
      shouldNotTimeOut(initFuture, Duration.ofSeconds(2));
      assertTrue(initFuture.isDone());
      assertTrue(pollingProcessor.isInitialized());

      // status should now be VALID (although there might have been more failed polls before that)
      DataSourceStatusProvider.Status status2 = requireDataSourceStatusEventually(statuses,
          DataSourceStatusProvider.State.VALID, DataSourceStatusProvider.State.INITIALIZING);
      assertNotNull(status2.getLastError());
      assertEquals(DataSourceStatusProvider.ErrorKind.ERROR_RESPONSE, status2.getLastError().getKind());
      assertEquals(statusCode, status2.getLastError().getStatusCode());
      
      // simulate another error of the same kind - the difference is now the state will be INTERRUPTED
      requestor.httpException = httpError;
      
      DataSourceStatusProvider.Status status3 = requireDataSourceStatusEventually(statuses,
          DataSourceStatusProvider.State.INTERRUPTED, DataSourceStatusProvider.State.VALID);
      assertNotNull(status3.getLastError());
      assertEquals(DataSourceStatusProvider.ErrorKind.ERROR_RESPONSE, status3.getLastError().getKind());
      assertEquals(statusCode, status3.getLastError().getStatusCode());
      assertNotSame(status1.getLastError(), status3.getLastError()); // it's a new error object of the same kind
    }
  }
  
  private static class MockFeatureRequestor implements FeatureRequestor {
    volatile AllData allData;
    volatile HttpErrorException httpException;
    volatile IOException ioException;
    
    public void close() throws IOException {}

    public DataModel.FeatureFlag getFlag(String featureKey) throws IOException, HttpErrorException {
      return null;
    }

    public DataModel.Segment getSegment(String segmentKey) throws IOException, HttpErrorException {
      return null;
    }

    public AllData getAllData() throws IOException, HttpErrorException {
      if (httpException != null) {
        throw httpException;
      }
      if (ioException != null) {
        throw ioException;
      }
      return allData;
    }
  }
}
