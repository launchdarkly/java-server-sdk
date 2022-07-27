package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class LoggingConfigurationBuilderTest {
  private static final String SDK_KEY = "sdk-key";
  private static final ClientContext BASIC_CONTEXT = new ClientContext(SDK_KEY);

  @Test
  public void testDefaults() {
    LoggingConfiguration c = Components.logging().build(BASIC_CONTEXT);
    assertEquals(LoggingConfigurationBuilder.DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER,
        c.getLogDataSourceOutageAsErrorAfter());
  }
  
  @Test
  public void logDataSourceOutageAsErrorAfter() {
    LoggingConfiguration c1 = Components.logging()
        .logDataSourceOutageAsErrorAfter(Duration.ofMinutes(9))
        .build(BASIC_CONTEXT);
    assertEquals(Duration.ofMinutes(9), c1.getLogDataSourceOutageAsErrorAfter());

    LoggingConfiguration c2 = Components.logging()
        .logDataSourceOutageAsErrorAfter(null)
        .build(BASIC_CONTEXT);
    assertNull(c2.getLogDataSourceOutageAsErrorAfter());
  }
}
