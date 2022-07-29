package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.logging.LDLogLevel;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LDSLF4J;
import com.launchdarkly.logging.LogCapture;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

import org.junit.Test;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class LoggingConfigurationBuilderTest {
  private static final String SDK_KEY = "sdk-key";
  private static final ClientContext BASIC_CONTEXT = new ClientContext(SDK_KEY);

  @Test
  public void testDefaults() {
    LoggingConfiguration c = Components.logging().createLoggingConfiguration(BASIC_CONTEXT);
    assertEquals(LoggingConfigurationBuilder.DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER,
        c.getLogDataSourceOutageAsErrorAfter());
  }
  
  @Test
  public void logDataSourceOutageAsErrorAfter() {
    LoggingConfiguration c1 = Components.logging()
        .logDataSourceOutageAsErrorAfter(Duration.ofMinutes(9))
        .createLoggingConfiguration(BASIC_CONTEXT);
    assertEquals(Duration.ofMinutes(9), c1.getLogDataSourceOutageAsErrorAfter());

    LoggingConfiguration c2 = Components.logging()
        .logDataSourceOutageAsErrorAfter(null)
        .createLoggingConfiguration(BASIC_CONTEXT);
    assertNull(c2.getLogDataSourceOutageAsErrorAfter());
  }
  
  @Test
  public void defaultLogAdapterIsSLF4J() {
    LoggingConfiguration c = Components.logging()
        .createLoggingConfiguration(BASIC_CONTEXT);
    assertThat(c.getLogAdapter(), sameInstance(LDSLF4J.adapter()));
  }
  
  @Test
  public void canSetLogAdapterAndLevel() {
    LogCapture logSink = Logs.capture();
    LoggingConfiguration c = Components.logging()
        .adapter(logSink)
        .level(LDLogLevel.WARN)
        .createLoggingConfiguration(BASIC_CONTEXT);
    LDLogger logger = LDLogger.withAdapter(c.getLogAdapter(), "");
    logger.debug("message 1");
    logger.info("message 2");
    logger.warn("message 3");
    logger.error("message 4");
    assertThat(logSink.getMessageStrings(), contains("WARN:message 3", "ERROR:message 4"));
  }

  @Test
  public void defaultLevelIsInfo() {
    LogCapture logSink = Logs.capture();
    LoggingConfiguration c = Components.logging()
        .adapter(logSink)
        .createLoggingConfiguration(BASIC_CONTEXT);
    LDLogger logger = LDLogger.withAdapter(c.getLogAdapter(), "");
    logger.debug("message 1");
    logger.info("message 2");
    logger.warn("message 3");
    logger.error("message 4");
    assertThat(logSink.getMessageStrings(), contains("INFO:message 2", "WARN:message 3", "ERROR:message 4"));
  }
}
