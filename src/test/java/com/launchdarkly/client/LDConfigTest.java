package com.launchdarkly.client;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDConfigTest {
  @Test
  public void testNoProxyConfigured() {
    LDConfig config = new LDConfig.Builder().build();
    assertNull(config.httpConfig.proxy);
    assertNull(config.httpConfig.proxyAuthenticator);
  }

  @Test
  public void testOnlyProxyHostConfiguredIsNull() {
    LDConfig config = new LDConfig.Builder().proxyHost("bla").build();
    assertNull(config.httpConfig.proxy);
  }

  @Test
  public void testOnlyProxyPortConfiguredHasPortAndDefaultHost() {
    LDConfig config = new LDConfig.Builder().proxyPort(1234).build();
    assertEquals(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 1234)), config.httpConfig.proxy);
  }
  @Test
  public void testProxy() {
    LDConfig config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .build();
    assertEquals(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost2", 4444)), config.httpConfig.proxy);
  }

  @Test
  public void testProxyAuth() {
    LDConfig config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .proxyUsername("proxyUser")
        .proxyPassword("proxyPassword")
        .build();
    assertNotNull(config.httpConfig.proxy);
    assertNotNull(config.httpConfig.proxyAuthenticator);
  }

  @Test
  public void testProxyAuthPartialConfig() {
    LDConfig config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .proxyUsername("proxyUser")
        .build();
    assertNotNull(config.httpConfig.proxy);
    assertNull(config.httpConfig.proxyAuthenticator);

    config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .proxyPassword("proxyPassword")
        .build();
    assertNotNull(config.httpConfig.proxy);
    assertNull(config.httpConfig.proxyAuthenticator);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testMinimumPollingIntervalIsEnforcedProperly(){
    LDConfig config = new LDConfig.Builder().pollingIntervalMillis(10L).build();
    assertEquals(30000L, config.deprecatedPollingIntervalMillis);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testPollingIntervalIsEnforcedProperly(){
    LDConfig config = new LDConfig.Builder().pollingIntervalMillis(30001L).build();
    assertEquals(30001L, config.deprecatedPollingIntervalMillis);
  }
  
  @Test
  public void testSendEventsDefaultsToTrue() {
    LDConfig config = new LDConfig.Builder().build();
    assertEquals(true, config.deprecatedSendEvents);
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void testSendEventsCanBeSetToFalse() {
    LDConfig config = new LDConfig.Builder().sendEvents(false).build();
    assertEquals(false, config.deprecatedSendEvents);
  }

  @Test
  public void testDefaultDiagnosticRecordingInterval() {
    LDConfig config = new LDConfig.Builder().build();
    assertEquals(900_000, config.diagnosticRecordingIntervalMillis);
  }

  @Test
  public void testDiagnosticRecordingInterval() {
    LDConfig config = new LDConfig.Builder().diagnosticRecordingIntervalMillis(120_000).build();
    assertEquals(120_000, config.diagnosticRecordingIntervalMillis);
  }

  @Test
  public void testMinimumDiagnosticRecordingIntervalEnforced() {
    LDConfig config = new LDConfig.Builder().diagnosticRecordingIntervalMillis(10).build();
    assertEquals(60_000, config.diagnosticRecordingIntervalMillis);
  }

  @Test
  public void testDefaultDiagnosticOptOut() {
    LDConfig config = new LDConfig.Builder().build();
    assertFalse(config.diagnosticOptOut);
  }

  @Test
  public void testDiagnosticOptOut() {
    LDConfig config = new LDConfig.Builder().diagnosticOptOut(true).build();
    assertTrue(config.diagnosticOptOut);
  }

  @Test
  public void testWrapperNotConfigured() {
    LDConfig config = new LDConfig.Builder().build();
    assertNull(config.wrapperName);
    assertNull(config.wrapperVersion);
  }

  @Test public void testWrapperConfigured() {
    LDConfig config = new LDConfig.Builder()
            .wrapperName("Scala")
            .wrapperVersion("0.1.0")
            .build();
    assertEquals("Scala", config.wrapperName);
    assertEquals("0.1.0", config.wrapperVersion);
  }
}