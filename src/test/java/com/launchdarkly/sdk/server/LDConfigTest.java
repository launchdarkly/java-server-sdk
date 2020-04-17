package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilderTest;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpConnectTimeout() {
    LDConfig config = new LDConfig.Builder().connectTimeout(Duration.ofMillis(999)).build();
    assertEquals(999, config.httpConfig.getConnectTimeout().toMillis());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpSocketTimeout() {
    LDConfig config = new LDConfig.Builder().socketTimeout(Duration.ofMillis(999)).build();
    assertEquals(999, config.httpConfig.getSocketTimeout().toMillis());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpOnlyProxyHostConfiguredIsNull() {
    LDConfig config = new LDConfig.Builder().proxyHost("bla").build();
    assertNull(config.httpConfig.getProxy());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpOnlyProxyPortConfiguredHasPortAndDefaultHost() {
    LDConfig config = new LDConfig.Builder().proxyPort(1234).build();
    assertEquals(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 1234)), config.httpConfig.getProxy());
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpProxy() {
    LDConfig config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .build();
    assertEquals(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost2", 4444)), config.httpConfig.getProxy());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpProxyAuth() {
    LDConfig config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .proxyUsername("user")
        .proxyPassword("pass")
        .build();
    assertNotNull(config.httpConfig.getProxy());
    assertNotNull(config.httpConfig.getProxyAuthentication());
    assertEquals("Basic dXNlcjpwYXNz", config.httpConfig.getProxyAuthentication().provideAuthorization(null));
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpProxyAuthPartialConfig() {
    LDConfig config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .proxyUsername("proxyUser")
        .build();
    assertNotNull(config.httpConfig.getProxy());
    assertNull(config.httpConfig.getProxyAuthentication());

    config = new LDConfig.Builder()
        .proxyHost("localhost2")
        .proxyPort(4444)
        .proxyPassword("proxyPassword")
        .build();
    assertNotNull(config.httpConfig.getProxy());
    assertNull(config.httpConfig.getProxyAuthentication());
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpSslOptions() {
    SSLSocketFactory sf = new HttpConfigurationBuilderTest.StubSSLSocketFactory();
    X509TrustManager tm = new HttpConfigurationBuilderTest.StubX509TrustManager();
    LDConfig config = new LDConfig.Builder().sslSocketFactory(sf, tm).build();
    assertSame(sf, config.httpConfig.getSslSocketFactory());
    assertSame(tm, config.httpConfig.getTrustManager());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpWrapperNameOnly() {
    LDConfig config = new LDConfig.Builder()
            .wrapperName("Scala")
            .build();
    assertEquals("Scala", config.httpConfig.getWrapperIdentifier());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpWrapperWithVersion() {
    LDConfig config = new LDConfig.Builder()
            .wrapperName("Scala")
            .wrapperVersion("0.1.0")
            .build();
    assertEquals("Scala/0.1.0", config.httpConfig.getWrapperIdentifier());
  }
}