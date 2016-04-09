package com.launchdarkly.client;

import org.apache.http.client.methods.HttpPost;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LDConfigTest {
  @Test
  public void testConnectTimeoutSpecifiedInSeconds() {
    LDConfig config = new LDConfig.Builder().connectTimeout(3).build();

    assertEquals(3000, config.connectTimeout);
  }

  @Test
  public void testConnectTimeoutSpecifiedInMilliseconds() {
    LDConfig config = new LDConfig.Builder().connectTimeoutMillis(3000).build();

    assertEquals(3000, config.connectTimeout);
  }
  @Test
  public void testSocketTimeoutSpecifiedInSeconds() {
    LDConfig config = new LDConfig.Builder().socketTimeout(3).build();

    assertEquals(3000, config.socketTimeout);
  }

  @Test
  public void testSocketTimeoutSpecifiedInMilliseconds() {
    LDConfig config = new LDConfig.Builder().socketTimeoutMillis(3000).build();

    assertEquals(3000, config.socketTimeout);
  }

  @Test
  public void testNoProxyConfigured() {
    LDConfig config = new LDConfig.Builder().build();

    assertNull(config.proxyHost);
  }

  @Test
  public void testOnlyProxyPortConfiguredHasPort() {
    LDConfig config = new LDConfig.Builder().proxyPort(1234).build();

    assertEquals(1234, config.proxyHost.getPort());
  }

  @Test
  public void testOnlyProxyPortConfiguredHasDefaultHost() {
    LDConfig config = new LDConfig.Builder().proxyPort(1234).build();

    assertEquals("localhost", config.proxyHost.getHostName());
  }

  @Test
  public void testOnlyProxyPortConfiguredHasDefaultScheme() {
    LDConfig config = new LDConfig.Builder().proxyPort(1234).build();

    assertEquals("https", config.proxyHost.getSchemeName());
  }

  @Test
  public void testOnlyProxyHostConfiguredHasDefaultPort() {
    LDConfig config = new LDConfig.Builder().proxyHost("myproxy.example.com").build();

    assertEquals(-1, config.proxyHost.getPort());
  }

  @Test
  public void testProxyHostConfiguredHasHost() {
    LDConfig config = new LDConfig.Builder().proxyHost("myproxy.example.com").build();

    assertEquals("myproxy.example.com", config.proxyHost.getHostName());
  }

  @Test
  public void testProxySchemeConfiguredHasScheme() {
    LDConfig config = new LDConfig.Builder().proxyScheme("http").build();

    assertEquals("http", config.proxyHost.getSchemeName());
  }

  @Test
  public void testDefaultEventsUriIsConstructedProperly(){
    LDConfig config = new LDConfig.Builder().build();

    HttpPost post = config.postEventsRequest("dummy-api-key", "/bulk");
    assertEquals("https://events.launchdarkly.com/bulk", post.getURI().toString());
  }

  @Test
  public void testCustomEventsUriIsConstructedProperly(){
    LDConfig config = new LDConfig.Builder().eventsURI(URI.create("http://localhost:3000/api/events")).build();

    HttpPost post = config.postEventsRequest("dummy-api-key", "/bulk");
    assertEquals("http://localhost:3000/api/events/bulk", post.getURI().toString());
  }

  @Test
  public void testMinimumPollingIntervalIsEnforcedProperly(){
    LDConfig config = new LDConfig.Builder().pollingIntervalMillis(10L).build();
    assertEquals(1000L, config.pollingIntervalMillis);
  }

  @Test
  public void testPollingIntervalIsEnforcedProperly(){
    LDConfig config = new LDConfig.Builder().pollingIntervalMillis(10001L).build();
    assertEquals(10001L, config.pollingIntervalMillis);
  }
}