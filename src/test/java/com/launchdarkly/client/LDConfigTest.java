package com.launchdarkly.client;

import com.launchdarkly.client.integrations.HttpConfigurationBuilderTest;
import com.launchdarkly.client.interfaces.HttpConfiguration;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;

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
    assertEquals(defaults.getConnectTimeoutMillis(), hc.getConnectTimeoutMillis());
    assertNull(hc.getProxy());
    assertNull(hc.getProxyAuthentication());
    assertEquals(defaults.getSocketTimeoutMillis(), hc.getSocketTimeoutMillis());
    assertNull(hc.getSslSocketFactory());
    assertNull(hc.getTrustManager());
    assertNull(hc.getWrapperIdentifier());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpConnectTimeout() {
    LDConfig config = new LDConfig.Builder().connectTimeoutMillis(999).build();
    assertEquals(999, config.httpConfig.getConnectTimeoutMillis());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpConnectTimeoutSeconds() {
    LDConfig config = new LDConfig.Builder().connectTimeout(999).build();
    assertEquals(999000, config.httpConfig.getConnectTimeoutMillis());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpSocketTimeout() {
    LDConfig config = new LDConfig.Builder().socketTimeoutMillis(999).build();
    assertEquals(999, config.httpConfig.getSocketTimeoutMillis());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedHttpSocketTimeoutSeconds() {
    LDConfig config = new LDConfig.Builder().socketTimeout(999).build();
    assertEquals(999000, config.httpConfig.getSocketTimeoutMillis());
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