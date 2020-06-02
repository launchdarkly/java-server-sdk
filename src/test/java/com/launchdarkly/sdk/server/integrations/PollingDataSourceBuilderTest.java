package com.launchdarkly.sdk.server.integrations;

import org.junit.Test;

import java.net.URI;
import java.time.Duration;

import static com.launchdarkly.sdk.server.Components.pollingDataSource;
import static com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class PollingDataSourceBuilderTest {
  @Test
  public void baseURI() {
    assertNull(pollingDataSource().baseURI);
    
    assertEquals(URI.create("x"), pollingDataSource().baseURI(URI.create("x")).baseURI);
    
    assertNull(pollingDataSource().baseURI(URI.create("X")).baseURI(null).baseURI);
  }
  
  @Test
  public void pollInterval() {
    assertEquals(DEFAULT_POLL_INTERVAL, pollingDataSource().pollInterval);
    
    assertEquals(Duration.ofMinutes(7),
        pollingDataSource().pollInterval(Duration.ofMinutes(7)).pollInterval);

    assertEquals(DEFAULT_POLL_INTERVAL,
        pollingDataSource().pollInterval(Duration.ofMinutes(7)).pollInterval(null).pollInterval);

    assertEquals(DEFAULT_POLL_INTERVAL,
        pollingDataSource().pollInterval(Duration.ofMillis(1)).pollInterval);
  }
}
