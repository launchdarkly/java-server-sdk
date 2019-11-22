package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    assertEquals(3, jsonObject.getAsJsonPrimitive("eventsInQueue").getAsLong());
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
    DiagnosticEvent.Init.DiagnosticConfiguration diagnosticConfiguration = new DiagnosticEvent.Init.DiagnosticConfiguration(ldConfig);
    JsonObject diagnosticJson = new Gson().toJsonTree(diagnosticConfiguration).getAsJsonObject();
    JsonObject expected = new JsonObject();
    expected.addProperty("allAttributesPrivate", false);
    expected.addProperty("connectTimeoutMillis", 2_000);
    expected.addProperty("customBaseURI", false);
    expected.addProperty("customEventsURI", false);
    expected.addProperty("customStreamURI", false);
    expected.addProperty("diagnosticRecordingIntervalMillis", 900_000);
    expected.addProperty("eventsCapacity", 10_000);
    expected.addProperty("eventsFlushIntervalMillis",5_000);
    expected.addProperty("featureStore", "InMemoryFeatureStoreFactory");
    expected.addProperty("inlineUsersInEvents", false);
    expected.addProperty("offline", false);
    expected.addProperty("pollingIntervalMillis", 30_000);
    expected.addProperty("reconnectTimeMillis", 1_000);
    expected.addProperty("samplingInterval", 0);
    expected.addProperty("socketTimeoutMillis", 10_000);
    expected.addProperty("startWaitMillis", 5_000);
    expected.addProperty("streamingDisabled", false);
    expected.addProperty("userKeysCapacity", 1_000);
    expected.addProperty("userKeysFlushIntervalMillis", 300_000);
    expected.addProperty("usingProxy", false);
    expected.addProperty("usingProxyAuthenticator", false);
    expected.addProperty("usingRelayDaemon", false);

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
            .featureStoreFactory(Components.redisFeatureStore())
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

    DiagnosticEvent.Init.DiagnosticConfiguration diagnosticConfiguration = new DiagnosticEvent.Init.DiagnosticConfiguration(ldConfig);
    JsonObject diagnosticJson = gson.toJsonTree(diagnosticConfiguration).getAsJsonObject();
    JsonObject expected = new JsonObject();
    expected.addProperty("allAttributesPrivate", true);
    expected.addProperty("connectTimeoutMillis", 5_000);
    expected.addProperty("customBaseURI", true);
    expected.addProperty("customEventsURI", true);
    expected.addProperty("customStreamURI", true);
    expected.addProperty("diagnosticRecordingIntervalMillis", 1_800_000);
    expected.addProperty("eventsCapacity", 20_000);
    expected.addProperty("eventsFlushIntervalMillis",10_000);
    expected.addProperty("featureStore", "RedisFeatureStoreBuilder");
    expected.addProperty("inlineUsersInEvents", true);
    expected.addProperty("offline", true);
    expected.addProperty("pollingIntervalMillis", 60_000);
    expected.addProperty("reconnectTimeMillis", 2_000);
    expected.addProperty("samplingInterval", 1);
    expected.addProperty("socketTimeoutMillis", 20_000);
    expected.addProperty("startWaitMillis", 10_000);
    expected.addProperty("streamingDisabled", true);
    expected.addProperty("userKeysCapacity",  2_000);
    expected.addProperty("userKeysFlushIntervalMillis", 600_000);
    expected.addProperty("usingProxy", true);
    expected.addProperty("usingProxyAuthenticator", true);
    expected.addProperty("usingRelayDaemon", true);

    assertEquals(expected, diagnosticJson);
  }

}
