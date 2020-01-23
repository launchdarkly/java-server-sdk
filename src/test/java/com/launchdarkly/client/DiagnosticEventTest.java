package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.launchdarkly.client.interfaces.DiagnosticDescription;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class DiagnosticEventTest {

  private static Gson gson = new Gson();
  private static List<DiagnosticEvent.StreamInit> testStreamInits = Collections.singletonList(new DiagnosticEvent.StreamInit(1500, 100, true));

  @SuppressWarnings("ResultOfMethodCallIgnored")
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
    UUID.fromString(idObject.getAsJsonPrimitive("diagnosticId").getAsString());
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

  @Test
  public void testDefaultDiagnosticConfiguration() {
    LDConfig ldConfig = new LDConfig.Builder().build();
    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    LDValue expected = LDValue.buildObject()
        .put("allAttributesPrivate", false)
        .put("connectTimeoutMillis", 2_000)
        .put("customBaseURI", false)
        .put("customEventsURI", false)
        .put("customStreamURI", false)
        .put("dataStore", "memory")
        .put("diagnosticRecordingIntervalMillis", 900_000)
        .put("eventsCapacity", 10_000)
        .put("eventsFlushIntervalMillis",5_000)
        .put("inlineUsersInEvents", false)
        .put("offline", false)
        .put("pollingIntervalMillis", 30_000)
        .put("reconnectTimeMillis", 1_000)
        .put("samplingInterval", 0)
        .put("socketTimeoutMillis", 10_000)
        .put("startWaitMillis", 5_000)
        .put("streamingDisabled", false)
        .put("userKeysCapacity", 1_000)
        .put("userKeysFlushIntervalMillis", 300_000)
        .put("usingProxy", false)
        .put("usingProxyAuthenticator", false)
        .put("usingRelayDaemon", false)
        .build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void testCustomDiagnosticConfiguration() {
    @SuppressWarnings("deprecation")
    LDConfig ldConfig = new LDConfig.Builder()
            .allAttributesPrivate(true)
            .connectTimeout(5)
            .baseURI(URI.create("https://1.1.1.1"))
            .eventsURI(URI.create("https://1.1.1.1"))
            .streamURI(URI.create("https://1.1.1.1"))
            .diagnosticRecordingIntervalMillis(1_800_000)
            .sendEvents(false)
            .capacity(20_000)
            .flushInterval(10)
            .dataStore(Components.redisFeatureStore())
            .inlineUsersInEvents(true)
            .offline(true)
            .pollingIntervalMillis(60_000)
            .reconnectTimeMs(2_000)
            .samplingInterval(1)
            .socketTimeout(20)
            .startWaitMillis(10_000)
            .stream(false)
            .userKeysCapacity(2_000)
            .userKeysFlushInterval(600)
            .proxyPort(1234)
            .proxyUsername("username")
            .proxyPassword("password")
            .useLdd(true)
            .build();

    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    LDValue expected = LDValue.buildObject()
        .put("allAttributesPrivate", true)
        .put("connectTimeoutMillis", 5_000)
        .put("customBaseURI", true)
        .put("customEventsURI", true)
        .put("customStreamURI", true)
        .put("dataStore", "Redis")
        .put("diagnosticRecordingIntervalMillis", 1_800_000)
        .put("eventsCapacity", 20_000)
        .put("eventsFlushIntervalMillis",10_000)
        .put("inlineUsersInEvents", true)
        .put("offline", true)
        .put("pollingIntervalMillis", 60_000)
        .put("reconnectTimeMillis", 2_000)
        .put("samplingInterval", 1)
        .put("socketTimeoutMillis", 20_000)
        .put("startWaitMillis", 10_000)
        .put("streamingDisabled", true)
        .put("userKeysCapacity",  2_000)
        .put("userKeysFlushIntervalMillis", 600_000)
        .put("usingProxy", true)
        .put("usingProxyAuthenticator", true)
        .put("usingRelayDaemon", true)
        .build();

    assertEquals(expected, diagnosticJson);
  }

  @Test
  public void customComponentCannotInjectOverlyLongData() {
    LDConfig ldConfig = new LDConfig.Builder().dataStore(new FakeStoreFactory()).build();
    LDValue diagnosticJson = DiagnosticEvent.Init.getConfigurationData(ldConfig);
    assertEquals(FakeStoreFactory.veryLongString().substring(0, 100), diagnosticJson.get("dataStore").stringValue());
  }
  
  private static class FakeStoreFactory implements FeatureStoreFactory, DiagnosticDescription {
    @Override
    public LDValue describeConfiguration() {
      return LDValue.of(veryLongString());
    }

    @Override
    public FeatureStore createFeatureStore() {
      return null;
    }    
    
    public static String veryLongString() {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < 128; i++) {
        b.append('@' + i);
      }
      return b.toString();
    }
  }
}
