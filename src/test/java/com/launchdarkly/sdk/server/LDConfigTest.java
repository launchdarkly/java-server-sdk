package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.server.integrations.BigSegmentsConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.specificComponent;
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
    assertNotNull(config.bigSegments);
    assertNull(config.bigSegments.build(clientContext("", config)).getStore());
    assertNotNull(config.dataSource);
    assertEquals(Components.streamingDataSource().getClass(), config.dataSource.getClass());
    assertNotNull(config.dataStore);
    assertEquals(Components.inMemoryDataStore().getClass(), config.dataStore.getClass());
    assertFalse(config.diagnosticOptOut);
    assertNotNull(config.events);
    assertEquals(Components.sendEvents().getClass(), config.events.getClass());
    assertFalse(config.offline);
    
    assertNotNull(config.http);
    HttpConfiguration httpConfig = config.http.build(BASIC_CONTEXT);
    assertEquals(HttpConfigurationBuilder.DEFAULT_CONNECT_TIMEOUT, httpConfig.getConnectTimeout());
    
    assertNotNull(config.logging);
    LoggingConfiguration loggingConfig = config.logging.build(BASIC_CONTEXT);
    assertEquals(LoggingConfigurationBuilder.DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER,
        loggingConfig.getLogDataSourceOutageAsErrorAfter());
    
    assertEquals(LDConfig.DEFAULT_START_WAIT, config.startWait);
    assertEquals(Thread.MIN_PRIORITY, config.threadPriority);
  }

  @Test
  public void bigSegmentsConfigFactory() {
    BigSegmentsConfigurationBuilder f = Components.bigSegments(null);
    LDConfig config = new LDConfig.Builder().bigSegments(f).build();
    assertSame(f, config.bigSegments);
  }
  
  @Test
  public void dataSourceFactory() {
    ComponentConfigurer<DataSource> f = specificComponent(null);
    LDConfig config = new LDConfig.Builder().dataSource(f).build();
    assertSame(f, config.dataSource);
  }

  @Test
  public void dataStoreFactory() {
    ComponentConfigurer<DataStore> f = specificComponent(null);
    LDConfig config = new LDConfig.Builder().dataStore(f).build();
    assertSame(f, config.dataStore);
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
    ComponentConfigurer<EventProcessor> f = specificComponent(null);
    LDConfig config = new LDConfig.Builder().events(f).build();
    assertSame(f, config.events);
  }

  @Test
  public void offline() {
    LDConfig config1 = new LDConfig.Builder().offline(true).build();
    assertTrue(config1.offline);
    assertSame(Components.externalUpdatesOnly(), config1.dataSource);
    assertSame(Components.noEvents(), config1.events);
    
    LDConfig config2 = new LDConfig.Builder().offline(true).dataSource(Components.streamingDataSource()).build();
    assertTrue(config2.offline);
    assertSame(Components.externalUpdatesOnly(), config2.dataSource); // offline overrides specified factory
    assertSame(Components.noEvents(), config2.events);
    
    LDConfig config3 = new LDConfig.Builder().offline(true).offline(false).build();
    assertFalse(config3.offline); // just testing that the setter works for both true and false
  }

  @Test
  public void http() {
    HttpConfigurationBuilder b = Components.httpConfiguration().connectTimeout(Duration.ofSeconds(9));
    LDConfig config = new LDConfig.Builder().http(b).build();
    assertEquals(Duration.ofSeconds(9),
        config.http.build(BASIC_CONTEXT).getConnectTimeout());
  }

  @Test
  public void logging() {
    LoggingConfigurationBuilder b = Components.logging().logDataSourceOutageAsErrorAfter(Duration.ofSeconds(9));
    LDConfig config = new LDConfig.Builder().logging(b).build();
    assertEquals(Duration.ofSeconds(9),
        config.logging.build(BASIC_CONTEXT).getLogDataSourceOutageAsErrorAfter());
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
    HttpConfiguration hc = config.http.build(BASIC_CONTEXT);
    HttpConfiguration defaults = Components.httpConfiguration().build(BASIC_CONTEXT);
    assertEquals(defaults.getConnectTimeout(), hc.getConnectTimeout());
    assertNull(hc.getProxy());
    assertNull(hc.getProxyAuthentication());
    assertEquals(defaults.getSocketTimeout(), hc.getSocketTimeout());
    assertNull(hc.getSslSocketFactory());
    assertNull(hc.getTrustManager());
    assertEquals(ImmutableMap.copyOf(defaults.getDefaultHeaders()), ImmutableMap.copyOf(hc.getDefaultHeaders()));
  }
}
