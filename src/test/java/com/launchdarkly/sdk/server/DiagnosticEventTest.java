package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.DiagnosticDescription;

import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class DiagnosticEventTest {

  private static Gson gson = new Gson();
  private static List<DiagnosticEvent.StreamInit> testStreamInits = Collections.singletonList(new DiagnosticEvent.StreamInit(1500, 100, true));

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
        .put("inlineUsersInEvents", false)
        .put("offline", false)
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
  
  @Test
  public void testDefaultDiagnosticConfiguration() {
    LDConfig ldConfig = new LDConfig.Builder().build();
    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    LDValue expected = expectedDefaultProperties().build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationGeneralProperties() {
    LDConfig ldConfig = new LDConfig.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .socketTimeout(Duration.ofSeconds(20))
            .startWait(Duration.ofSeconds(10))
            .proxyPort(1234)
            .proxyUsername("username")
            .proxyPassword("password")
            .build();

    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    LDValue expected = expectedDefaultProperties()
        .put("connectTimeoutMillis", 5_000)
        .put("socketTimeoutMillis",  20_000)
        .put("startWaitMillis", 10_000)
        .put("usingProxy", true)
        .put("usingProxyAuthenticator", true)
        .build();

    assertEquals(expected, diagnosticJson);
  }
  
  @Test
  public void testCustomDiagnosticConfigurationForStreaming() {
    LDConfig ldConfig = new LDConfig.Builder()
            .dataSource(
                Components.streamingDataSource()
                  .baseURI(URI.create("https://1.1.1.1"))
                  .pollingBaseURI(URI.create("https://1.1.1.1"))
                  .initialReconnectDelay(Duration.ofSeconds(2))
                )
            .build();

    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    LDValue expected = expectedDefaultPropertiesWithoutStreaming()
        .put("customBaseURI", true)
        .put("customStreamURI", true)
        .put("reconnectTimeMillis", 2_000)
        .build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationForPolling() {
    LDConfig ldConfig = new LDConfig.Builder()
            .dataSource(
                Components.pollingDataSource()
                  .baseURI(URI.create("https://1.1.1.1"))
                  .pollInterval(Duration.ofSeconds(60))
                )
            .build();

    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    LDValue expected = expectedDefaultPropertiesWithoutStreaming()
        .put("customBaseURI", true)
        .put("pollingIntervalMillis", 60_000)
        .put("streamingDisabled", true)
        .build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationForEvents() {
    LDConfig ldConfig = new LDConfig.Builder()
          .events(
              Components.sendEvents()
                .allAttributesPrivate(true)
                .capacity(20_000)
                .diagnosticRecordingInterval(Duration.ofSeconds(1_800))
                .flushInterval(Duration.ofSeconds(10))
                .inlineUsersInEvents(true)
                .userKeysCapacity(2_000)
                .userKeysFlushInterval(Duration.ofSeconds(600))
              )
          .build();

    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    LDValue expected = expectedDefaultProperties()
        .put("allAttributesPrivate", true)
        .put("diagnosticRecordingIntervalMillis", 1_800_000)
        .put("eventsCapacity", 20_000)
        .put("eventsFlushIntervalMillis", 10_000)
        .put("inlineUsersInEvents", true)
        .put("userKeysCapacity", 2_000)
        .put("userKeysFlushIntervalMillis", 600_000)
        .build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationForDaemonMode() {
    LDConfig ldConfig = new LDConfig.Builder()
            .dataSource(Components.externalUpdatesOnly())
            .dataStore(new DataStoreFactoryWithComponentName())
            .build();

    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    LDValue expected = expectedDefaultPropertiesWithoutStreaming()
        .put("dataStoreType", "my-test-store")
        .put("usingRelayDaemon", true)
        .build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfigurationForOffline() {
    LDConfig ldConfig = new LDConfig.Builder()
            .offline(true)
            .build();

    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    LDValue expected = expectedDefaultPropertiesWithoutStreaming()
        .put("offline", true)
        .build();

    assertEquals(expected, diagnosticJson);
  }
  
  private static class DataStoreFactoryWithComponentName implements DataStoreFactory, DiagnosticDescription {
    @Override
    public LDValue describeConfiguration(LDConfig config) {
      return LDValue.of("my-test-store");
    }

    @Override
    public DataStore createDataStore(ClientContext context) {
      return null;
    }
  }
}
