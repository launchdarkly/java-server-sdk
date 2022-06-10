package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.BasicConfiguration;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;
import com.launchdarkly.sdk.server.interfaces.DiagnosticDescription;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStore;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStoreFactory;

import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class DiagnosticEventTest {

  private static Gson gson = new Gson();
  private static List<DiagnosticEvent.StreamInit> testStreamInits = Collections.singletonList(new DiagnosticEvent.StreamInit(1500, 100, true));
  private static final URI CUSTOM_URI = URI.create("http://1.1.1.1");
  
  @Test
  public void testSerialization() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    DiagnosticEvent.Statistics diagnosticStatisticsEvent = new DiagnosticEvent.Statistics(2000, diagnosticId, 1000, 1, 2, 3, testStreamInits);
    JsonObject jsonObject = gson.toJsonTree(diagnosticStatisticsEvent).getAsJsonObject();
    assertEquals(8, jsonObject.size());
    assertEquals("diagnostic", diagnosticStatisticsEvent.kind);
    assertEquals(2000, jsonObject.getAsJsonPrimitive("creationDate").getAsLong());
    JsonObject idObject = jsonObject.getAsJsonObject("id");
    assertEquals("DK_KEY", idObject.getAsJsonPrimitive("sdkKeySuffix").getAsString());
    // Throws InvalidArgumentException on invalid UUID
    @SuppressWarnings("unused")
    UUID uuid = UUID.fromString(idObject.getAsJsonPrimitive("diagnosticId").getAsString());
    assertEquals(1000, jsonObject.getAsJsonPrimitive("dataSinceDate").getAsLong());
    assertEquals(1, jsonObject.getAsJsonPrimitive("droppedEvents").getAsLong());
    assertEquals(2, jsonObject.getAsJsonPrimitive("deduplicatedUsers").getAsLong());
    assertEquals(3, jsonObject.getAsJsonPrimitive("eventsInLastBatch").getAsLong());
    JsonArray initsJson = jsonObject.getAsJsonArray("streamInits");
    assertEquals(1, initsJson.size());
    JsonObject initJson = initsJson.get(0).getAsJsonObject();
    assertEquals(1500, initJson.getAsJsonPrimitive("timestamp").getAsInt());
    assertEquals(100, initJson.getAsJsonPrimitive("durationMillis").getAsInt());
    assertTrue(initJson.getAsJsonPrimitive("failed").getAsBoolean());
  }

  private ObjectBuilder expectedDefaultProperties() {
    return expectedDefaultPropertiesWithoutStreaming()
        .put("reconnectTimeMillis", 1_000);
  }

  private ObjectBuilder expectedDefaultPropertiesWithoutStreaming() {
    return LDValue.buildObject()
        .put("allAttributesPrivate", false)
        .put("connectTimeoutMillis", 2_000)
        .put("customBaseURI", false)
        .put("customEventsURI", false)
        .put("customStreamURI", false)
        .put("dataStoreType", "memory")
        .put("diagnosticRecordingIntervalMillis", 900_000)
        .put("eventsCapacity", 10_000)
        .put("eventsFlushIntervalMillis",5_000)
        .put("samplingInterval", 0)
        .put("socketTimeoutMillis", 10_000)
        .put("startWaitMillis", 5_000)
        .put("streamingDisabled", false)
        .put("userKeysCapacity", 1_000)
        .put("userKeysFlushIntervalMillis", 300_000)
        .put("usingProxy", false)
        .put("usingProxyAuthenticator", false)
        .put("usingRelayDaemon", false);
  }
  
  private static LDValue makeConfigData(LDConfig config) {
    ClientContext context = clientContext("SDK_KEY", config); // the SDK key doesn't matter for these tests
    return DiagnosticEvent.Init.getConfigurationData(config, context.getBasic(), context.getHttp());
  }
  
  @Test
  public void testDefaultDiagnosticConfiguration() {
    LDConfig ldConfig = new LDConfig.Builder().build();
    LDValue diagnosticJson = makeConfigData(ldConfig);
    LDValue expected = expectedDefaultProperties().build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationGeneralProperties() {
    LDConfig ldConfig = new LDConfig.Builder()
            .startWait(Duration.ofSeconds(10))
            .build();

    LDValue diagnosticJson = makeConfigData(ldConfig);
    LDValue expected = expectedDefaultProperties()
        .put("startWaitMillis", 10_000)
        .build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationForServiceEndpoints() {
    LDConfig ldConfig1 = new LDConfig.Builder()
        .serviceEndpoints(
            Components.serviceEndpoints()
              .streaming(CUSTOM_URI)
              .events(CUSTOM_URI)
              // this shouldn't show up in diagnostics because we don't use the polling component
              .polling(CUSTOM_URI)
        )
        .build();
    LDValue expected1 = expectedDefaultProperties()
        .put("customStreamURI", true)
        .put("customEventsURI", true)
        .build();
    assertEquals(expected1, makeConfigData(ldConfig1));

    LDConfig ldConfig2 = new LDConfig.Builder()
        .serviceEndpoints(
            Components.serviceEndpoints()
              .events(CUSTOM_URI)
              .polling(CUSTOM_URI)
        )
        .dataSource(
            Components.pollingDataSource()
        )
        .events(Components.sendEvents())
        .build();
    LDValue expected2 = expectedDefaultPropertiesWithoutStreaming()
        .put("customBaseURI", true)
        .put("customEventsURI", true)
        .put("customStreamURI", false)
        .put("pollingIntervalMillis", PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL.toMillis())
        .put("streamingDisabled", true)
        .build();
    assertEquals(expected2, makeConfigData(ldConfig2));
  }
  
  @Test
  public void testCustomDiagnosticConfigurationForStreaming() {
    LDConfig ldConfig1 = new LDConfig.Builder()
            .dataSource(
                Components.streamingDataSource()
                  .initialReconnectDelay(Duration.ofSeconds(2))
                )
            .build();
    LDValue expected1 = expectedDefaultPropertiesWithoutStreaming()
        .put("reconnectTimeMillis", 2_000)
        .build();
    assertEquals(expected1, makeConfigData(ldConfig1));
    
    LDConfig ldConfig2 = new LDConfig.Builder()
        .dataSource(Components.streamingDataSource()) // no custom base URIs
        .build();
    LDValue expected2 = expectedDefaultProperties().build();
    assertEquals(expected2, makeConfigData(ldConfig2));
  }

  @Test
  public void testCustomDiagnosticConfigurationForPolling() {
    LDConfig ldConfig1 = new LDConfig.Builder()
            .dataSource(
                Components.pollingDataSource()
                  .pollInterval(Duration.ofSeconds(60))
                )
            .build();
    LDValue expected1 = expectedDefaultPropertiesWithoutStreaming()
        .put("pollingIntervalMillis", 60_000)
        .put("streamingDisabled", true)
        .build();
    assertEquals(expected1,  makeConfigData(ldConfig1));

    LDConfig ldConfig2 = new LDConfig.Builder()
        .dataSource(Components.pollingDataSource()) // no custom base URI
        .build();
    LDValue expected2 = expectedDefaultPropertiesWithoutStreaming()
        .put("pollingIntervalMillis", PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL.toMillis())
        .put("streamingDisabled", true)
        .build();
    assertEquals(expected2, makeConfigData(ldConfig2));
  }

  @Test
  public void testCustomDiagnosticConfigurationForCustomDataStore() {
    LDConfig ldConfig1 = new LDConfig.Builder()
        .dataStore(new DataStoreFactoryWithDiagnosticDescription(LDValue.of("my-test-store")))
        .build();
    LDValue expected1 = expectedDefaultProperties().put("dataStoreType", "my-test-store").build();
    assertEquals(expected1, makeConfigData(ldConfig1));

    LDConfig ldConfig2 = new LDConfig.Builder()
        .dataStore(new DataStoreFactoryWithoutDiagnosticDescription())
        .build();
    LDValue expected2 = expectedDefaultProperties().put("dataStoreType", "custom").build();
    assertEquals(expected2, makeConfigData(ldConfig2));

    LDConfig ldConfig3 = new LDConfig.Builder()
        .dataStore(new DataStoreFactoryWithDiagnosticDescription(null))
        .build();
    LDValue expected3 = expectedDefaultProperties().put("dataStoreType", "custom").build();
    assertEquals(expected3, makeConfigData(ldConfig3));

    LDConfig ldConfig4 = new LDConfig.Builder()
        .dataStore(new DataStoreFactoryWithDiagnosticDescription(LDValue.of(4)))
        .build();
    LDValue expected4 = expectedDefaultProperties().put("dataStoreType", "custom").build();
    assertEquals(expected4, makeConfigData(ldConfig4));
  }

  @Test
  public void testCustomDiagnosticConfigurationForPersistentDataStore() {
    LDConfig ldConfig1 = new LDConfig.Builder()
        .dataStore(Components.persistentDataStore(new PersistentDataStoreFactoryWithComponentName()))
        .build();

    LDValue diagnosticJson1 = makeConfigData(ldConfig1);
    LDValue expected1 = expectedDefaultProperties().put("dataStoreType", "my-test-store").build();

    assertEquals(expected1, diagnosticJson1);

    LDConfig ldConfig2 = new LDConfig.Builder()
        .dataStore(Components.persistentDataStore(new PersistentDataStoreFactoryWithoutComponentName()))
        .build();

    LDValue diagnosticJson2 = makeConfigData(ldConfig2);
    LDValue expected2 = expectedDefaultProperties().put("dataStoreType", "custom").build();

    assertEquals(expected2, diagnosticJson2);
  }

  @Test
  public void testCustomDiagnosticConfigurationForEvents() {
    LDConfig ldConfig1 = new LDConfig.Builder()
          .events(
              Components.sendEvents()
                .allAttributesPrivate(true)
                .capacity(20_000)
                .diagnosticRecordingInterval(Duration.ofSeconds(1_800))
                .flushInterval(Duration.ofSeconds(10))
                .userKeysCapacity(2_000)
                .userKeysFlushInterval(Duration.ofSeconds(600))
              )
          .build();

    LDValue diagnosticJson1 = makeConfigData(ldConfig1);
    LDValue expected1 = expectedDefaultProperties()
        .put("allAttributesPrivate", true)
        .put("diagnosticRecordingIntervalMillis", 1_800_000)
        .put("eventsCapacity", 20_000)
        .put("eventsFlushIntervalMillis", 10_000)
        .put("userKeysCapacity", 2_000)
        .put("userKeysFlushIntervalMillis", 600_000)
        .build();

    assertEquals(expected1, diagnosticJson1);
    
    LDConfig ldConfig2 = new LDConfig.Builder()
        .events(Components.sendEvents()) // no custom base URI
        .build();
    
    LDValue diagnosticJson2 = makeConfigData(ldConfig2);
    LDValue expected2 = expectedDefaultProperties().build();
    
    assertEquals(expected2, diagnosticJson2);
}

  @Test
  public void testCustomDiagnosticConfigurationForDaemonMode() {
    LDConfig ldConfig = new LDConfig.Builder()
            .dataSource(Components.externalUpdatesOnly())
            .build();

    LDValue diagnosticJson = makeConfigData(ldConfig);
    LDValue expected = expectedDefaultPropertiesWithoutStreaming()
        .put("usingRelayDaemon", true)
        .build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationHttpProperties() {
    LDConfig ldConfig = new LDConfig.Builder()
        .http(
            Components.httpConfiguration()
              .connectTimeout(Duration.ofSeconds(5))
              .socketTimeout(Duration.ofSeconds(20))
              .proxyHostAndPort("localhost", 1234)
              .proxyAuth(Components.httpBasicAuthentication("username", "password"))
        )
        .build();

    LDValue diagnosticJson = makeConfigData(ldConfig);
    LDValue expected = expectedDefaultProperties()
        .put("connectTimeoutMillis", 5_000)
        .put("socketTimeoutMillis",  20_000)
        .put("usingProxy", true)
        .put("usingProxyAuthenticator", true)
        .build();

    assertEquals(expected, diagnosticJson);
  }
  
  @Test
  public void customComponentCannotInjectUnsupportedConfigProperty() {
    String unsupportedPropertyName = "fake";
    LDValue description = LDValue.buildObject().put(unsupportedPropertyName, true).build();
    LDConfig config = new LDConfig.Builder()
        .dataSource(new DataSourceFactoryWithDiagnosticDescription(description))
        .build();
    
    LDValue diagnosticJson = makeConfigData(config);
    
    assertThat(diagnosticJson.keys(), not(hasItem(unsupportedPropertyName)));
  }

  @Test
  public void customComponentCannotInjectSupportedConfigPropertyWithWrongType() {
    LDValue description = LDValue.buildObject().put("streamingDisabled", 3).build();
    LDConfig config = new LDConfig.Builder()
        .dataSource(new DataSourceFactoryWithDiagnosticDescription(description))
        .build();
    
    LDValue diagnosticJson = makeConfigData(config);
    
    assertThat(diagnosticJson.keys(), not(hasItem("streamingDisabled")));
  }

  @Test
  public void customComponentDescriptionOfUnsupportedTypeIsIgnored() {
    LDConfig config1 = new LDConfig.Builder()
        .dataSource(new DataSourceFactoryWithDiagnosticDescription(LDValue.of(3)))
        .build();
    LDConfig config2 = new LDConfig.Builder()
        .dataSource(new DataSourceFactoryWithoutDiagnosticDescription())
        .build();
    
    LDValue diagnosticJson1 = makeConfigData(config1);
    LDValue diagnosticJson2 = makeConfigData(config2);
    
    assertEquals(diagnosticJson1, diagnosticJson2);
  }

  private static class DataSourceFactoryWithDiagnosticDescription implements DataSourceFactory, DiagnosticDescription {
    private final LDValue value;
    
    DataSourceFactoryWithDiagnosticDescription(LDValue value) {
      this.value = value;
    }
    
    @Override
    public LDValue describeConfiguration(BasicConfiguration basicConfig) {
      return value;
    }

    @Override
    public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
      return null;
    }
  }

  private static class DataSourceFactoryWithoutDiagnosticDescription implements DataSourceFactory {
    @Override
    public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
      return null;
    }
  }

  private static class DataStoreFactoryWithDiagnosticDescription implements DataStoreFactory, DiagnosticDescription {
    private final LDValue value;
    
    DataStoreFactoryWithDiagnosticDescription(LDValue value) {
      this.value = value;
    }
    
    @Override
    public LDValue describeConfiguration(BasicConfiguration basicConfig) {
      return value;
    }

    @Override
    public DataStore createDataStore(ClientContext context, DataStoreUpdates dataStoreUpdates) {
      return null;
    }
  }

  private static class DataStoreFactoryWithoutDiagnosticDescription implements DataStoreFactory {
    @Override
    public DataStore createDataStore(ClientContext context, DataStoreUpdates dataStoreUpdates) {
      return null;
    }
  }
  
  private static class PersistentDataStoreFactoryWithComponentName implements PersistentDataStoreFactory, DiagnosticDescription {
    @Override
    public LDValue describeConfiguration(BasicConfiguration basicConfig) {
      return LDValue.of("my-test-store");
    }

    @Override
    public PersistentDataStore createPersistentDataStore(ClientContext context) {
      return null;
    }
  }
  
  private static class PersistentDataStoreFactoryWithoutComponentName implements PersistentDataStoreFactory {
    @Override
    public PersistentDataStore createPersistentDataStore(ClientContext context) {
      return null;
    }
  }
}
