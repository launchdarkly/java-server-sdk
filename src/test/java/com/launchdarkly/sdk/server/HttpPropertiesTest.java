package com.launchdarkly.sdk.server;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

import okhttp3.OkHttpClient;

@SuppressWarnings("javadoc")
public class HttpPropertiesTest {
  @Test
  public void testConnectTimeout() {
    HttpProperties hp = new HttpProperties(
        Duration.ofSeconds(100),
        null, null, null, null, null, null, null);
    OkHttpClient httpClient = hp.toHttpClientBuilder().build();
    try {
      assertEquals(100000, httpClient.connectTimeoutMillis());
    } finally {
      HttpProperties.shutdownHttpClient(httpClient);
    }
  }
  
  @Test
  public void testSocketTimeout() {
    HttpProperties hp = new HttpProperties(
        null, null, null, null, null,
        Duration.ofSeconds(100),
        null, null);
    OkHttpClient httpClient = hp.toHttpClientBuilder().build();
    try {
      assertEquals(100000, httpClient.readTimeoutMillis());
    } finally {
      HttpProperties.shutdownHttpClient(httpClient);
    }
  }
}
