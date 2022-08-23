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
  final EventContextDeduplicator contextDeduplicator;
  final EventSender eventSender;
  final URI eventsUri;
  final Duration flushInterval;
  final ImmutableList<AttributeRef> privateAttributes;
  final Duration diagnosticRecordingInterval;
  
  EventsConfiguration(
      boolean allAttributesPrivate,
      int capacity,
      EventContextDeduplicator contextDeduplicator,
      EventSender eventSender,
      URI eventsUri,
      Duration flushInterval,
      Iterable<AttributeRef> privateAttributes,
      Duration diagnosticRecordingInterval
      ) {
    super();
    this.allAttributesPrivate = allAttributesPrivate;
    this.capacity = capacity;
    this.contextDeduplicator = contextDeduplicator;
    this.eventSender = eventSender;
    this.eventsUri = eventsUri;
    this.flushInterval = flushInterval;
    this.privateAttributes = privateAttributes == null ? ImmutableList.of() : ImmutableList.copyOf(privateAttributes);
    this.diagnosticRecordingInterval = diagnosticRecordingInterval;
  }
}