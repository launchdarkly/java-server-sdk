package com.launchdarkly.sdk.server.integrations;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.sdk.server.Components.pollingDataSource;
import static com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class PollingDataSourceBuilderTest {
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

  @Test
  public void testPayloadFilter() {
    assertEquals(null, pollingDataSource().payloadFilter);

    assertEquals("aFilter",
        pollingDataSource().payloadFilter("aFilter").payloadFilter);

    assertEquals(null,
        pollingDataSource().payloadFilter("aFilter").payloadFilter(null).payloadFilter);
  }
}
