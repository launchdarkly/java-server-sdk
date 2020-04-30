package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;

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
