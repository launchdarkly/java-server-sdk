package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.AttributeRef;

import java.net.URI;
import java.time.Duration;

// Used internally to encapsulate the various config/builder properties for events.
final class EventsConfiguration {
  final boolean allAttributesPrivate;
  final int capacity;
  final EventContextDeduplicator contextDeduplicator;
  final Duration diagnosticRecordingInterval;
  final DiagnosticStore diagnosticStore;
  final EventSender eventSender;
  final URI eventsUri;
  final Duration flushInterval;
  final ImmutableList<AttributeRef> privateAttributes;
  
  EventsConfiguration(
      boolean allAttributesPrivate,
      int capacity,
      EventContextDeduplicator contextDeduplicator,
      Duration diagnosticRecordingInterval,
      DiagnosticStore diagnosticStore,
      EventSender eventSender,
      URI eventsUri,
      Duration flushInterval,
      Iterable<AttributeRef> privateAttributes
      ) {
    super();
    this.allAttributesPrivate = allAttributesPrivate;
    this.capacity = capacity;
    this.contextDeduplicator = contextDeduplicator;
    this.diagnosticRecordingInterval = diagnosticRecordingInterval;
    this.diagnosticStore = diagnosticStore;
    this.eventSender = eventSender;
    this.eventsUri = eventsUri;
    this.flushInterval = flushInterval;
    this.privateAttributes = privateAttributes == null ? ImmutableList.of() : ImmutableList.copyOf(privateAttributes);
  }
}