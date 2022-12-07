package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.EventSender;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.sdk.server.Components.sendEvents;
import static com.launchdarkly.sdk.server.integrations.EventProcessorBuilder.DEFAULT_CAPACITY;
import static com.launchdarkly.sdk.server.integrations.EventProcessorBuilder.DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL;
import static com.launchdarkly.sdk.server.integrations.EventProcessorBuilder.DEFAULT_FLUSH_INTERVAL;
import static com.launchdarkly.sdk.server.integrations.EventProcessorBuilder.DEFAULT_USER_KEYS_CAPACITY;
import static com.launchdarkly.sdk.server.integrations.EventProcessorBuilder.DEFAULT_USER_KEYS_FLUSH_INTERVAL;
import static com.launchdarkly.sdk.server.integrations.EventProcessorBuilder.MIN_DIAGNOSTIC_RECORDING_INTERVAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class EventProcessorBuilderTest {
  @Test
  public void allAttributesPrivate() {
    assertEquals(false, sendEvents().allAttributesPrivate);

    assertEquals(true, sendEvents().allAttributesPrivate(true).allAttributesPrivate);

    assertEquals(false, sendEvents()
        .allAttributesPrivate(true)
        .allAttributesPrivate(false)
        .allAttributesPrivate);
  }
  
  @Test
  public void capacity() {
    assertEquals(DEFAULT_CAPACITY, sendEvents().capacity);

    assertEquals(200, sendEvents().capacity(200).capacity);
  }
  
  @Test
  public void diagnosticRecordingInterval() {
    EventProcessorBuilder builder1 = sendEvents();
    assertEquals(DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL, builder1.diagnosticRecordingInterval);

    EventProcessorBuilder builder2 = sendEvents().diagnosticRecordingInterval(Duration.ofSeconds(120));
    assertEquals(Duration.ofSeconds(120), builder2.diagnosticRecordingInterval);

    EventProcessorBuilder builder3 = sendEvents()
        .diagnosticRecordingInterval(Duration.ofSeconds(120))
        .diagnosticRecordingInterval(null); // null sets it back to the default
    assertEquals(DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL, builder3.diagnosticRecordingInterval);

    EventProcessorBuilder builder4 = sendEvents().diagnosticRecordingInterval(Duration.ofSeconds(10));
    assertEquals(MIN_DIAGNOSTIC_RECORDING_INTERVAL, builder4.diagnosticRecordingInterval);

  }
  
  @Test
  public void eventSender() {
    assertNull(sendEvents().eventSenderConfigurer);
    
    ComponentConfigurer<EventSender> f = (ctx) -> null;
    assertSame(f, sendEvents().eventSender(f).eventSenderConfigurer);
    
    assertNull(sendEvents().eventSender(f).eventSender(null).eventSenderConfigurer);
  }
  
  @Test
  public void flushInterval() {
    EventProcessorBuilder builder1 = Components.sendEvents();
    assertEquals(DEFAULT_FLUSH_INTERVAL, builder1.flushInterval);
    
    EventProcessorBuilder builder2 = Components.sendEvents().flushInterval(Duration.ofSeconds(120));
    assertEquals(Duration.ofSeconds(120), builder2.flushInterval);
    
    EventProcessorBuilder builder3 = Components.sendEvents()
        .flushInterval(Duration.ofSeconds(120))
        .flushInterval(null); // null sets it back to the default
    assertEquals(DEFAULT_FLUSH_INTERVAL, builder3.flushInterval);
  }
    
  @Test
  public void privateAttributes() {
    assertNull(sendEvents().privateAttributes);
    
    assertEquals(ImmutableSet.of(AttributeRef.fromLiteral("email"), AttributeRef.fromPath("/address/street")),
        sendEvents().privateAttributes("email", "/address/street").privateAttributes);
  }
  
  @Test
  public void userKeysCapacity() {
    assertEquals(DEFAULT_USER_KEYS_CAPACITY, sendEvents().userKeysCapacity);
    
    assertEquals(44, sendEvents().userKeysCapacity(44).userKeysCapacity);
  }
  
  @Test
  public void usrKeysFlushInterval() {
    EventProcessorBuilder builder1 = Components.sendEvents();
    assertEquals(DEFAULT_USER_KEYS_FLUSH_INTERVAL, builder1.userKeysFlushInterval);
    
    EventProcessorBuilder builder2 = Components.sendEvents().userKeysFlushInterval(Duration.ofSeconds(120));
    assertEquals(Duration.ofSeconds(120), builder2.userKeysFlushInterval);
    
    EventProcessorBuilder builder3 = Components.sendEvents()
        .userKeysFlushInterval(Duration.ofSeconds(120))
        .userKeysFlushInterval(null); // null sets it back to the default
    assertEquals(DEFAULT_USER_KEYS_FLUSH_INTERVAL, builder3.userKeysFlushInterval);
  }
}
