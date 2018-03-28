package com.launchdarkly.client;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import junit.framework.AssertionFailedError;

/**
 * See also LDClientEvaluationTest, etc. This file contains mostly tests for the startup logic.
 */
public class LDClientTest extends EasyMockSupport {
  private UpdateProcessor updateProcessor;
  private EventProcessor eventProcessor;
  private Future<Void> initFuture;
  private LDClientInterface client;

  @SuppressWarnings("unchecked")
  @Before
  public void before() {
    updateProcessor = createStrictMock(UpdateProcessor.class);
    eventProcessor = createStrictMock(EventProcessor.class);
    initFuture = createStrictMock(Future.class);
  }

  @Test
  public void clientHasDefaultEventProcessorIfSendEventsIsTrue() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .stream(false)
        .baseURI(URI.create("/fake"))
        .startWaitMillis(0)
        .sendEvents(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(DefaultEventProcessor.class, client.eventProcessor.getClass());
    }
  }
  
  @Test
  public void clientHasNullEventProcessorIfSendEventsIsFalse() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .stream(false)
        .baseURI(URI.create("/fake"))
        .startWaitMillis(0)
        .sendEvents(false)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(EventProcessor.NullEventProcessor.class, client.eventProcessor.getClass());
    }
  }
  
  @Test
  public void streamingClientHasStreamProcessor() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .stream(true)
        .streamURI(URI.create("/fake"))
        .startWaitMillis(0)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(StreamProcessor.class, client.updateProcessor.getClass());
    }
  }

  @Test
  public void pollingClientHasPollingProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .stream(false)
        .baseURI(URI.create("/fake"))
        .startWaitMillis(0)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(PollingProcessor.class, client.updateProcessor.getClass());
    }
  }

  @Test
  public void noWaitForUpdateProcessorIfWaitMillisIsZero() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(0L)
        .build();

    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(false);
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }

  @Test
  public void willWaitForUpdateProcessorIfWaitMillisIsNonZero() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(10L)
        .build();

    expect(updateProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(null);
    expect(updateProcessor.initialized()).andReturn(false);
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }

  @Test
  public void updateProcessorCanTimeOut() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(10L)
        .build();

    expect(updateProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    expect(updateProcessor.initialized()).andReturn(false);
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }
  
  @Test
  public void clientCatchesRuntimeExceptionFromUpdateProcessor() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(10L)
        .build();

    expect(updateProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new RuntimeException());
    expect(updateProcessor.initialized()).andReturn(false);
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }

  @Test
  public void isFlagKnownReturnsTrueForExistingFlag() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(0)
            .featureStore(testFeatureStore)
            .build();
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(true).times(1);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.setIntegerValue("key", 1);
    assertTrue(client.isFlagKnown("key"));
    verifyAll();
  }

  @Test
  public void isFlagKnownReturnsFalseForUnknownFlag() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(0)
            .featureStore(testFeatureStore)
            .build();
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(true).times(1);
    replayAll();

    client = createMockClient(config);

    assertFalse(client.isFlagKnown("key"));
    verifyAll();
  }

  @Test
  public void isFlagKnownReturnsFalseIfStoreAndClientAreNotInitialized() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(false);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(0)
            .featureStore(testFeatureStore)
            .build();
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(false).times(1);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.setIntegerValue("key", 1);
    assertFalse(client.isFlagKnown("key"));
    verifyAll();
  }

  @Test
  public void isFlagKnownUsesStoreIfStoreIsInitializedButClientIsNot() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(0)
            .featureStore(testFeatureStore)
            .build();
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(false).times(1);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.setIntegerValue("key", 1);
    assertTrue(client.isFlagKnown("key"));
    verifyAll();
  }
  
  @Test
  public void evaluationUsesStoreIfStoreIsInitializedButClientIsNot() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
        .featureStore(testFeatureStore)
        .startWaitMillis(0L)
        .build();
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(false);
    expectEventsSent(1);
    replayAll();

    client = createMockClient(config);
    
    testFeatureStore.setIntegerValue("key", 1);
    assertEquals(new Integer(1), client.intVariation("key", new LDUser("user"), 0));
    
    verifyAll();
  }

  private void expectEventsSent(int count) {
    eventProcessor.sendEvent(anyObject(Event.class));
    if (count > 0) {
      expectLastCall().times(count);
    } else {
      expectLastCall().andThrow(new AssertionFailedError("should not have queued an event")).anyTimes();
    }
  }
  
  private LDClientInterface createMockClient(LDConfig config) {
    return new LDClient("SDK_KEY", config) {
      @Override
      protected UpdateProcessor createUpdateProcessor(String sdkKey, LDConfig config) {
        return LDClientTest.this.updateProcessor;
      }

      @Override
      protected EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
        return LDClientTest.this.eventProcessor;
      }
    };
  }
}