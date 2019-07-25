package com.launchdarkly.client;

import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DiagnosticAccumulatorTest {

  @Test
  public void startSetsDiagnosticId() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    long currentTime = System.currentTimeMillis();
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator();
    diagnosticAccumulator.start(diagnosticId, currentTime);
    assertSame(diagnosticId, diagnosticAccumulator.diagnosticId);
  }

  @Test
  public void startSetsDataSinceDate() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    long currentTime = System.currentTimeMillis();
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator();
    diagnosticAccumulator.start(diagnosticId, currentTime);
    assertEquals(currentTime, diagnosticAccumulator.dataSinceDate);
  }

  @Test
  public void createsDiagnosticStatisticsEvent() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    long currentTime = System.currentTimeMillis();
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator();
    diagnosticAccumulator.start(diagnosticId, currentTime);
    DiagnosticEvent.Statistics diagnosticStatisticsEvent = diagnosticAccumulator.createEventAndReset(10, 15, 20);
    assertSame(diagnosticId, diagnosticStatisticsEvent.id);
    assertEquals(10, diagnosticStatisticsEvent.droppedEvents);
    assertEquals(15, diagnosticStatisticsEvent.deduplicatedUsers);
    assertEquals(20, diagnosticStatisticsEvent.eventsInQueue);
    assertEquals(currentTime, diagnosticStatisticsEvent.dataSinceDate);
  }

  @Test
  public void resetsDataSinceDate() throws InterruptedException {
    long currentTime = System.currentTimeMillis();
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator();
    diagnosticAccumulator.start(null, currentTime);
    Thread.sleep(2);
    diagnosticAccumulator.createEventAndReset(0, 0, 0);
    assertNotEquals(currentTime, diagnosticAccumulator.dataSinceDate);
  }
}
