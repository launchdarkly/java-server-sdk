package com.launchdarkly.sdk.server.integrations;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.sdk.server.Components.streamingDataSource;
import static com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder.DEFAULT_INITIAL_RECONNECT_DELAY;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class StreamingDataSourceBuilderTest {
  @Test
  public void initialReconnectDelay() {
    assertEquals(DEFAULT_INITIAL_RECONNECT_DELAY, streamingDataSource().initialReconnectDelay);
    
    assertEquals(Duration.ofMillis(222),
        streamingDataSource().initialReconnectDelay(Duration.ofMillis(222)).initialReconnectDelay);

    assertEquals(DEFAULT_INITIAL_RECONNECT_DELAY,
        streamingDataSource().initialReconnectDelay(Duration.ofMillis(222)).initialReconnectDelay(null).initialReconnectDelay);
  }

  @Test
  public void testPayloadFilter() {
    assertEquals(null, streamingDataSource().payloadFilter);

    assertEquals("aFilter",
      streamingDataSource().payloadFilter("aFilter").payloadFilter);

    assertEquals(null,
      streamingDataSource().payloadFilter("aFilter").payloadFilter(null).payloadFilter);
  }
}
