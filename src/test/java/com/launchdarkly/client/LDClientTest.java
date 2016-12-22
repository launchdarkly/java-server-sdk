package com.launchdarkly.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
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
    testFeatureStore.setFeatureTrue("key");
    assertTrue("Test flag should be true, but was not.", client.boolVariation("key", new LDUser("user"), false));

    verifyAll();
  }

  @Test
  public void testTestOfflineModeAllFlags() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    client = createOfflineClient(testFeatureStore);
    testFeatureStore.setFeatureTrue("key");
    Map<String, JsonElement> allFlags = client.allFlags(new LDUser("user"));
    assertNotNull("Expected non-nil response from allFlags() when offline mode is set to true", allFlags);
    assertEquals("Didn't get expected flag count from allFlags() in offline mode", 1, allFlags.size());
    assertTrue("Test flag should be true, but was not.", allFlags.get("key").getAsBoolean());
  }

  @Test
  public void testTestOfflineModeStringVariationNullDefault() {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    client = createOfflineClient(testFeatureStore);

    String actual = client.stringVariation("missingKey", new LDUser(""), null);
    assertNull("Expected null response:", actual);

    String expected = "stringValue";
    testFeatureStore.setStringValue("key", expected);
    actual = client.stringVariation("key", new LDUser(""), null);
    assertEquals(expected, actual);
  }

  @Test
  public void testTestOfflineModeDoubleVariationNullDefault() {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    client = createOfflineClient(testFeatureStore);

    Double actual = client.doubleVariation("missingKey", new LDUser(""), null);
    assertNull("Expected null response:", actual);

    Double expected = 100.0;
    testFeatureStore.setDoubleValue("key", expected);
    actual = client.doubleVariation("key", new LDUser(""), null);
    assertEquals(expected, actual);
  }

  @Test
  public void testTestOfflineModeJsonVariationNullDefault() {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    client = createOfflineClient(testFeatureStore);

    JsonElement actual = client.jsonVariation("missingKey", new LDUser(""), null);
    assertNull("Expected null response:", actual);

    JsonElement expected = new JsonArray();
    testFeatureStore.setJsonValue("key", expected);
    actual = client.jsonVariation("key", new LDUser(""), null);
    assertEquals(expected, actual);
  }

  @Test
  public void testTestFeatureStoreSetFalse() throws IOException, InterruptedException, ExecutionException, TimeoutException {
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
    testFeatureStore.setFeatureFalse("key");
    assertFalse("Test flag should be false, but was on (the default).", client.boolVariation("key", new LDUser("user"), true));

    verifyAll();
  }

  @Test
  public void testTestFeatureStoreFlagTrueThenFalse() throws IOException, InterruptedException, ExecutionException, TimeoutException {
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

    testFeatureStore.setFeatureTrue("key");
    assertTrue("Test flag should be true, but was not.", client.boolVariation("key", new LDUser("user"), false));

    testFeatureStore.setFeatureFalse("key");
    assertFalse("Test flag should be false, but was on (the default).", client.boolVariation("key", new LDUser("user"), true));

    verifyAll();
  }

  @Test
  public void testTestFeatureStoreIntegerVariation() throws Exception {
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

    testFeatureStore.setIntegerValue("key", 1);
    assertEquals(new Integer(1), client.intVariation("key", new LDUser("user"), 0));
    testFeatureStore.setIntegerValue("key", 42);
    assertEquals(new Integer(42), client.intVariation("key", new LDUser("user"), 1));
    verifyAll();
  }

  @Test
  public void testTestFeatureStoreDoubleVariation() throws Exception {
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

    testFeatureStore.setDoubleValue("key", 1d);
    assertEquals(new Double(1), client.doubleVariation("key", new LDUser("user"), 0d));
    testFeatureStore.setDoubleValue("key", 42d);
    assertEquals(new Double(42), client.doubleVariation("key", new LDUser("user"), 1d));
    verifyAll();
  }

  @Test
  public void testTestFeatureStoreStringVariation() throws Exception {
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

    testFeatureStore.setStringValue("key", "apples");
    assertEquals("apples", client.stringVariation("key", new LDUser("user"), "oranges"));
    testFeatureStore.setStringValue("key", "bananas");
    assertEquals("bananas", client.stringVariation("key", new LDUser("user"), "apples"));
    verifyAll();
  }

  @Test
  public void testTestFeatureStoreJsonVariationPrimitive() throws Exception {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    LDConfig config = new LDConfig.Builder()
            .startWaitMillis(10L)
            .stream(false)
            .featureStore(testFeatureStore)
            .build();

    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(new Object());
    expect(pollingProcessor.start()).andReturn(initFuture);
    expect(pollingProcessor.initialized()).andReturn(true).times(4);
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

  @Test
  public void testSecureModeHash() {
    LDConfig config = new LDConfig.Builder()
            .offline(true)
            .build();
    LDClientInterface client = new LDClient("secret", config);
    LDUser user = new LDUser.Builder("Message").build();
    assertEquals("aa747c502a898200f9e4fa21bac68136f886a0e27aec70ba06daf2e2a5cb5597", client.secureModeHash(user));
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

  private LDClient createOfflineClient(FeatureStore featureStore) {
    LDConfig config = new LDConfig.Builder()
        .startWaitMillis(10L)
        .offline(true)
        .featureStore(featureStore)
        .build();

    return new LDClient("", config);
  }
}
