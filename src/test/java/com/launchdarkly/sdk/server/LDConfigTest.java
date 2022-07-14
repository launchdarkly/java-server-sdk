package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.server.integrations.BigSegmentsConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.DataSourceFactory;
import com.launchdarkly.sdk.server.subsystems.DataStoreFactory;
import com.launchdarkly.sdk.server.subsystems.EventProcessorFactory;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDConfigTest {
  private static final ClientContext BASIC_CONTEXT = new ClientContext("");
  
  @Test
  public void defaults() {
    LDConfig config = new LDConfig.Builder().build();
    assertNotNull(config.bigSegmentsConfigBuilder);
    assertNull(config.bigSegmentsConfigBuilder.createBigSegmentsConfiguration(clientContext("", config)).getStore());
    assertNotNull(config.dataSourceFactory);
    assertEquals(Components.streamingDataSource().getClass(), config.dataSourceFactory.getClass());
    assertNotNull(config.dataStoreFactory);
    assertEquals(Components.inMemoryDataStore().getClass(), config.dataStoreFactory.getClass());
    assertFalse(config.diagnosticOptOut);
    assertNotNull(config.eventProcessorFactory);
    assertEquals(Components.sendEvents().getClass(), config.eventProcessorFactory.getClass());
    assertFalse(config.offline);
    
    assertNotNull(config.httpConfigFactory);
    HttpConfiguration httpConfig = config.httpConfigFactory.createHttpConfiguration(BASIC_CONTEXT);
    assertEquals(HttpConfigurationBuilder.DEFAULT_CONNECT_TIMEOUT, httpConfig.getConnectTimeout());
    
    assertNotNull(config.loggingConfigFactory);
    LoggingConfiguration loggingConfig = config.loggingConfigFactory.createLoggingConfiguration(BASIC_CONTEXT);
    assertEquals(LoggingConfigurationBuilder.DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER,
        loggingConfig.getLogDataSourceOutageAsErrorAfter());
    
    assertEquals(LDConfig.DEFAULT_START_WAIT, config.startWait);
    assertEquals(Thread.MIN_PRIORITY, config.threadPriority);
  }

  @Test
  public void bigSegmentsConfigFactory() {
    BigSegmentsConfigurationBuilder f = Components.bigSegments(null);
    LDConfig config = new LDConfig.Builder().bigSegments(f).build();
    assertSame(f, config.bigSegmentsConfigBuilder);
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
    LDConfig config1 = new LDConfig.Builder().offline(true).build();
    assertTrue(config1.offline);
    assertSame(Components.externalUpdatesOnly(), config1.dataSourceFactory);
    assertSame(Components.noEvents(), config1.eventProcessorFactory);
    
    LDConfig config2 = new LDConfig.Builder().offline(true).dataSource(Components.streamingDataSource()).build();
    assertTrue(config2.offline);
    assertSame(Components.externalUpdatesOnly(), config2.dataSourceFactory); // offline overrides specified factory
    assertSame(Components.noEvents(), config2.eventProcessorFactory);
    
    LDConfig config3 = new LDConfig.Builder().offline(true).offline(false).build();
    assertFalse(config3.offline); // just testing that the setter works for both true and false
  }

  @Test
  public void http() {
    HttpConfigurationBuilder b = Components.httpConfiguration().connectTimeout(Duration.ofSeconds(9));
    LDConfig config = new LDConfig.Builder().http(b).build();
    assertEquals(Duration.ofSeconds(9),
        config.httpConfigFactory.createHttpConfiguration(BASIC_CONTEXT).getConnectTimeout());
  }

  @Test
  public void logging() {
    LoggingConfigurationBuilder b = Components.logging().logDataSourceOutageAsErrorAfter(Duration.ofSeconds(9));
    LDConfig config = new LDConfig.Builder().logging(b).build();
    assertEquals(Duration.ofSeconds(9),
        config.loggingConfigFactory.createLoggingConfiguration(BASIC_CONTEXT).getLogDataSourceOutageAsErrorAfter());
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
  public void testHttpDefaults() {
    LDConfig config = new LDConfig.Builder().build();
    HttpConfiguration hc = config.httpConfigFactory.createHttpConfiguration(BASIC_CONTEXT);
    HttpConfiguration defaults = Components.httpConfiguration().createHttpConfiguration(BASIC_CONTEXT);
    assertEquals(defaults.getConnectTimeout(), hc.getConnectTimeout());
    assertNull(hc.getProxy());
    assertNull(hc.getProxyAuthentication());
    assertEquals(defaults.getSocketTimeout(), hc.getSocketTimeout());
    assertNull(hc.getSslSocketFactory());
    assertNull(hc.getTrustManager());
    assertEquals(ImmutableMap.copyOf(defaults.getDefaultHeaders()), ImmutableMap.copyOf(hc.getDefaultHeaders()));
  }
}
