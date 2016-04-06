package com.launchdarkly.client;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LDClientTest extends EasyMockSupport {
  private FeatureRequestor requestor;
  private StreamProcessor streamProcessor;
  private PollingProcessor pollingProcessor;
  private Future initFuture;

  @Before
  public void before() {
    requestor = createStrictMock(FeatureRequestor.class);
    streamProcessor = createStrictMock(StreamProcessor.class);
    pollingProcessor = createStrictMock(PollingProcessor.class);
    initFuture = createStrictMock(Future.class);
  }

  @Test
  public void testOfflineDoesNotConnect() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .build();

    LDClient client = createMockClient(config, 0L);
    replayAll();

    assertDefaultValueIsReturned(client);
    assertTrue(client.initialized());
    verifyAll();
  }

  @Test
  public void testUseLddDoesNotConnect() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .build();

    LDClient client = createMockClient(config, 0L);
    replayAll();

    assertDefaultValueIsReturned(client);
    assertTrue(client.initialized());
    verifyAll();
  }

  @Test
  public void testStreamingNoWait() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .stream(true)
        .build();

    expect(streamProcessor.start()).andReturn(initFuture);
    expect(streamProcessor.initialized()).andReturn(false);
    replayAll();

    LDClient client = createMockClient(config, 0L);
    assertDefaultValueIsReturned(client);

    verifyAll();
  }

  @Test
  public void testStreamingWait() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .stream(true)
        .build();

    expect(streamProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    replayAll();

    LDClient client = createMockClient(config, 10L);
    verifyAll();
  }

  @Test
  public void testPollingNoWait() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .stream(false)
        .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(pollingProcessor.initialized()).andReturn(false);
    replayAll();

    LDClient client = createMockClient(config, 0L);
    assertDefaultValueIsReturned(client);

    verifyAll();
  }

  @Test
  public void testPollingWait() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .stream(false)
        .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    replayAll();

    LDClient client = createMockClient(config, 10L);
    verifyAll();
  }

  private void assertDefaultValueIsReturned(LDClient client) {
    boolean result = client.toggle("test", new LDUser("test.key"), true);
    assertEquals(true, result);
  }

  private LDClient createMockClient(
      LDConfig config,
      Long waitForMillis
  ) {
    return new LDClient("API_KEY", config, waitForMillis) {

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
    };
  }
}
