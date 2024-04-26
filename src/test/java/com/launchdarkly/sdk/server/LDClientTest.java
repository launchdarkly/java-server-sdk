package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.MockPersistentDataStore;
import com.launchdarkly.sdk.server.integrations.Hook;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;

import org.easymock.Capture;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.TestComponents.dataStoreThatThrowsException;
import static com.launchdarkly.sdk.server.TestComponents.failedDataSource;
import static com.launchdarkly.sdk.server.TestComponents.initedDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificComponent;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * See also LDClientEvaluationTest, etc. This file contains mostly tests for the startup logic.
 */
@SuppressWarnings("javadoc")
public class LDClientTest extends BaseTest {
  private final static String SDK_KEY = "SDK_KEY";

  private DataSource dataSource;
  private EventProcessor eventProcessor;
  private Future<Void> initFuture;
  private LDClientInterface client;
  private final EasyMockSupport mocks = new EasyMockSupport();

  @SuppressWarnings("unchecked")
  @Before
  public void before() {
    dataSource = mocks.createStrictMock(DataSource.class);
    eventProcessor = mocks.createStrictMock(EventProcessor.class);
    initFuture = mocks.createStrictMock(Future.class);
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
    try (LDClient client = new LDClient("", baseConfig().build())) {}
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
        .logging(Components.logging(testLogging))
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(DefaultEventProcessorWrapper.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void clientHasDefaultEventProcessorWithSendEvents() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.sendEvents())
        .diagnosticOptOut(true)
        .logging(Components.logging(testLogging))
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(DefaultEventProcessorWrapper.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void clientHasNoOpEventProcessorWithNoEvents() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.noEvents())
        .logging(Components.logging(testLogging))
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals(NoOpEventProcessor.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void streamingClientHasStreamProcessor() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .serviceEndpoints(Components.serviceEndpoints().streaming("http://fake"))
        .events(Components.noEvents())
        .logging(Components.logging(testLogging))
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(StreamProcessor.class, client.dataSource.getClass());
    }
  }

