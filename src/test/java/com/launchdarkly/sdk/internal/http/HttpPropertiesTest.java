package com.launchdarkly.sdk.internal.http;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import okhttp3.OkHttpClient;

@SuppressWarnings("javadoc")
public class HttpPropertiesTest {
  @Test
  public void testConnectTimeout() {
    HttpProperties hp = new HttpProperties(
        100000,
        null, null, null, null, 0, null, null);
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
        0, null, null, null, null,
        100000,
        null, null);
    OkHttpClient httpClient = hp.toHttpClientBuilder().build();
    try {
      assertEquals(100000, httpClient.readTimeoutMillis());
    } finally {
      HttpProperties.shutdownHttpClient(httpClient);
    }
  }
}
