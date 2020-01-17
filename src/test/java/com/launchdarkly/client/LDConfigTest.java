package com.launchdarkly.client;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
}