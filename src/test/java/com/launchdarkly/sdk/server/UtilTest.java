package com.launchdarkly.sdk.server;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.sdk.server.Util.configureHttpClientBuilder;
import static com.launchdarkly.sdk.server.Util.shutdownHttpClient;
import static org.junit.Assert.assertEquals;

import okhttp3.OkHttpClient;

@SuppressWarnings("javadoc")
public class UtilTest {
  @Test
  public void testConnectTimeout() {
    LDConfig config = new LDConfig.Builder().http(Components.httpConfiguration().connectTimeout(Duration.ofSeconds(3))).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config.httpConfig, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.connectTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }
  
  @Test
  public void testSocketTimeout() {
    LDConfig config = new LDConfig.Builder().http(Components.httpConfiguration().socketTimeout(Duration.ofSeconds(3))).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config.httpConfig, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.readTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }
}
