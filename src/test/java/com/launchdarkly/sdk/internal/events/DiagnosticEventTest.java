package com.launchdarkly.sdk.internal.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.launchdarkly.sdk.internal.GsonHelpers.gsonInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class DiagnosticEventTest {
  private static List<DiagnosticEvent.StreamInit> testStreamInits =
      Collections.singletonList(new DiagnosticEvent.StreamInit(1500, 100, true));

  @Test
  public void testSerialization() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    DiagnosticEvent.Statistics diagnosticStatisticsEvent = new DiagnosticEvent.Statistics(2000, diagnosticId, 1000, 1, 2, 3, testStreamInits);
    JsonObject jsonObject = gsonInstance().toJsonTree(diagnosticStatisticsEvent).getAsJsonObject();
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
}
