package com.launchdarkly.client.integrations;

import com.launchdarkly.client.Components;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EventProcessorBuilderTest {
  @Test
  public void testDefaultDiagnosticRecordingInterval() {
    EventProcessorBuilder builder = Components.sendEvents();
    assertEquals(900, builder.diagnosticRecordingIntervalSeconds);
  }

  @Test
  public void testDiagnosticRecordingInterval() {
    EventProcessorBuilder builder = Components.sendEvents().diagnosticRecordingIntervalSeconds(120);
    assertEquals(120, builder.diagnosticRecordingIntervalSeconds);
  }

  @Test
  public void testMinimumDiagnosticRecordingIntervalEnforced() {
    EventProcessorBuilder builder = Components.sendEvents().diagnosticRecordingIntervalSeconds(10);
    assertEquals(60, builder.diagnosticRecordingIntervalSeconds);
  }

}
