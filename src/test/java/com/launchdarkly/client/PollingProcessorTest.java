package com.launchdarkly.client;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class PollingProcessorTest {
  @Test
  public void testConnectionOk() throws Exception {
    MockFeatureRequestor requestor = new MockFeatureRequestor();
    requestor.allData = new FeatureRequestor.AllData(new HashMap<String, FeatureFlag>(), new HashMap<String, Segment>());
    FeatureStore store = new InMemoryFeatureStore();
    
    try (PollingProcessor pollingProcessor = new PollingProcessor(LDConfig.DEFAULT, requestor, store)) {    
      Future<Void> initFuture = pollingProcessor.start();
      initFuture.get(1000, TimeUnit.MILLISECONDS);
      assertTrue(pollingProcessor.initialized());
      assertTrue(store.initialized());
    }
  }

  @Test
  public void testConnectionProblem() throws Exception {
    MockFeatureRequestor requestor = new MockFeatureRequestor();
    requestor.ioException = new IOException("This exception is part of a test and yes you should be seeing it.");
    FeatureStore store = new InMemoryFeatureStore();

    try (PollingProcessor pollingProcessor = new PollingProcessor(LDConfig.DEFAULT, requestor, store)) {
      Future<Void> initFuture = pollingProcessor.start();
      try {
        initFuture.get(200L, TimeUnit.MILLISECONDS);
        fail("Expected Timeout, instead initFuture.get() returned.");
      } catch (TimeoutException ignored) {
      }
      assertFalse(initFuture.isDone());
      assertFalse(pollingProcessor.initialized());
      assertFalse(store.initialized());
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
  
  private void testUnrecoverableHttpError(int status) throws Exception {
    MockFeatureRequestor requestor = new MockFeatureRequestor();
    requestor.httpException = new HttpErrorException(status);
    try (PollingProcessor pollingProcessor = new PollingProcessor(LDConfig.DEFAULT, requestor, new InMemoryFeatureStore())) {  
      long startTime = System.currentTimeMillis();
      Future<Void> initFuture = pollingProcessor.start();
      try {
        initFuture.get(10, TimeUnit.SECONDS);
      } catch (TimeoutException ignored) {
        fail("Should not have timed out");
      }
      assertTrue((System.currentTimeMillis() - startTime) < 9000);
      assertTrue(initFuture.isDone());
      assertFalse(pollingProcessor.initialized());
    }
  }
  
  private void testRecoverableHttpError(int status) throws Exception {
    MockFeatureRequestor requestor = new MockFeatureRequestor();
    requestor.httpException = new HttpErrorException(status);
    try (PollingProcessor pollingProcessor = new PollingProcessor(LDConfig.DEFAULT, requestor, new InMemoryFeatureStore())) {
      Future<Void> initFuture = pollingProcessor.start();
      try {
        initFuture.get(200, TimeUnit.MILLISECONDS);
        fail("expected timeout");
      } catch (TimeoutException ignored) {
      }
      assertFalse(initFuture.isDone());
      assertFalse(pollingProcessor.initialized());
    }
  }
  
  private static class MockFeatureRequestor implements FeatureRequestor {
    AllData allData;
    HttpErrorException httpException;
    IOException ioException;
    
    public void close() throws IOException {}

    public FeatureFlag getFlag(String featureKey) throws IOException, HttpErrorException {
      return null;
    }

    public Segment getSegment(String segmentKey) throws IOException, HttpErrorException {
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
