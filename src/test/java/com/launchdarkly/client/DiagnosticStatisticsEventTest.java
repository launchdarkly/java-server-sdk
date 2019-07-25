package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class DiagnosticStatisticsEventTest {

  @Test
  public void testConstructor() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    DiagnosticEvent.Statistics diagnosticStatisticsEvent = new DiagnosticEvent.Statistics(2000, diagnosticId, 1000, 1, 2, 3);
    assertEquals("diagnostic", diagnosticStatisticsEvent.kind);
    assertEquals(2000, diagnosticStatisticsEvent.creationDate);
    assertSame(diagnosticId, diagnosticStatisticsEvent.id);
    assertEquals(1000, diagnosticStatisticsEvent.dataSinceDate);
    assertEquals(1, diagnosticStatisticsEvent.droppedEvents);
    assertEquals(2, diagnosticStatisticsEvent.deduplicatedUsers);
    assertEquals(3, diagnosticStatisticsEvent.eventsInQueue);
  }

  @Test
  public void testSerialization() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    DiagnosticEvent.Statistics diagnosticStatisticsEvent = new DiagnosticEvent.Statistics(2000, diagnosticId, 1000, 1, 2, 3);
    Gson gson = new Gson();
    JsonObject jsonObject = gson.toJsonTree(diagnosticStatisticsEvent).getAsJsonObject();
    assertEquals(7, jsonObject.size());
    assertEquals("diagnostic", diagnosticStatisticsEvent.kind);
    assertEquals(2000, jsonObject.getAsJsonPrimitive("creationDate").getAsLong());
    JsonObject idObject = jsonObject.getAsJsonObject("id");
    assertEquals("DK_KEY", idObject.getAsJsonPrimitive("sdkKeySuffix").getAsString());
    assertNotNull(UUID.fromString(idObject.getAsJsonPrimitive("diagnosticId").getAsString()));
    assertEquals(1000, jsonObject.getAsJsonPrimitive("dataSinceDate").getAsLong());
    assertEquals(1, jsonObject.getAsJsonPrimitive("droppedEvents").getAsLong());
    assertEquals(2, jsonObject.getAsJsonPrimitive("deduplicatedUsers").getAsLong());
    assertEquals(3, jsonObject.getAsJsonPrimitive("eventsInQueue").getAsLong());
  }

}
