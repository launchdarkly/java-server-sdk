package com.launchdarkly.client;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.launchdarkly.client.interfaces.DataSource;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.Event;
import com.launchdarkly.client.interfaces.EventProcessor;
import com.launchdarkly.client.interfaces.EventProcessorFactory;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;
import com.launchdarkly.client.value.LDValue;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.launchdarkly.client.DataModel.DataKinds.FEATURES;
import static com.launchdarkly.client.DataModel.DataKinds.SEGMENTS;
import static com.launchdarkly.client.ModelBuilders.flagBuilder;
import static com.launchdarkly.client.ModelBuilders.flagWithValue;
import static com.launchdarkly.client.ModelBuilders.prerequisite;
import static com.launchdarkly.client.ModelBuilders.segmentBuilder;
import static com.launchdarkly.client.TestUtil.dataSourceWithData;
import static com.launchdarkly.client.TestUtil.failedDataSource;
import static com.launchdarkly.client.TestUtil.initedDataStore;
import static com.launchdarkly.client.TestUtil.specificDataStore;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import junit.framework.AssertionFailedError;

/**
 * See also LDClientEvaluationTest, etc. This file contains mostly tests for the startup logic.
 */
@SuppressWarnings("javadoc")
public class LDClientTest extends EasyMockSupport {
  private final static String SDK_KEY = "SDK_KEY";

  private DataSource dataSource;
  private EventProcessor eventProcessor;
  private Future<Void> initFuture;
  private LDClientInterface client;

  @SuppressWarnings("unchecked")
  @Before
  public void before() {
    dataSource = createStrictMock(DataSource.class);
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
    try (LDClient client = new LDClient(SDK_KEY, null)) {
      fail("expected exception");
    } catch (NullPointerException e) {
      assertEquals("config must not be null", e.getMessage());
    }
  }
  