  @Test
  public void canSetCustomStreamingEndpoint() throws Exception {
    String base = "http://fake";
    URI baseUri = URI.create(base);
    String expected = base + StandardEndpoints.STREAMING_REQUEST_PATH;
    LDConfig config = new LDConfig.Builder()
        .serviceEndpoints(Components.serviceEndpoints().streaming(baseUri))
        .events(Components.noEvents())
        .logging(Components.logging(testLogging))
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(expected, ((StreamProcessor) client.dataSource).streamUri.toString());
    }
  }

  @Test
  public void pollingClientHasPollingProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.pollingDataSource())
        .serviceEndpoints(Components.serviceEndpoints().polling("http://fake"))
        .events(Components.noEvents())
        .logging(Components.logging(testLogging))
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
        .logging(Components.logging(testLogging))
        .startWait(Duration.ZERO)
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      String actual = ((DefaultFeatureRequestor) ((PollingProcessor) client.dataSource).requestor).pollingUri.toString();
      assertThat(actual, containsString(pu.toString()));
    }
  }

  @Test
  public void canSetHooks() throws Exception {
    LDConfig config1 = new LDConfig.Builder()
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config1)) {
      assertNotEquals(EvaluatorWithHooks.class, client.evaluator.getClass());
    }

    LDConfig config2 = new LDConfig.Builder()
        .hooks(Components.hooks().setHooks(Collections.singletonList(mock(Hook.class))))
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config2)) {
      assertEquals(EvaluatorWithHooks.class, client.evaluator.getClass());
    }
  }

  @Test
  public void sameDiagnosticStorePassedToFactoriesWhenSupported() throws IOException {
    @SuppressWarnings("unchecked")
    ComponentConfigurer<DataSource> mockDataSourceFactory = mocks.createStrictMock(ComponentConfigurer.class);

    LDConfig config = new LDConfig.Builder()
            .serviceEndpoints(Components.serviceEndpoints().events("fake-host")) // event processor will try to send a diagnostic event here
            .dataSource(mockDataSourceFactory)
            .events(Components.sendEvents())
            .logging(Components.logging(testLogging))
            .startWait(Duration.ZERO)
            .build();

    Capture<ClientContext> capturedDataSourceContext = Capture.newInstance();
    expect(mockDataSourceFactory.build(capture(capturedDataSourceContext))).andReturn(failedDataSource());

    mocks.replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      mocks.verifyAll();
      assertNotNull(ClientContextImpl.get(capturedDataSourceContext.getValue()).diagnosticStore);
    }
  }

  @Test
  public void nullDiagnosticStorePassedToFactoriesWhenOptedOut() throws IOException {
    @SuppressWarnings("unchecked")
    ComponentConfigurer<DataSource> mockDataSourceFactory = mocks.createStrictMock(ComponentConfigurer.class);

    LDConfig config = new LDConfig.Builder()
            .dataSource(mockDataSourceFactory)
            .diagnosticOptOut(true)
            .logging(Components.logging(testLogging))
            .startWait(Duration.ZERO)
            .build();

    Capture<ClientContext> capturedDataSourceContext = Capture.newInstance();
    expect(mockDataSourceFactory.build(capture(capturedDataSourceContext))).andReturn(failedDataSource());

    mocks.replayAll();

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      mocks.verifyAll();
      assertNull(ClientContextImpl.get(capturedDataSourceContext.getValue()).diagnosticStore);
    }
  }

  @Test
  public void noWaitForDataSourceIfWaitMillisIsZero() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ZERO);

    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(false);
    mocks.replayAll();

    client = createMockClient(config);
    assertFalse(client.isInitialized());

    mocks.verifyAll();
  }

  @Test
  public void willWaitForDataSourceIfWaitMillisIsGreaterThanZero() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ofMillis(10));

    expect(dataSource.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(null);
    expect(dataSource.isInitialized()).andReturn(false).anyTimes();
    mocks.replayAll();

    client = createMockClient(config);
    assertFalse(client.isInitialized());

    mocks.verifyAll();
  }

  @Test
  public void noWaitForDataSourceIfWaitMillisIsNegative() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ofMillis(-10));

    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(false);
    mocks.replayAll();

    client = createMockClient(config);
    assertFalse(client.isInitialized());

    mocks.verifyAll();
  }

  @Test
  public void dataSourceCanTimeOut() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ofMillis(10));

    expect(dataSource.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException());
    expect(dataSource.isInitialized()).andReturn(false).anyTimes();
    mocks.replayAll();

    client = createMockClient(config);
    assertFalse(client.isInitialized());

    mocks.verifyAll();
  }
  
  @Test
  public void clientCatchesRuntimeExceptionFromDataSource() throws Exception {
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ofMillis(10));

    expect(dataSource.start()).andReturn(initFuture);
    expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new RuntimeException());
    expect(dataSource.isInitialized()).andReturn(false).anyTimes();
    mocks.replayAll();

    client = createMockClient(config);
    assertFalse(client.isInitialized());

    mocks.verifyAll();
  }

  @Test
  public void isFlagKnownReturnsTrueForExistingFlag() throws Exception {
    DataStore testDataStore = initedDataStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataStore(specificComponent(testDataStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(true).times(1);
    mocks.replayAll();

    client = createMockClient(config);

    upsertFlag(testDataStore, flagWithValue("key", LDValue.of(1)));
    assertTrue(client.isFlagKnown("key"));
    mocks.verifyAll();
  }

  @Test
  public void isFlagKnownReturnsFalseForUnknownFlag() throws Exception {
    DataStore testDataStore = initedDataStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataStore(specificComponent(testDataStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(true).times(1);
    mocks.replayAll();

    client = createMockClient(config);

    assertFalse(client.isFlagKnown("key"));
    mocks.verifyAll();
  }

  @Test
  public void isFlagKnownReturnsFalseIfStoreAndClientAreNotInitialized() throws Exception {
    DataStore testDataStore = new InMemoryDataStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataStore(specificComponent(testDataStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(false).times(1);
    mocks.replayAll();

    client = createMockClient(config);

    upsertFlag(testDataStore, flagWithValue("key", LDValue.of(1)));
    assertFalse(client.isFlagKnown("key"));
    mocks.verifyAll();
  }

  @Test
  public void isFlagKnownUsesStoreIfStoreIsInitializedButClientIsNot() throws Exception {
    DataStore testDataStore = initedDataStore();
    LDConfig.Builder config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataStore(specificComponent(testDataStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(false).times(1);
    mocks.replayAll();

    client = createMockClient(config);

    upsertFlag(testDataStore, flagWithValue("key", LDValue.of(1)));
    assertTrue(client.isFlagKnown("key"));
    mocks.verifyAll();
  }
  
  @Test
  public void isFlagKnownCatchesExceptionFromDataStore() throws Exception {
    DataStore badStore = dataStoreThatThrowsException(new RuntimeException("sorry"));
    LDConfig.Builder config = new LDConfig.Builder()
        .startWait(Duration.ZERO)
        .dataStore(specificComponent(badStore));
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(false).times(1);
    mocks.replayAll();

    client = createMockClient(config);
    
    assertFalse(client.isFlagKnown("key"));
  }
  
  @Test
  public void getVersion() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.noEvents())
        .logging(Components.logging(testLogging))
        .build();
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertEquals(Version.SDK_VERSION, client.version());
    }
  }

  @Test
  public void canGetCacheStatsFromDataStoreStatusProvider() throws Exception {
    LDConfig config1 = baseConfig().build();
    try (LDClient client1 = new LDClient(SDK_KEY, config1)) {
      assertNull(client1.getDataStoreStatusProvider().getCacheStats());
    }
    
    LDConfig config2 = baseConfig()
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
    LDContext context = LDContext.create("userkey");
    LDContext contextAsUser = LDContext.create(context.getKey());
    String expectedHash = "c097a70924341660427c2e487b86efee789210f9e6dafc3b5f50e75bc596ff99";
    
    client = createMockClient(new LDConfig.Builder()
        .startWait(Duration.ZERO));
    assertEquals(expectedHash, client.secureModeHash(context));
    assertEquals(expectedHash, client.secureModeHash(contextAsUser));
  
    assertNull(client.secureModeHash(null));
    assertNull(client.secureModeHash(LDContext.create(null))); // invalid context
  }

  private void setupMockDataSourceToInitialize(boolean willInitialize) {
    expect(dataSource.start()).andReturn(initFuture);
    expect(dataSource.isInitialized()).andReturn(willInitialize);
    mocks.replayAll();
  }
  
  private LDClient createMockClient(LDConfig.Builder config) {
    config.dataSource(specificComponent(dataSource));
    config.events(specificComponent(eventProcessor));
    config.logging(Components.logging(testLogging));
    return new LDClient(SDK_KEY, config.build());
  }
}