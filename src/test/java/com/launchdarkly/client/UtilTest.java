package com.launchdarkly.client;

import org.junit.Test;

import static com.launchdarkly.client.Util.configureHttpClientBuilder;
import static com.launchdarkly.client.Util.shutdownHttpClient;
import static org.junit.Assert.assertEquals;

import okhttp3.OkHttpClient;

@SuppressWarnings("javadoc")
public class UtilTest {
  @Test
  public void testConnectTimeoutSpecifiedInSeconds() {
    LDConfig config = new LDConfig.Builder().connectTimeout(3).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.connectTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }

  @Test
  public void testConnectTimeoutSpecifiedInMilliseconds() {
    LDConfig config = new LDConfig.Builder().connectTimeoutMillis(3000).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.connectTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }
  
  @Test
  public void testSocketTimeoutSpecifiedInSeconds() {
    LDConfig config = new LDConfig.Builder().socketTimeout(3).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.readTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }

  @Test
  public void testSocketTimeoutSpecifiedInMilliseconds() {
    LDConfig config = new LDConfig.Builder().socketTimeoutMillis(3000).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.readTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }
}
