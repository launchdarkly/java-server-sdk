package com.launchdarkly.client.integrations;

import com.launchdarkly.client.Components;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EventProcessorBuilderTest {
  @Test
  public void testDefaultDiagnosticRecordingInterval() {
    EventProcessorBuilder builder = Components.sendEvents();
    assertEquals(Duration.ofSeconds(900), builder.diagnosticRecordingInterval);
  }

  @Test
  public void testDiagnosticRecordingInterval() {
    EventProcessorBuilder builder = Components.sendEvents().diagnosticRecordingInterval(Duration.ofSeconds(120));
    assertEquals(Duration.ofSeconds(120), builder.diagnosticRecordingInterval);
  }

  @Test
  public void testMinimumDiagnosticRecordingIntervalEnforced() {
    EventProcessorBuilder builder = Components.sendEvents().diagnosticRecordingInterval(Duration.ofSeconds(10));
    assertEquals(Duration.ofSeconds(60), builder.diagnosticRecordingInterval);
  }
}
