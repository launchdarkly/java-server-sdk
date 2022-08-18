package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.server.subsystems.EventSender;

import java.net.URI;
import java.time.Duration;

// Used internally to encapsulate the various config/builder properties for events.
final class EventsConfiguration {
  final boolean allAttributesPrivate;
  final int capacity;
  final EventSender eventSender;
  final URI eventsUri;
  final Duration flushInterval;
  final ImmutableList<AttributeRef> privateAttributes;
  final int userKeysCapacity;
  final Duration userKeysFlushInterval;
  final Duration diagnosticRecordingInterval;
  
  EventsConfiguration(
      boolean allAttributesPrivate,
      int capacity,
      EventSender eventSender,
      URI eventsUri,
      Duration flushInterval,
      Iterable<AttributeRef> privateAttributes,
      int userKeysCapacity,
      Duration userKeysFlushInterval,
      Duration diagnosticRecordingInterval
      ) {
    super();
    this.allAttributesPrivate = allAttributesPrivate;
    this.capacity = capacity;
    this.eventSender = eventSender;
    this.eventsUri = eventsUri;
    this.flushInterval = flushInterval;
    this.privateAttributes = privateAttributes == null ? ImmutableList.of() : ImmutableList.copyOf(privateAttributes);
    this.userKeysCapacity = userKeysCapacity;
    this.userKeysFlushInterval = userKeysFlushInterval;
    this.diagnosticRecordingInterval = diagnosticRecordingInterval;
  }
}