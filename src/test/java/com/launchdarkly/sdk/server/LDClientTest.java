package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.TestUtil.DataSourceFactoryThatExposesUpdater;
import com.launchdarkly.sdk.server.TestUtil.FlagChangeEventSink;
import com.launchdarkly.sdk.server.TestUtil.FlagValueChangeEventSink;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.TestUtil.failedDataSource;
import static com.launchdarkly.sdk.server.TestUtil.initedDataStore;
import static com.launchdarkly.sdk.server.TestUtil.specificDataStore;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
  public void clientSendsFlagChangeEvents() throws Exception {
    // The logic for sending change events is tested in detail in DataStoreUpdatesImplTest, but here we'll
    // verify that the client is actually telling DataStoreUpdatesImpl about updates, and managing the
    // listener list.
    DataStore testDataStore = initedDataStore();
    DataBuilder initialData = new DataBuilder().addAny(DataModel.FEATURES,
        flagBuilder("flagkey").version(1).build());
    DataSourceFactoryThatExposesUpdater updatableSource = new DataSourceFactoryThatExposesUpdater(initialData.build());
    LDConfig config = new LDConfig.Builder()
        .dataStore(specificDataStore(testDataStore))
        .dataSource(updatableSource)
        .events(Components.noEvents())
        .build();
    
    client = new LDClient(SDK_KEY, config);
    
    FlagChangeEventSink eventSink1 = new FlagChangeEventSink();
    FlagChangeEventSink eventSink2 = new FlagChangeEventSink();
    client.registerFlagChangeListener(eventSink1);
    client.registerFlagChangeListener(eventSink2);
    
    eventSink1.expectNoEvents();
    eventSink2.expectNoEvents();
    
    updatableSource.updateFlag(flagBuilder("flagkey").version(2).build());
    
    FlagChangeEvent event1 = eventSink1.awaitEvent();
    FlagChangeEvent event2 = eventSink2.awaitEvent();
    assertThat(event1.getKey(), equalTo("flagkey"));
    assertThat(event2.getKey(), equalTo("flagkey"));
    eventSink1.expectNoEvents();
    eventSink2.expectNoEvents();
    
    client.unregisterFlagChangeListener(eventSink1);
    
    updatableSource.updateFlag(flagBuilder("flagkey").version(3).build());

    FlagChangeEvent event3 = eventSink2.awaitEvent();
    assertThat(event3.getKey(), equalTo("flagkey"));
    eventSink1.expectNoEvents();
    eventSink2.expectNoEvents();
  }

  @Test
  public void clientSendsFlagValueChangeEvents() throws Exception {
    String flagKey = "important-flag";
    LDUser user = new LDUser("important-user");
    LDUser otherUser = new LDUser("unimportant-user");
    DataStore testDataStore = initedDataStore();
    
    FeatureFlag alwaysFalseFlag = flagBuilder(flagKey).version(1).on(true).variations(false, true)
        .fallthroughVariation(0).build();
    DataBuilder initialData = new DataBuilder().addAny(DataModel.FEATURES, alwaysFalseFlag);
    
    DataSourceFactoryThatExposesUpdater updatableSource = new DataSourceFactoryThatExposesUpdater(initialData.build());
    LDConfig config = new LDConfig.Builder()
        .dataStore(specificDataStore(testDataStore))
        .dataSource(updatableSource)
        .events(Components.noEvents())
        .build();
    
    client = new LDClient(SDK_KEY, config);
    FlagValueChangeEventSink eventSink1 = new FlagValueChangeEventSink();
    FlagValueChangeEventSink eventSink2 = new FlagValueChangeEventSink();
    client.registerFlagChangeListener(Components.flagValueMonitoringListener(flagKey, user, eventSink1));
    client.registerFlagChangeListener(Components.flagValueMonitoringListener(flagKey, otherUser, eventSink2));
    
    eventSink1.expectNoEvents();
    eventSink2.expectNoEvents();
    
    FeatureFlag flagIsTrueForMyUserOnly = flagBuilder(flagKey).version(2).on(true).variations(false, true)
        .targets(ModelBuilders.target(1, user.getKey())).fallthroughVariation(0).build();
    updatableSource.updateFlag(flagIsTrueForMyUserOnly);
    
    // eventSink1 receives a value change event; eventSink2 doesn't because the flag's value hasn't changed for otherUser
    FlagValueChangeEvent event1 = eventSink1.awaitEvent();
    assertThat(event1.getKey(), equalTo(flagKey));
    assertThat(event1.getOldValue(), equalTo(LDValue.of(false)));
    assertThat(event1.getNewValue(), equalTo(LDValue.of(true)));
    eventSink1.expectNoEvents();
    
    eventSink2.expectNoEvents();
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
}