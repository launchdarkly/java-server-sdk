package com.launchdarkly.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
}