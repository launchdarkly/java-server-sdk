package com.launchdarkly.sdk.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;

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

import static com.google.common.collect.Iterables.transform;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toDataMap;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.ModelBuilders.prerequisite;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestUtil.dataSourceWithData;
import static com.launchdarkly.sdk.server.TestUtil.failedDataSource;
import static com.launchdarkly.sdk.server.TestUtil.initedDataStore;
import static com.launchdarkly.sdk.server.TestUtil.specificDataStore;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
  public void clientHasDefaultEventProcessorWithDefaultConfig() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
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
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(Components.NullEventProcessor.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void streamingClientHasStreamProcessor() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.streamingDataSource().baseURI(URI.create("http://fake")))
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(StreamProcessor.class, client.dataSource.getClass());
    }
  }

  @Test
  public void pollingClientHasPollingProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.pollingDataSource().baseURI(URI.create("http://fake")))
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(PollingProcessor.class, client.dataSource.getClass());
    }
  }

  @Test
  public void sameDiagnosticAccumulatorPassedToFactoriesWhenSupported() throws IOException {
    DataSourceFactory mockDataSourceFactory = createStrictMock(DataSourceFactory.class);

    LDConfig config = new LDConfig.Builder()
            .dataSource(mockDataSourceFactory)
            .events(Components.sendEvents().baseURI(URI.create("fake-host"))) // event processor will try to send a diagnostic event here
            .startWait(Duration.ZERO)
            .build();

    Capture<ClientContext> capturedDataSourceContext = Capture.newInstance();
    expect(mockDataSourceFactory.createDataSource(capture(capturedDataSourceContext),
        isA(DataStoreUpdates.class))).andReturn(failedDataSource());

    replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      verifyAll();
      DiagnosticAccumulator acc = ((DefaultEventProcessor)client.eventProcessor).dispatcher.diagnosticAccumulator; 
      assertNotNull(acc);
      assertSame(acc, ClientContextImpl.getDiagnosticAccumulator(capturedDataSourceContext.getValue()));
    }
  }

  @Test
  public void nullDiagnosticAccumulatorPassedToFactoriesWhenOptedOut() throws IOException {
    DataSourceFactory mockDataSourceFactory = createStrictMock(DataSourceFactory.class);

    LDConfig config = new LDConfig.Builder()
            .dataSource(mockDataSourceFactory)
            .diagnosticOptOut(true)
            .startWait(Duration.ZERO)
            .build();

    Capture<ClientContext> capturedDataSourceContext = Capture.newInstance();
    expect(mockDataSourceFactory.createDataSource(capture(capturedDataSourceContext),
        isA(DataStoreUpdates.class))).andReturn(failedDataSource());

    replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      verifyAll();
      assertNull(((DefaultEventProcessor)client.eventProcessor).dispatcher.diagnosticAccumulator);
      assertNull(ClientContextImpl.getDiagnosticAccumulator(capturedDataSourceContext.getValue()));
    }
  }

  @Test
  public void nullDiagnosticAccumulatorPassedToUpdateFactoryWhenEventProcessorDoesNotSupportDiagnostics() throws IOException {
    EventProcessor mockEventProcessor = createStrictMock(EventProcessor.class);
    mockEventProcessor.close();
    EasyMock.expectLastCall().anyTimes();
    EventProcessorFactory mockEventProcessorFactory = createStrictMock(EventProcessorFactory.class);
    DataSourceFactory mockDataSourceFactory = createStrictMock(DataSourceFactory.class);

    LDConfig config = new LDConfig.Builder()
            .events(mockEventProcessorFactory)
            .dataSource(mockDataSourceFactory)
            .startWait(Duration.ZERO)
            .build();

    Capture<ClientContext> capturedEventContext = Capture.newInstance();
    Capture<ClientContext> capturedDataSourceContext = Capture.newInstance();
    expect(mockEventProcessorFactory.createEventProcessor(capture(capturedEventContext))).andReturn(mockEventProcessor);
    expect(mockDataSourceFactory.createDataSource(capture(capturedDataSourceContext),
        isA(DataStoreUpdates.class))).andReturn(failedDataSource());

    replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      verifyAll();
      assertNull(ClientContextImpl.getDiagnosticAccumulator(capturedEventContext.getValue()));
      assertNull(ClientContextImpl.getDiagnosticAccumulator(capturedDataSourceContext.getValue()));
    }
  }

  @Test
  public void noWaitForDataSourceIfWaitMillisIsZero() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ZERO);

    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(false);
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
    expect(dataSource.isInitialized()).andReturn(false).anyTimes();
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
    expect(dataSource.isInitialized()).andReturn(false).anyTimes();
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
    expect(dataSource.isInitialized()).andReturn(false).anyTimes();
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
    expect(dataSource.isInitialized()).andReturn(true).times(1);
    replayAll();

    client = createMockClient(config);

    upsertFlag(testDataStore, flagWithValue("key", LDValue.of(1)));
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
    expect(dataSource.isInitialized()).andReturn(true).times(1);
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
    expect(dataSource.isInitialized()).andReturn(false).times(1);
    replayAll();

    client = createMockClient(config);

    upsertFlag(testDataStore, flagWithValue("key", LDValue.of(1)));
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
    expect(dataSource.isInitialized()).andReturn(false).times(1);
    replayAll();

    client = createMockClient(config);

    upsertFlag(testDataStore, flagWithValue("key", LDValue.of(1)));
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
    expect(dataSource.isInitialized()).andReturn(false);
    expectEventsSent(1);
    replayAll();

    client = createMockClient(config);
    
    upsertFlag(testDataStore, flagWithValue("key", LDValue.of(1)));
    assertEquals(new Integer(1), client.intVariation("key", new LDUser("user"), 0));
    
    verifyAll();
  }

  @Test
  public void dataSetIsPassedToDataStoreInCorrectOrder() throws Exception {
    // This verifies that the client is using DataStoreClientWrapper and that it is applying the
    // correct ordering for flag prerequisites, etc. This should work regardless of what kind of
    // DataSource we're using.
    
    Capture<FullDataSet<ItemDescriptor>> captureData = Capture.newInstance();
    DataStore store = createStrictMock(DataStore.class);
    store.init(EasyMock.capture(captureData));
    replay(store);
    
    LDConfig.Builder config = new LDConfig.Builder()
        .dataSource(dataSourceWithData(DEPENDENCY_ORDERING_TEST_DATA))
        .dataStore(specificDataStore(store))
        .events(Components.noEvents());
    client = new LDClient(SDK_KEY, config.build());
       
    Map<DataKind, Map<String, ItemDescriptor>> dataMap = toDataMap(captureData.getValue());
    assertEquals(2, dataMap.size());
    Map<DataKind, Map<String, ItemDescriptor>> inputDataMap = toDataMap(DEPENDENCY_ORDERING_TEST_DATA);
    
    // Segments should always come first
    assertEquals(SEGMENTS, Iterables.get(dataMap.keySet(), 0));
    assertEquals(inputDataMap.get(SEGMENTS).size(), Iterables.get(dataMap.values(), 0).size());
    
    // Features should be ordered so that a flag always appears after its prerequisites, if any
    assertEquals(FEATURES, Iterables.get(dataMap.keySet(), 1));
    Map<String, ItemDescriptor> map1 = Iterables.get(dataMap.values(), 1);
    List<DataModel.FeatureFlag> list1 = ImmutableList.copyOf(transform(map1.values(), d -> (DataModel.FeatureFlag)d.getItem()));
    assertEquals(inputDataMap.get(FEATURES).size(), map1.size());
    for (int itemIndex = 0; itemIndex < list1.size(); itemIndex++) {
      DataModel.FeatureFlag item = list1.get(itemIndex);
      for (DataModel.Prerequisite prereq: item.getPrerequisites()) {
        DataModel.FeatureFlag depFlag = (DataModel.FeatureFlag)map1.get(prereq.getKey()).getItem();
        int depIndex = list1.indexOf(depFlag);
        if (depIndex > itemIndex) {
          fail(String.format("%s depends on %s, but %s was listed first; keys in order are [%s]",
              item.getKey(), prereq.getKey(), item.getKey(),
              Joiner.on(", ").join(map1.keySet())));
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
    config.events(TestUtil.specificEventProcessor(eventProcessor));
    return new LDClient(SDK_KEY, config.build());
  }
  
  private static FullDataSet<ItemDescriptor> DEPENDENCY_ORDERING_TEST_DATA =
      new DataBuilder()
        .addAny(FEATURES,
              flagBuilder("a")
                  .prerequisites(prerequisite("b", 0), prerequisite("c", 0)).build(),
              flagBuilder("b")
                  .prerequisites(prerequisite("c", 0), prerequisite("e", 0)).build(),
              flagBuilder("c").build(),
              flagBuilder("d").build(),
              flagBuilder("e").build(),
              flagBuilder("f").build())
        .addAny(SEGMENTS,
              segmentBuilder("o").build())
        .build();
}