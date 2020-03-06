package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.LDConfig;

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
    assertNull(config.httpConfig.wrapperName);
    assertNull(config.httpConfig.wrapperVersion);
  }

  @Test public void testWrapperConfigured() {
    LDConfig config = new LDConfig.Builder()
            .wrapperName("Scala")
            .wrapperVersion("0.1.0")
            .build();
    assertEquals("Scala", config.httpConfig.wrapperName);
    assertEquals("0.1.0", config.httpConfig.wrapperVersion);
  }
}