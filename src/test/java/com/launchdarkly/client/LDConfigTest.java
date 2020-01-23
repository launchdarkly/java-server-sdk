package com.launchdarkly.client;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

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
    assertNull(config.proxy);
    assertNull(config.proxyAuthenticator);
  }

  @Test
  public void testOnlyProxyHostConfiguredIsNull() {
    LDConfig config = new LDConfig.Builder().proxyHost("bla").build();
    assertNull(config.proxy);
  }

  @Test
  public void testOnlyProxyPortConfiguredHasPortAndDefaultHost() {
    LDConfig config = new LDConfig.Builder().proxyPort(1234).build();
    assertEquals(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 1234)), config.proxy);
  }
  @Test
  public void testProxy() {
    LDConfig config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .build();
    assertEquals(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost2", 4444)), config.proxy);
  }

  @Test
  public void testProxyAuth() {
    LDConfig config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .proxyUsername("proxyUser")
        .proxyPassword("proxyPassword")
        .build();
    assertNotNull(config.proxy);
    assertNotNull(config.proxyAuthenticator);
  }

  @Test
  public void testProxyAuthPartialConfig() {
    LDConfig config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .proxyUsername("proxyUser")
        .build();
    assertNotNull(config.proxy);
    assertNull(config.proxyAuthenticator);

    config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .proxyPassword("proxyPassword")
        .build();
    assertNotNull(config.proxy);
    assertNull(config.proxyAuthenticator);
  }

  @Test
  public void testMinimumPollingIntervalIsEnforcedProperly(){
    LDConfig config = new LDConfig.Builder().pollingInterval(Duration.ofSeconds(10)).build();
    assertEquals(Duration.ofSeconds(30), config.pollingInterval);
  }

  @Test
  public void testPollingIntervalIsEnforcedProperly(){
    LDConfig config = new LDConfig.Builder().pollingInterval(Duration.ofMillis(30001)).build();
    assertEquals(Duration.ofMillis(30001), config.pollingInterval);
  }
  
  @Test
  public void testSendEventsDefaultsToTrue() {
    LDConfig config = new LDConfig.Builder().build();
    assertEquals(true, config.sendEvents);
  }
  
  @Test
  public void testSendEventsCanBeSetToFalse() {
    LDConfig config = new LDConfig.Builder().sendEvents(false).build();
    assertEquals(false, config.sendEvents);
  }

  @Test
  public void testDefaultDiagnosticRecordingInterval() {
    LDConfig config = new LDConfig.Builder().build();
    assertEquals(Duration.ofMillis(900_000), config.diagnosticRecordingInterval);
  }

  @Test
  public void testDiagnosticRecordingInterval() {
    LDConfig config = new LDConfig.Builder().diagnosticRecordingInterval(Duration.ofMillis(120_000)).build();
    assertEquals(Duration.ofMillis(120_000), config.diagnosticRecordingInterval);
  }

  @Test
  public void testMinimumDiagnosticRecordingIntervalEnforced() {
    LDConfig config = new LDConfig.Builder().diagnosticRecordingInterval(Duration.ofMillis(10)).build();
    assertEquals(Duration.ofMillis(60_000), config.diagnosticRecordingInterval);
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