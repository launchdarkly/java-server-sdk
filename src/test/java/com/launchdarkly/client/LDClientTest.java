package com.launchdarkly.client;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LDClientTest extends EasyMockSupport {
  private FeatureRequestor requestor;
  private StreamProcessor streamProcessor;
  private PollingProcessor pollingProcessor;
  private EventProcessor eventProcessor;
  private Future initFuture;
  private LDClient client;

  @Before
  public void before() {
    requestor = createStrictMock(FeatureRequestor.class);
    streamProcessor = createStrictMock(StreamProcessor.class);
    pollingProcessor = createStrictMock(PollingProcessor.class);
    eventProcessor = createStrictMock(EventProcessor.class);
    initFuture = createStrictMock(Future.class);
  }

  @Test
  public void testOffline() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .build();

    client = createMockClient(config);
    replayAll();

    assertDefaultValueIsReturned();
    assertTrue(client.initialized());
    verifyAll();
  }

  @Test
  public void testTestFeatureStoreFlagOn() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(new Object());
    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(pollingProcessor.initialized()).andReturn(true).times(1);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true);
    replayAll();

    client = createMockClient(config);
    testFeatureStore.turnFeatureOn("key");
    assertTrue("Test flag should be on, but was not.", client.toggle("key", new LDUser("user"), false));

    verifyAll();
  }

  @Test
  public void testTestFeatureStoreFlagOff() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(new Object());
    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(pollingProcessor.initialized()).andReturn(true).times(1);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true);
    replayAll();

    client = createMockClient(config);
    testFeatureStore.turnFeatureOff("key");
    assertFalse("Test flag should be off, but was on (the default).", client.toggle("key", new LDUser("user"), true));

    verifyAll();
  }

  @Test
  public void testTestFeatureStoreFlagOnThenOff() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(new Object());
    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(pollingProcessor.initialized()).andReturn(true).times(2);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true).times(2);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.turnFeatureOn("key");
    assertTrue("Test flag should be on, but was not.", client.toggle("key", new LDUser("user"), false));

    testFeatureStore.turnFeatureOff("key");
    assertFalse("Test flag should be off, but was on (the default).", client.toggle("key", new LDUser("user"), true));

    verifyAll();
  }

  @Test
  public void testUseLdd() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .build();

    client = createMockClient(config);
    // Asserting 2 things here: no pollingProcessor or streamingProcessor activity
    // and sending of event:
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true);
    replayAll();

    assertDefaultValueIsReturned();
    assertTrue(client.initialized());
    verifyAll();
  }

  @Test
  public void testStreamingNoWait() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(0L)
        .stream(true)
        .build();

    expect(streamProcessor.start()).andReturn(initFuture);
    expect(streamProcessor.initialized()).andReturn(false);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true);
    replayAll();

    client = createMockClient(config);
    assertDefaultValueIsReturned();

    verifyAll();
  }

  @Test
  public void testStreamingWait() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(10L)
        .stream(true)
        .build();

    expect(streamProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    replayAll();

    client = createMockClient(config);
    verifyAll();
  }

  @Test
  public void testPollingNoWait() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(0L)
        .stream(false)
        .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(pollingProcessor.initialized()).andReturn(false);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true);
    replayAll();

    client = createMockClient(config);
    assertDefaultValueIsReturned();

    verifyAll();
  }

  @Test
  public void testPollingWait() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(10L)
        .stream(false)
        .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true);
    expect(pollingProcessor.initialized()).andReturn(false);
    replayAll();

    client = createMockClient(config);
    assertDefaultValueIsReturned();
    verifyAll();
  }

  private void assertDefaultValueIsReturned() {
    boolean result = client.toggle("test", new LDUser("test.key"), true);
    assertEquals(true, result);
  }

  private LDClient createMockClient(LDConfig config) {
    return new LDClient("API_KEY", config) {
      @Override
      protected FeatureRequestor createFeatureRequestor(String apiKey, LDConfig config) {
        return requestor;
      }

      @Override
      protected StreamProcessor createStreamProcessor(String apiKey, LDConfig config, FeatureRequestor requestor) {
        return streamProcessor;
      }

      @Override
      protected PollingProcessor createPollingProcessor(LDConfig config) {
        return pollingProcessor;
      }

      @Override
      protected EventProcessor createEventProcessor(String apiKey, LDConfig config) {
        return eventProcessor;
      }
    };
  }
}
