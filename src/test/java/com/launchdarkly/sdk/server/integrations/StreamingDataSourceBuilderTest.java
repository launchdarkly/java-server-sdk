package com.launchdarkly.sdk.server.integrations;

import org.junit.Test;

import java.net.URI;
import java.time.Duration;

import static com.launchdarkly.sdk.server.Components.streamingDataSource;
import static com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder.DEFAULT_INITIAL_RECONNECT_DELAY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class StreamingDataSourceBuilderTest {
  @Test
  public void baseURI() {
    assertNull(streamingDataSource().baseURI);
    
    assertEquals(URI.create("x"), streamingDataSource().baseURI(URI.create("x")).baseURI);
    
    assertNull(streamingDataSource().baseURI(URI.create("X")).baseURI(null).baseURI);
  }
  
  @Test
  public void initialReconnectDelay() {
    assertEquals(DEFAULT_INITIAL_RECONNECT_DELAY, streamingDataSource().initialReconnectDelay);
    
    assertEquals(Duration.ofMillis(222),
        streamingDataSource().initialReconnectDelay(Duration.ofMillis(222)).initialReconnectDelay);

    assertEquals(DEFAULT_INITIAL_RECONNECT_DELAY,
        streamingDataSource().initialReconnectDelay(Duration.ofMillis(222)).initialReconnectDelay(null).initialReconnectDelay);
  }
  
  @Test
  public void pollingBaseURI() {
    // The pollingBaseURI option is now ignored, so this test just verifies that changing it does *not*
    // change the stream's regular baseURI property.
    StreamingDataSourceBuilder b = streamingDataSource();
    b.pollingBaseURI(URI.create("x"));
    assertNull(b.baseURI);
  }
}
