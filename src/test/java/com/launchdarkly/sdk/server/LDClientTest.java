package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.MockPersistentDataStore;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;

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

import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.TestComponents.dataStoreThatThrowsException;
import static com.launchdarkly.sdk.server.TestComponents.failedDataSource;
import static com.launchdarkly.sdk.server.TestComponents.initedDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificDataSource;
import static com.launchdarkly.sdk.server.TestComponents.specificDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificEventProcessor;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
  public void constructorThrowsExceptionForSdkKeyWithControlCharacter() throws Exception {
    try (LDClient client = new LDClient(SDK_KEY + "\n")) {
      fail("expected exception");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), not(containsString(SDK_KEY)));
    }
  }

  @Test
  public void constructorWithConfigThrowsExceptionForSdkKeyWithControlCharacter() throws Exception {
    try (LDClient client = new LDClient(SDK_KEY + "\n", LDConfig.DEFAULT)) {
      fail("expected exception");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), not(containsString(SDK_KEY)));
    }
  }

  @Test
  public void constructorAllowsSdkKeyToBeEmpty() throws Exception {
    // It may seem counter-intuitive to allow this, but if someone is using the SDK in offline
    // mode, or with a file data source or a test fixture, they may reasonably assume that it's
    // OK to pass an empty string since the key won't actually be used.
    try (LDClient client = new LDClient(SDK_KEY + "")) {}
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
        .diagnosticOptOut(true)
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
        .diagnosticOptOut(true)
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
      assertEquals(ComponentsImpl.NullEventProcessor.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void canSetCustomEventsEndpoint() throws Exception {
    URI eu = URI.create("http://fake");
    LDConfig config = new LDConfig.Builder()
        .serviceEndpoints(Components.serviceEndpoints().events(eu))
        .events(Components.sendEvents())
        .diagnosticOptOut(true)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(eu, ((DefaultEventProcessor) client.eventProcessor).dispatcher.eventsConfig.eventsUri);
    }
  }

  @Test
  public void streamingClientHasStreamProcessor() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.streamingDataSource().baseURI(URI.create("http://fake")))
        .events(Components.noEvents())
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(StreamProcessor.class, client.dataSource.getClass());
    }
  }

  @Test
  public void canSetCustomStreamingEndpoint() throws Exception {
    URI su = URI.create("http://fake");
    LDConfig config = new LDConfig.Builder()
        .serviceEndpoints(Components.serviceEndpoints().streaming(su))
        .events(Components.noEvents())
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(su, ((StreamProcessor) client.dataSource).streamUri);
    }
  }

  @Test
  public void pollingClientHasPollingProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.pollingDataSource().baseURI(URI.create("http://fake")))
        .events(Components.noEvents())
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(PollingProcessor.class, client.dataSource.getClass());
    }
  }

  @Test
  public void canSetCustomPollingEndpoint() throws Exception {
    URI pu = URI.create("http://fake");
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.pollingDataSource())
        .serviceEndpoints(Components.serviceEndpoints().polling(pu))
        .events(Components.noEvents())
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(pu, ((DefaultFeatureRequestor) ((PollingProcessor) client.dataSource).requestor).baseUri);
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
        isA(DataSourceUpdates.class))).andReturn(failedDataSource());

    replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      verifyAll();
      DiagnosticAccumulator acc = ((DefaultEventProcessor)client.eventProcessor).dispatcher.diagnosticAccumulator; 
      assertNotNull(acc);
      assertSame(acc, ClientContextImpl.get(capturedDataSourceContext.getValue()).diagnosticAccumulator);
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
        isA(DataSourceUpdates.class))).andReturn(failedDataSource());

    replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      verifyAll();
      assertNull(((DefaultEventProcessor)client.eventProcessor).dispatcher.diagnosticAccumulator);
      assertNull(ClientContextImpl.get(capturedDataSourceContext.getValue()).diagnosticAccumulator);
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
        isA(DataSourceUpdates.class))).andReturn(failedDataSource());

    replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      verifyAll();
      assertNull(ClientContextImpl.get(capturedEventContext.getValue()).diagnosticAccumulator);
      assertNull(ClientContextImpl.get(capturedDataSourceContext.getValue()).diagnosticAccumulator);
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
    assertFalse(client.isInitialized());

    verifyAll();
  }

  @Test
  public void willWaitForDataSourceIfWaitMillisIsGreaterThanZero() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ofMillis(10));

    expect(dataSource.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(null);
    expect(dataSource.isInitialized()).andReturn(false).anyTimes();
    replayAll();

    client = createMockClient(config);
    assertFalse(client.isInitialized());

    verifyAll();
  }

  @Test
  public void noWaitForDataSourceIfWaitMillisIsNegative() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ofMillis(-10));

    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(false);
    replayAll();

    client = createMockClient(config);
    assertFalse(client.isInitialized());

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
    assertFalse(client.isInitialized());

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
    assertFalse(client.isInitialized());

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
  public void isFlagKnownCatchesExceptionFromDataStore() throws Exception {
    DataStore badStore = dataStoreThatThrowsException(new RuntimeException("sorry"));
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ZERO)
        .dataStore(specificDataStore(badStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(false).times(1);
    replayAll();

    client = createMockClient(config);
    
    assertFalse(client.isFlagKnown("key"));
  }
  
  @Test
  public void getVersion() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.noEvents())
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(Version.SDK_VERSION, client.version());
    }
  }

  @Test
  public void canGetCacheStatsFromDataStoreStatusProvider() throws Exception {
    LDConfig config1 = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.noEvents())
        .build();
    try (LDClient client1 = new LDClient(SDK_KEY, config1)) {
      assertNull(client1.getDataStoreStatusProvider().getCacheStats());
    }
    
    LDConfig config2 = new LDConfig.Builder()
        .dataStore(Components.persistentDataStore(c -> new MockPersistentDataStore()))
        .build();
    try (LDClient client2 = new LDClient(SDK_KEY, config2)) {
      DataStoreStatusProvider.CacheStats expectedStats = new DataStoreStatusProvider.CacheStats(0, 0, 0, 0, 0, 0);
      assertEquals(expectedStats, client2.getDataStoreStatusProvider().getCacheStats());
    }
  }
  
  @Test
  public void testSecureModeHash() throws IOException {
    setupMockDataSourceToInitialize(true);
    LDUser user = new LDUser.Builder("userkey").build();
    String expectedHash = "c097a70924341660427c2e487b86efee789210f9e6dafc3b5f50e75bc596ff99";
    
    client = createMockClient(new LDConfig.Builder().startWait(Duration.ZERO));
    assertEquals(expectedHash, client.secureModeHash(user));
  
    assertNull(client.secureModeHash(null));
    assertNull(client.secureModeHash(new LDUser(null)));
  }

  private void setupMockDataSourceToInitialize(boolean willInitialize) {
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(willInitialize);
    replayAll();
  }
  
  private LDClient createMockClient(LDConfig.Builder config) {
    config.dataSource(specificDataSource(dataSource));
    config.events(specificEventProcessor(eventProcessor));
    return new LDClient(SDK_KEY, config.build());
  }
}