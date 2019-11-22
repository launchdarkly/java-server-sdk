package com.launchdarkly.client;

import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DiagnosticAccumulatorTest {

  @Test
  public void createsDiagnosticStatisticsEvent() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    long startDate = diagnosticAccumulator.dataSinceDate;
    DiagnosticEvent.Statistics diagnosticStatisticsEvent = diagnosticAccumulator.createEventAndReset(10, 15, 20);
    assertSame(diagnosticId, diagnosticStatisticsEvent.id);
    assertEquals(10, diagnosticStatisticsEvent.droppedEvents);
    assertEquals(15, diagnosticStatisticsEvent.deduplicatedUsers);
    assertEquals(20, diagnosticStatisticsEvent.eventsInQueue);
    assertEquals(startDate, diagnosticStatisticsEvent.dataSinceDate);
  }

  @Test
  public void resetsDataSinceDate() throws InterruptedException {
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId("SDK_KEY"));
    long startDate = diagnosticAccumulator.dataSinceDate;
    Thread.sleep(2);
    diagnosticAccumulator.createEventAndReset(0, 0, 0);
    assertNotEquals(startDate, diagnosticAccumulator.dataSinceDate);
  }
}
