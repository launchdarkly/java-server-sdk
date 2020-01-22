package com.launchdarkly.client;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.launchdarkly.client.value.LDValue;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.launchdarkly.client.TestUtil.flagWithValue;
import static com.launchdarkly.client.TestUtil.initedFeatureStore;
import static com.launchdarkly.client.TestUtil.specificFeatureStore;
import static com.launchdarkly.client.TestUtil.updateProcessorWithData;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import junit.framework.AssertionFailedError;

/**
 * See also LDClientEvaluationTest, etc. This file contains mostly tests for the startup logic.
 */
@SuppressWarnings("javadoc")
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
  public void constructorThrowsExceptionForNullSdkKey() throws Exception {
    try (LDClient client = new LDClient(null)) {
      fail("expected exception");
    } catch (NullPointerException e) {
      assertEquals("sdkKey must not be null", e.getMessage());
    }
  }

  @Test
  public void constructorWithConfigThrowsExceptionForNullSdkKey() throws Exception {
    try (LDClient client = new LDClient(null, new LDConfig.Builder().build())) {
      fail("expected exception");
    } catch (NullPointerException e) {
      assertEquals("sdkKey must not be null", e.getMessage());
    }
  }

  @Test
  public void constructorThrowsExceptionForNullConfig() throws Exception {
    try (LDClient client = new LDClient("SDK_KEY", null)) {
      fail("expected exception");
    } catch (NullPointerException e) {
      assertEquals("config must not be null", e.getMessage());
    }
  }
  
  @Test
  public void clientHasDefaultEventProcessorWithDefaultConfig() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .startWaitMillis(0)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(DefaultEventProcessor.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void clientHasDefaultEventProcessorWithSendEvents() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.sendEvents())
        .startWaitMillis(0)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(DefaultEventProcessor.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void clientHasNullEventProcessorWithNoEvents() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.noEvents())
        .startWaitMillis(0)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(Components.NullEventProcessor.class, client.eventProcessor.getClass());
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void clientHasDefaultEventProcessorIfSendEventsIsTrue() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .startWaitMillis(0)
        .sendEvents(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(DefaultEventProcessor.class, client.eventProcessor.getClass());
    }
  }

  @SuppressWarnings("deprecation")
  @Test
  public void clientHasNullEventProcessorIfSendEventsIsFalse() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .startWaitMillis(0)
        .sendEvents(false)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(Components.NullEventProcessor.class, client.eventProcessor.getClass());
    }
  }
  
  @Test
  public void streamingClientHasStreamProcessor() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.streamingDataSource().baseUri(URI.create("http://fake")))
        .startWaitMillis(0)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(StreamProcessor.class, client.updateProcessor.getClass());
    }
  }

  @Test
  public void pollingClientHasPollingProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.pollingDataSource().baseUri(URI.create("http://fake")))
        .startWaitMillis(0)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(PollingProcessor.class, client.updateProcessor.getClass());
    }
  }

  @Test
  public void noWaitForUpdateProcessorIfWaitMillisIsZero() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWaitMillis(0L);

    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(false);
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }

  @Test
  public void willWaitForUpdateProcessorIfWaitMillisIsNonZero() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWaitMillis(10L);

    expect(updateProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(null);
    expect(updateProcessor.initialized()).andReturn(false).anyTimes();
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }

  @Test
  public void updateProcessorCanTimeOut() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWaitMillis(10L);

    expect(updateProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    expect(updateProcessor.initialized()).andReturn(false).anyTimes();
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }
  
  @Test
  public void clientCatchesRuntimeExceptionFromUpdateProcessor() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWaitMillis(10L);

    expect(updateProcessor.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new RuntimeException());
    expect(updateProcessor.initialized()).andReturn(false).anyTimes();
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }

  @Test
  public void isFlagKnownReturnsTrueForExistingFlag() throws Exception {
    FeatureStore testFeatureStore = initedFeatureStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWaitMillis(0)
            .dataStore(specificFeatureStore(testFeatureStore));
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(true).times(1);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(1)));
    assertTrue(client.isFlagKnown("key"));
    verifyAll();
  }

  @Test
  public void isFlagKnownReturnsFalseForUnknownFlag() throws Exception {
    FeatureStore testFeatureStore = initedFeatureStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWaitMillis(0)
            .dataStore(specificFeatureStore(testFeatureStore));
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(true).times(1);
    replayAll();

    client = createMockClient(config);

    assertFalse(client.isFlagKnown("key"));
    verifyAll();
  }

  @Test
  public void isFlagKnownReturnsFalseIfStoreAndClientAreNotInitialized() throws Exception {
    FeatureStore testFeatureStore = new InMemoryFeatureStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWaitMillis(0)
            .dataStore(specificFeatureStore(testFeatureStore));
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(false).times(1);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(1)));
    assertFalse(client.isFlagKnown("key"));
    verifyAll();
  }

  @Test
  public void isFlagKnownUsesStoreIfStoreIsInitializedButClientIsNot() throws Exception {
    FeatureStore testFeatureStore = initedFeatureStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWaitMillis(0)
            .dataStore(specificFeatureStore(testFeatureStore));
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(false).times(1);
    replayAll();

    client = createMockClient(config);

    testFeatureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(1)));
    assertTrue(client.isFlagKnown("key"));
    verifyAll();
  }
  
  @Test
  public void evaluationUsesStoreIfStoreIsInitializedButClientIsNot() throws Exception {
    FeatureStore testFeatureStore = initedFeatureStore();
    LDConfig.Builder config = new LDConfig.Builder()
        .dataStore(specificFeatureStore(testFeatureStore))
        .startWaitMillis(0L);
    expect(updateProcessor.start()).andReturn(initFuture);
    expect(updateProcessor.initialized()).andReturn(false);
    expectEventsSent(1);
    replayAll();

    client = createMockClient(config);
    
    testFeatureStore.upsert(FEATURES, flagWithValue("key", LDValue.of(1)));
    assertEquals(new Integer(1), client.intVariation("key", new LDUser("user"), 0));
    
    verifyAll();
  }

  @Test
  public void dataSetIsPassedToFeatureStoreInCorrectOrder() throws Exception {
    // This verifies that the client is using FeatureStoreClientWrapper and that it is applying the
    // correct ordering for flag prerequisites, etc. This should work regardless of what kind of
    // UpdateProcessor we're using.
    
    Capture<Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>>> captureData = Capture.newInstance();
    FeatureStore store = createStrictMock(FeatureStore.class);
    store.init(EasyMock.capture(captureData));
    replay(store);
    
    LDConfig.Builder config = new LDConfig.Builder()
        .dataSource(updateProcessorWithData(DEPENDENCY_ORDERING_TEST_DATA))
        .dataStore(specificFeatureStore(store))
        .events(Components.noEvents());
    client = new LDClient("SDK_KEY", config.build());
    
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> dataMap = captureData.getValue();
    assertEquals(2, dataMap.size());
    
    // Segments should always come first
    assertEquals(SEGMENTS, Iterables.get(dataMap.keySet(), 0));
    assertEquals(DEPENDENCY_ORDERING_TEST_DATA.get(SEGMENTS).size(), Iterables.get(dataMap.values(), 0).size());
    
    // Features should be ordered so that a flag always appears after its prerequisites, if any
    assertEquals(FEATURES, Iterables.get(dataMap.keySet(), 1));
    Map<String, ? extends VersionedData> map1 = Iterables.get(dataMap.values(), 1);
    List<VersionedData> list1 = ImmutableList.copyOf(map1.values());
    assertEquals(DEPENDENCY_ORDERING_TEST_DATA.get(FEATURES).size(), map1.size());
    for (int itemIndex = 0; itemIndex < list1.size(); itemIndex++) {
      FeatureFlag item = (FeatureFlag)list1.get(itemIndex);
      for (Prerequisite prereq: item.getPrerequisites()) {
        FeatureFlag depFlag = (FeatureFlag)map1.get(prereq.getKey());
        int depIndex = list1.indexOf(depFlag);
        if (depIndex > itemIndex) {
          Iterable<String> allKeys = Iterables.transform(list1, new Function<VersionedData, String>() {
            public String apply(VersionedData d) {
              return d.getKey();
            }
          });
          fail(String.format("%s depends on %s, but %s was listed first; keys in order are [%s]",
              item.getKey(), prereq.getKey(), item.getKey(),
              Joiner.on(", ").join(allKeys)));
        }
      }
    }
  }

  private void expectEventsSent(int count) {
    eventProcessor.sendEvent(anyObject(Event.class));
    if (count > 0) {
      expectLastCall().times(count);
    } else {
      expectLastCall().andThrow(new AssertionFailedError("should not have queued an event")).anyTimes();
    }
  }
  
  private LDClientInterface createMockClient(LDConfig.Builder config) {
    config.dataSource(TestUtil.specificUpdateProcessor(updateProcessor));
    config.events(TestUtil.specificEventProcessor(eventProcessor));
    return new LDClient("SDK_KEY", config.build());
  }
  
  private static Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> DEPENDENCY_ORDERING_TEST_DATA =
      ImmutableMap.<VersionedDataKind<?>, Map<String, ? extends VersionedData>>of(
          FEATURES,
          ImmutableMap.<String, VersionedData>builder()
              .put("a", new FeatureFlagBuilder("a")
                  .prerequisites(ImmutableList.of(new Prerequisite("b", 0), new Prerequisite("c", 0))).build())
              .put("b", new FeatureFlagBuilder("b")
                  .prerequisites(ImmutableList.of(new Prerequisite("c", 0), new Prerequisite("e", 0))).build())
              .put("c", new FeatureFlagBuilder("c").build())
              .put("d", new FeatureFlagBuilder("d").build())
              .put("e", new FeatureFlagBuilder("e").build())
              .put("f", new FeatureFlagBuilder("f").build())
              .build(),
          SEGMENTS,
          ImmutableMap.<String, VersionedData>of(
              "o", new Segment.Builder("o").build()
          )
      );
}