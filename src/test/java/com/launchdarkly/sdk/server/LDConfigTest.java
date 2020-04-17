package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDConfigTest {
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