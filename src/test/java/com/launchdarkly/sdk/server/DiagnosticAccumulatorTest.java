package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.DiagnosticAccumulator;
import com.launchdarkly.sdk.server.DiagnosticEvent;
import com.launchdarkly.sdk.server.DiagnosticId;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class DiagnosticAccumulatorTest {
  @Test
  public void createsDiagnosticStatisticsEvent() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(diagnosticId);
    long startDate = diagnosticAccumulator.dataSinceDate;
    DiagnosticEvent.Statistics diagnosticStatisticsEvent = diagnosticAccumulator.createEventAndReset(10, 15);
    assertSame(diagnosticId, diagnosticStatisticsEvent.id);
    assertEquals(10, diagnosticStatisticsEvent.droppedEvents);
    assertEquals(15, diagnosticStatisticsEvent.deduplicatedUsers);
    assertEquals(0, diagnosticStatisticsEvent.eventsInLastBatch);
    assertEquals(startDate, diagnosticStatisticsEvent.dataSinceDate);
  }

  @Test
  public void canRecordStreamInit() {
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId("SDK_KEY"));
    diagnosticAccumulator.recordStreamInit(1000, 200, false);
    DiagnosticEvent.Statistics statsEvent = diagnosticAccumulator.createEventAndReset(0, 0);
    assertEquals(1, statsEvent.streamInits.size());
    assertEquals(1000, statsEvent.streamInits.get(0).timestamp);
    assertEquals(200, statsEvent.streamInits.get(0).durationMillis);
    assertEquals(false, statsEvent.streamInits.get(0).failed);
  }

  @Test
  public void canRecordEventsInBatch() {
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId("SDK_KEY"));
    diagnosticAccumulator.recordEventsInBatch(100);
    DiagnosticEvent.Statistics statsEvent = diagnosticAccumulator.createEventAndReset(0, 0);
    assertEquals(100, statsEvent.eventsInLastBatch);
  }

  @Test
  public void resetsAccumulatorFieldsOnCreate() throws InterruptedException {
    DiagnosticAccumulator diagnosticAccumulator = new DiagnosticAccumulator(new DiagnosticId("SDK_KEY"));
    diagnosticAccumulator.recordStreamInit(1000, 200, false);
    diagnosticAccumulator.recordEventsInBatch(100);
    long startDate = diagnosticAccumulator.dataSinceDate;
    Thread.sleep(2);
    diagnosticAccumulator.createEventAndReset(0, 0);
    assertNotEquals(startDate, diagnosticAccumulator.dataSinceDate);
    DiagnosticEvent.Statistics resetEvent = diagnosticAccumulator.createEventAndReset(0,0);
    assertEquals(0, resetEvent.streamInits.size());
    assertEquals(0, resetEvent.eventsInLastBatch);
  }
}
