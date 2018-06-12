package com.launchdarkly.client;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PollingProcessorTest extends EasyMockSupport {
  @Test
  public void testConnectionOk() throws Exception {
    FeatureRequestor requestor = createStrictMock(FeatureRequestor.class);
    PollingProcessor pollingProcessor = new PollingProcessor(LDConfig.DEFAULT, requestor, new InMemoryFeatureStore());

    expect(requestor.getAllData())
        .andReturn(new FeatureRequestor.AllData(new HashMap<String, FeatureFlag>(), new HashMap<String, Segment>()))
        .once();
    replayAll();

    Future<Void> initFuture = pollingProcessor.start();
    initFuture.get(1000, TimeUnit.MILLISECONDS);
    assertTrue(pollingProcessor.initialized());
    pollingProcessor.close();
    verifyAll();
  }

  @Test
  public void testConnectionProblem() throws Exception {
    FeatureRequestor requestor = createStrictMock(FeatureRequestor.class);
    PollingProcessor pollingProcessor = new PollingProcessor(LDConfig.DEFAULT, requestor, new InMemoryFeatureStore());

    expect(requestor.getAllData())
        .andThrow(new IOException("This exception is part of a test and yes you should be seeing it."))
        .once();
    replayAll();

    Future<Void> initFuture = pollingProcessor.start();
    try {
      initFuture.get(200L, TimeUnit.MILLISECONDS);
      fail("Expected Timeout, instead initFuture.get() returned.");
    } catch (TimeoutException ignored) {
    }
    assertFalse(initFuture.isDone());
    assertFalse(pollingProcessor.initialized());
    pollingProcessor.close();
    verifyAll();
  }
  
  @Test
  public void signalsImmediateFailureOn401Error() throws Exception {
    FeatureRequestor requestor = createStrictMock(FeatureRequestor.class);
    PollingProcessor pollingProcessor = new PollingProcessor(LDConfig.DEFAULT, requestor, new InMemoryFeatureStore());

    expect(requestor.getAllData())
        .andThrow(new FeatureRequestor.InvalidSDKKeyException())
        .once();
    replayAll();

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
    pollingProcessor.close();
    verifyAll();
  }
}