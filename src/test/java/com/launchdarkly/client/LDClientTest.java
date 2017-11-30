package com.launchdarkly.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import junit.framework.AssertionFailedError;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;

public class LDClientTest extends EasyMockSupport {
  private FeatureRequestor requestor;
  private StreamProcessor streamProcessor;
  private PollingProcessor pollingProcessor;
  private EventProcessor eventProcessor;
  private Future initFuture;
  private LDClientInterface client;

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
  public void testTestFeatureStoreSetFeatureTrue() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true);
    replayAll();

    client = createMockClient(config);
    testFeatureStore.setFeatureTrue("key");
    assertTrue("Test flag should be true, but was not.", client.boolVariation("key", new LDUser("user"), false));

    verifyAll();
  }

  @Test
  public void testTestOfflineModeAllFlags() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(10L)
        .offline(true)
        .featureStore(testFeatureStore)
        .build();

    client = new LDClient("", config);//createMockClient(config);
    testFeatureStore.setFeatureTrue("key");
    Map<String, JsonElement> allFlags = client.allFlags(new LDUser("user"));
    assertNotNull("Expected non-nil response from allFlags() when offline mode is set to true", allFlags);
    assertEquals("Didn't get expected flag count from allFlags() in offline mode", 1, allFlags.size());
    assertTrue("Test flag should be true, but was not.", allFlags.get("key").getAsBoolean());
  }

  @Test
  public void testTestFeatureStoreSetFalse() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true);
    replayAll();

    client = createMockClient(config);
    testFeatureStore.setFeatureFalse("key");
    assertFalse("Test flag should be false, but was on (the default).", client.boolVariation("key", new LDUser("user"), true));

    verifyAll();
  }

  @Test
  public void testTestFeatureStoreFlagTrueThenFalse() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true).times(2);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.setFeatureTrue("key");
    assertTrue("Test flag should be true, but was not.", client.boolVariation("key", new LDUser("user"), false));

    testFeatureStore.setFeatureFalse("key");
    assertFalse("Test flag should be false, but was on (the default).", client.boolVariation("key", new LDUser("user"), true));

    verifyAll();
  }

  @Test
  public void testTestFeatureStoreIntegerVariation() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true).times(2);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.setIntegerValue("key", 1);
    assertEquals(new Integer(1), client.intVariation("key", new LDUser("user"), 0));
    testFeatureStore.setIntegerValue("key", 42);
    assertEquals(new Integer(42), client.intVariation("key", new LDUser("user"), 1));
    verifyAll();
  }

  @Test
  public void testTestFeatureStoreDoubleVariation() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true).times(2);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.setDoubleValue("key", 1d);
    assertEquals(new Double(1), client.doubleVariation("key", new LDUser("user"), 0d));
    testFeatureStore.setDoubleValue("key", 42d);
    assertEquals(new Double(42), client.doubleVariation("key", new LDUser("user"), 1d));
    verifyAll();
  }

  @Test
  public void testTestFeatureStoreStringVariation() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true).times(2);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.setStringValue("key", "apples");
    assertEquals("apples", client.stringVariation("key", new LDUser("user"), "oranges"));
    testFeatureStore.setStringValue("key", "bananas");
    assertEquals("bananas", client.stringVariation("key", new LDUser("user"), "apples"));
    verifyAll();
  }

  @Test
  public void testTestFeatureStoreJsonVariationPrimitive() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();
    
    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true).times(4);
    replayAll();

    client = createMockClient(config);

    // Character
    testFeatureStore.setJsonValue("key", new JsonPrimitive('a'));
    assertEquals(new JsonPrimitive('a'), client.jsonVariation("key", new LDUser("user"), new JsonPrimitive('b')));
    testFeatureStore.setJsonValue("key", new JsonPrimitive('b'));
    assertEquals(new JsonPrimitive('b'), client.jsonVariation("key", new LDUser("user"), new JsonPrimitive('z')));

    // Long
    testFeatureStore.setJsonValue("key", new JsonPrimitive(1L));
    assertEquals(new JsonPrimitive(1l), client.jsonVariation("key", new LDUser("user"), new JsonPrimitive(0L)));
    testFeatureStore.setJsonValue("key", new JsonPrimitive(42L));
    assertEquals(new JsonPrimitive(42L), client.jsonVariation("key", new LDUser("user"), new JsonPrimitive(0L)));
    verifyAll();
  }

  @Test
  public void testTestFeatureStoreJsonVariationArray() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(eventProcessor.sendEvent(anyObject(Event.class))).andReturn(true).times(2);
    replayAll();

    client = createMockClient(config);

    // JsonArray
    JsonArray array = new JsonArray();
    array.add("red");
    array.add("blue");
    array.add("green");
    testFeatureStore.setJsonValue("key", array);
    assertEquals(array, client.jsonVariation("key", new LDUser("user"), new JsonArray()));

    JsonArray array2 = new JsonArray();
    array2.addAll(array);
    array2.add("yellow");
    testFeatureStore.setJsonValue("key", array2);
    assertEquals(array2, client.jsonVariation("key", new LDUser("user"), new JsonArray()));
    verifyAll();
  }

  @Test
  public void testIsFlagKnown() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    testFeatureStore.setInitialized(true);
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(pollingProcessor.start()).andReturn(initFuture);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.setIntegerValue("key", 1);
    assertTrue("Flag is known", client.isFlagKnown("key"));
    assertFalse("Flag is unknown", client.isFlagKnown("unKnownKey"));
    verifyAll();
  }

  @Test
  public void testIsFlagKnownCallBeforeInitialization() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(new Object());
    expect(pollingProcessor.start()).andReturn(initFuture);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.setIntegerValue("key", 1);
    assertFalse("Flag is marked as unknown", client.isFlagKnown("key"));
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
    replayAll();

    client = createMockClient(config);
    assertDefaultValueIsReturned();
    verifyAll();
  }

  @Test
  public void testSecureModeHash() {
    LDConfig config = new LDConfig.Builder()
            .offline(true)
            .build();
    LDClientInterface client = new LDClient("secret", config);
    LDUser user = new LDUser.Builder("Message").build();
    assertEquals("aa747c502a898200f9e4fa21bac68136f886a0e27aec70ba06daf2e2a5cb5597", client.secureModeHash(user));
  }

  @Test
  public void testNoFeatureEventsAreSentWhenSendEventsIsFalse() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .sendEvents(false)
        .stream(false)
        .build();

    expect(initFuture.get(5000L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(pollingProcessor.initialized()).andReturn(true).anyTimes();
    expect(eventProcessor.sendEvent(anyObject(Event.class)))
      .andThrow(new AssertionFailedError("should not have queued an event")).anyTimes();
    replayAll();

    client = createMockClient(config);
    client.boolVariation("test", new LDUser("test.key"), true);

    verifyAll();
  }

  @Test
  public void testNoIdentifyEventsAreSentWhenSendEventsIsFalse() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .sendEvents(false)
        .stream(false)
        .build();

    expect(initFuture.get(5000L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(pollingProcessor.initialized()).andReturn(true).anyTimes();
    expect(eventProcessor.sendEvent(anyObject(Event.class)))
      .andThrow(new AssertionFailedError("should not have queued an event")).anyTimes();
    replayAll();

    client = createMockClient(config);
    client.identify(new LDUser("test.key"));

    verifyAll();
  }
  
  @Test
  public void testNoCustomEventsAreSentWhenSendEventsIsFalse() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .sendEvents(false)
        .stream(false)
        .build();

    expect(initFuture.get(5000L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(pollingProcessor.initialized()).andReturn(true).anyTimes();
    expect(eventProcessor.sendEvent(anyObject(Event.class)))
      .andThrow(new AssertionFailedError("should not have queued an event")).anyTimes();
    replayAll();

    client = createMockClient(config);
    client.track("test", new LDUser("test.key"));

    verifyAll();
  }
  
  private void assertDefaultValueIsReturned() {
    boolean result = client.boolVariation("test", new LDUser("test.key"), true);
    assertEquals(true, result);
  }

  private LDClientInterface createMockClient(LDConfig config) {
    return new LDClient("SDK_KEY", config) {
      @Override
      protected FeatureRequestor createFeatureRequestor(String sdkKey, LDConfig config) {
        return requestor;
      }

      @Override
      protected StreamProcessor createStreamProcessor(String sdkKey, LDConfig config, FeatureRequestor requestor) {
        return streamProcessor;
      }

      @Override
      protected PollingProcessor createPollingProcessor(LDConfig config) {
        return pollingProcessor;
      }

      @Override
      protected EventProcessor createEventProcessor(String sdkKey, LDConfig config) {
        return eventProcessor;
      }
    };
  }
}
