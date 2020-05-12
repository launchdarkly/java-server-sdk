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
  
  @Test
  public void describeDuration() {
    assertEquals("15 milliseconds", Util.describeDuration(Duration.ofMillis(15)));
    assertEquals("1500 milliseconds", Util.describeDuration(Duration.ofMillis(1500)));
    assertEquals("1 second", Util.describeDuration(Duration.ofMillis(1000)));
    assertEquals("2 seconds", Util.describeDuration(Duration.ofMillis(2000)));
    assertEquals("70 seconds", Util.describeDuration(Duration.ofMillis(70000)));
    assertEquals("1 minute", Util.describeDuration(Duration.ofMillis(60000)));
    assertEquals("2 minutes", Util.describeDuration(Duration.ofMillis(120000)));
  }
}
