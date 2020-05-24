package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDConfigTest {
  @Test
  public void defaults() {
    LDConfig config = new LDConfig.Builder().build();
    assertNull(config.dataSourceFactory);
    assertNull(config.dataStoreFactory);
    assertFalse(config.diagnosticOptOut);
    assertNull(config.eventProcessorFactory);
    assertFalse(config.offline);
    
    assertNotNull(config.httpConfig);
    assertEquals(HttpConfigurationBuilder.DEFAULT_CONNECT_TIMEOUT, config.httpConfig.getConnectTimeout());
    
    assertNotNull(config.loggingConfig);
    assertEquals(LoggingConfigurationBuilder.DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER,
        config.loggingConfig.getLogDataSourceOutageAsErrorAfter());
    
    assertEquals(LDConfig.DEFAULT_START_WAIT, config.startWait);
    assertEquals(Thread.MIN_PRIORITY, config.threadPriority);
  }
  
  @Test
  public void dataSourceFactory() {
    DataSourceFactory f = TestComponents.specificDataSource(null);
    LDConfig config = new LDConfig.Builder().dataSource(f).build();
    assertSame(f, config.dataSourceFactory);
  }

  @Test
  public void dataStoreFactory() {
    DataStoreFactory f = TestComponents.specificDataStore(null);
    LDConfig config = new LDConfig.Builder().dataStore(f).build();
    assertSame(f, config.dataStoreFactory);
  }

  @Test
  public void diagnosticOptOut() {
    LDConfig config = new LDConfig.Builder().diagnosticOptOut(true).build();
    assertTrue(config.diagnosticOptOut);
    
    LDConfig config1 = new LDConfig.Builder().diagnosticOptOut(true).diagnosticOptOut(false).build();
    assertFalse(config1.diagnosticOptOut);
  }

  @Test
  public void eventProcessorFactory() {
    EventProcessorFactory f = TestComponents.specificEventProcessor(null);
    LDConfig config = new LDConfig.Builder().events(f).build();
    assertSame(f, config.eventProcessorFactory);
  }

  @Test
  public void offline() {
    LDConfig config = new LDConfig.Builder().offline(true).build();
    assertTrue(config.offline);
    
    LDConfig config1 = new LDConfig.Builder().offline(true).offline(false).build();
    assertFalse(config1.offline);
  }

  @Test
  public void http() {
    HttpConfigurationBuilder b = Components.httpConfiguration().connectTimeout(Duration.ofSeconds(9));
    LDConfig config = new LDConfig.Builder().http(b).build();
    assertEquals(Duration.ofSeconds(9), config.httpConfig.getConnectTimeout());
  }

  @Test
  public void logging() {
    LoggingConfigurationBuilder b = Components.logging().logDataSourceOutageAsErrorAfter(Duration.ofSeconds(9));
    LDConfig config = new LDConfig.Builder().logging(b).build();
    assertEquals(Duration.ofSeconds(9), config.loggingConfig.getLogDataSourceOutageAsErrorAfter());
  }

  @Test
  public void startWait() {
    LDConfig config = new LDConfig.Builder().startWait(Duration.ZERO).build();
    assertEquals(Duration.ZERO, config.startWait);

    LDConfig config1 = new LDConfig.Builder().startWait(Duration.ZERO).startWait(null).build();
    assertEquals(LDConfig.DEFAULT_START_WAIT, config1.startWait);
  }
  
  @Test
  public void threadPriority() {
    LDConfig config = new LDConfig.Builder().threadPriority(Thread.MAX_PRIORITY).build();
    assertEquals(Thread.MAX_PRIORITY, config.threadPriority);
  }
  
  @Test
  public void testWrapperNotConfigured() {
    LDConfig config = new LDConfig.Builder().build();
    assertNull(config.httpConfig.getWrapperIdentifier());
  }

  @Test
  public void testWrapperNameOnly() {
    LDConfig config = new LDConfig.Builder()
        .http(
            Components.httpConfiguration()
              .wrapper("Scala", null)
        )
        .build();
    assertEquals("Scala", config.httpConfig.getWrapperIdentifier());
  }

  @Test
  public void testWrapperWithVersion() {
    LDConfig config = new LDConfig.Builder()
        .http(
            Components.httpConfiguration()
              .wrapper("Scala", "0.1.0")
        )
        .build();
    assertEquals("Scala/0.1.0", config.httpConfig.getWrapperIdentifier());
  }

  @Test
  public void testHttpDefaults() {
    LDConfig config = new LDConfig.Builder().build();
    HttpConfiguration hc = config.httpConfig;
    HttpConfiguration defaults = Components.httpConfiguration().createHttpConfiguration();
    assertEquals(defaults.getConnectTimeout(), hc.getConnectTimeout());
    assertNull(hc.getProxy());
    assertNull(hc.getProxyAuthentication());
    assertEquals(defaults.getSocketTimeout(), hc.getSocketTimeout());
    assertNull(hc.getSslSocketFactory());
    assertNull(hc.getTrustManager());
    assertNull(hc.getWrapperIdentifier());
  }
}