  @Test
  public void clientHasDefaultEventProcessorIfSendEventsIsTrue() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .stream(false)
        .baseURI(URI.create("/fake"))
        .startWait(Duration.ZERO)
        .sendEvents(true)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(DefaultEventProcessor.class, client.eventProcessor.getClass());
    }
  }
  
  @Test
  public void clientHasNullEventProcessorIfSendEventsIsFalse() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .stream(false)
        .baseURI(URI.create("/fake"))
        .startWait(Duration.ZERO)
        .sendEvents(false)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(Components.NullEventProcessor.class, client.eventProcessor.getClass());
    }
  }
  
  @Test
  public void streamingClientHasStreamProcessor() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .stream(true)
        .streamURI(URI.create("http://fake"))
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(StreamProcessor.class, client.dataSource.getClass());
    }
  }

  @Test
  public void pollingClientHasPollingProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .stream(false)
        .baseURI(URI.create("http://fake"))
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(PollingProcessor.class, client.dataSource.getClass());
    }
  }

  @Test
  public void sameDiagnosticAccumulatorPassedToFactoriesWhenSupported() throws IOException {
    EventProcessorFactoryWithDiagnostics mockEventProcessorFactory = createStrictMock(EventProcessorFactoryWithDiagnostics.class);
    DataSourceFactoryWithDiagnostics mockDataSourceFactory = createStrictMock(DataSourceFactoryWithDiagnostics.class);

    LDConfig config = new LDConfig.Builder()
            .stream(false)
            .baseURI(URI.create("http://fake"))
            .startWait(Duration.ZERO)
            .eventProcessor(mockEventProcessorFactory)
            .dataSource(mockDataSourceFactory)
            .build();

    Capture<DiagnosticAccumulator> capturedEventAccumulator = Capture.newInstance();
    Capture<DiagnosticAccumulator> capturedUpdateAccumulator = Capture.newInstance();
    expect(mockEventProcessorFactory.createEventProcessor(eq(SDK_KEY), isA(LDConfig.class), capture(capturedEventAccumulator))).andReturn(niceMock(EventProcessor.class));
    expect(mockDataSourceFactory.createDataSource(eq(SDK_KEY), isA(LDConfig.class), isA(DataStore.class), capture(capturedUpdateAccumulator))).andReturn(failedDataSource());

    replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      verifyAll();
      assertNotNull(capturedEventAccumulator.getValue());
      assertEquals(capturedEventAccumulator.getValue(), capturedUpdateAccumulator.getValue());
    }
  }

  @Test
  public void nullDiagnosticAccumulatorPassedToFactoriesWhenOptedOut() throws IOException {
    EventProcessorFactoryWithDiagnostics mockEventProcessorFactory = createStrictMock(EventProcessorFactoryWithDiagnostics.class);
    DataSourceFactoryWithDiagnostics mockDataSourceFactory = createStrictMock(DataSourceFactoryWithDiagnostics.class);

    LDConfig config = new LDConfig.Builder()
            .stream(false)
            .baseURI(URI.create("http://fake"))
            .startWait(Duration.ZERO)
            .eventProcessor(mockEventProcessorFactory)
            .dataSource(mockDataSourceFactory)
            .diagnosticOptOut(true)
            .build();

    expect(mockEventProcessorFactory.createEventProcessor(eq(SDK_KEY), isA(LDConfig.class), isNull(DiagnosticAccumulator.class))).andReturn(niceMock(EventProcessor.class));
    expect(mockDataSourceFactory.createDataSource(eq(SDK_KEY), isA(LDConfig.class), isA(DataStore.class), isNull(DiagnosticAccumulator.class))).andReturn(failedDataSource());

    replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      verifyAll();
    }
  }

  @Test
  public void nullDiagnosticAccumulatorPassedToUpdateFactoryWhenEventProcessorDoesNotSupportDiagnostics() throws IOException {
    EventProcessorFactory mockEventProcessorFactory = createStrictMock(EventProcessorFactory.class);
    DataSourceFactoryWithDiagnostics mockDataSourceFactory = createStrictMock(DataSourceFactoryWithDiagnostics.class);

    LDConfig config = new LDConfig.Builder()
            .stream(false)
            .baseURI(URI.create("http://fake"))
            .startWait(Duration.ZERO)
            .eventProcessor(mockEventProcessorFactory)
            .dataSource(mockDataSourceFactory)
            .build();

    expect(mockEventProcessorFactory.createEventProcessor(eq(SDK_KEY), isA(LDConfig.class))).andReturn(niceMock(EventProcessor.class));
    expect(mockDataSourceFactory.createDataSource(eq(SDK_KEY), isA(LDConfig.class), isA(DataStore.class), isNull(DiagnosticAccumulator.class))).andReturn(failedDataSource());

    replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      verifyAll();
    }
  }

  @Test
  public void noWaitForDataSourceIfWaitMillisIsZero() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ZERO);

    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.initialized()).andReturn(false);
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }

  @Test
  public void willWaitForDataSourceIfWaitMillisIsNonZero() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ofMillis(10));

    expect(dataSource.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(null);
    expect(dataSource.initialized()).andReturn(false).anyTimes();
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }

  @Test
  public void dataSourceCanTimeOut() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ofMillis(10));

    expect(dataSource.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    expect(dataSource.initialized()).andReturn(false).anyTimes();
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }
  
  @Test
  public void clientCatchesRuntimeExceptionFromDataSource() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ofMillis(10));

    expect(dataSource.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new RuntimeException());
    expect(dataSource.initialized()).andReturn(false).anyTimes();
    replayAll();

    client = createMockClient(config);
    assertFalse(client.initialized());

    verifyAll();
  }

  @Test
  public void isFlagKnownReturnsTrueForExistingFlag() throws Exception {
    DataStore testDataStore = initedDataStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataStore(specificDataStore(testDataStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.initialized()).andReturn(true).times(1);
    replayAll();

    client = createMockClient(config);

    testDataStore.upsert(FEATURES, flagWithValue("key", LDValue.of(1)));
    assertTrue(client.isFlagKnown("key"));
    verifyAll();
  }

  @Test
  public void isFlagKnownReturnsFalseForUnknownFlag() throws Exception {
    DataStore testDataStore = initedDataStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataStore(specificDataStore(testDataStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.initialized()).andReturn(true).times(1);
    replayAll();

    client = createMockClient(config);

    assertFalse(client.isFlagKnown("key"));
    verifyAll();
  }

  @Test
  public void isFlagKnownReturnsFalseIfStoreAndClientAreNotInitialized() throws Exception {
    DataStore testDataStore = new InMemoryDataStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataStore(specificDataStore(testDataStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.initialized()).andReturn(false).times(1);
    replayAll();

    client = createMockClient(config);

    testDataStore.upsert(FEATURES, flagWithValue("key", LDValue.of(1)));
    assertFalse(client.isFlagKnown("key"));
    verifyAll();
  }

  @Test
  public void isFlagKnownUsesStoreIfStoreIsInitializedButClientIsNot() throws Exception {
    DataStore testDataStore = initedDataStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataStore(specificDataStore(testDataStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.initialized()).andReturn(false).times(1);
    replayAll();

    client = createMockClient(config);

    testDataStore.upsert(FEATURES, flagWithValue("key", LDValue.of(1)));
    assertTrue(client.isFlagKnown("key"));
    verifyAll();
  }
  
  @Test
  public void evaluationUsesStoreIfStoreIsInitializedButClientIsNot() throws Exception {
    DataStore testDataStore = initedDataStore();
    LDConfig.Builder config = new LDConfig.Builder()
        .dataStore(specificDataStore(testDataStore))
        .startWait(Duration.ZERO);
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.initialized()).andReturn(false);
    expectEventsSent(1);
    replayAll();

    client = createMockClient(config);
    
    testDataStore.upsert(FEATURES, flagWithValue("key", LDValue.of(1)));
    assertEquals(new Integer(1), client.intVariation("key", new LDUser("user"), 0));
    
    verifyAll();
  }

  @Test
  public void dataSetIsPassedToDataStoreInCorrectOrder() throws Exception {
    // This verifies that the client is using DataStoreClientWrapper and that it is applying the
    // correct ordering for flag prerequisites, etc. This should work regardless of what kind of
    // DataSource we're using.
    
    Capture<Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>>> captureData = Capture.newInstance();
    DataStore store = createStrictMock(DataStore.class);
    store.init(EasyMock.capture(captureData));
    replay(store);
    
    LDConfig.Builder config = new LDConfig.Builder()
        .dataSource(dataSourceWithData(DEPENDENCY_ORDERING_TEST_DATA))
        .dataStore(specificDataStore(store))
        .sendEvents(false);
    client = new LDClient(SDK_KEY, config.build());
    
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
      DataModel.FeatureFlag item = (DataModel.FeatureFlag)list1.get(itemIndex);
      for (DataModel.Prerequisite prereq: item.getPrerequisites()) {
        DataModel.FeatureFlag depFlag = (DataModel.FeatureFlag)map1.get(prereq.getKey());
        int depIndex = list1.indexOf(depFlag);
        if (depIndex > itemIndex) {
          Iterable<String> allKeys = Iterables.transform(list1, d -> d.getKey());
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
    config.dataSource(TestUtil.specificDataSource(dataSource));
    config.eventProcessor(TestUtil.specificEventProcessor(eventProcessor));
    return new LDClient(SDK_KEY, config.build());
  }
  
  private static Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> DEPENDENCY_ORDERING_TEST_DATA =
      ImmutableMap.<VersionedDataKind<?>, Map<String, ? extends VersionedData>>of(
          FEATURES,
          ImmutableMap.<String, VersionedData>builder()
              .put("a", flagBuilder("a")
                  .prerequisites(prerequisite("b", 0), prerequisite("c", 0)).build())
              .put("b", flagBuilder("b")
                  .prerequisites(prerequisite("c", 0), prerequisite("e", 0)).build())
              .put("c", flagBuilder("c").build())
              .put("d", flagBuilder("d").build())
              .put("e", flagBuilder("e").build())
              .put("f", flagBuilder("f").build())
              .build(),
          SEGMENTS,
          ImmutableMap.<String, VersionedData>of(
              "o", segmentBuilder("o").build()
          )
      );
}