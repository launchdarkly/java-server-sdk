package com.launchdarkly.sdk.internal.events;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.sdk.AttributeRef;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Used internally to encapsulate the various config/builder properties for events.
public final class EventsConfiguration {
  final boolean allAttributesPrivate;
  final int capacity;
  final EventContextDeduplicator contextDeduplicator;
  final long diagnosticRecordingIntervalMillis;
  final DiagnosticStore diagnosticStore;
  final EventSender eventSender;
  final URI eventsUri;
  final long flushIntervalMillis;
  final List<AttributeRef> privateAttributes;
  
  public EventsConfiguration(
      boolean allAttributesPrivate,
      int capacity,
      EventContextDeduplicator contextDeduplicator,
      long diagnosticRecordingIntervalMillis,
      DiagnosticStore diagnosticStore,
      EventSender eventSender,
      URI eventsUri,
      long flushIntervalMillis,
      List<AttributeRef> privateAttributes
      ) {
    super();
    this.allAttributesPrivate = allAttributesPrivate;
    this.capacity = capacity;
    this.contextDeduplicator = contextDeduplicator;
    this.diagnosticRecordingIntervalMillis = diagnosticRecordingIntervalMillis;
    this.diagnosticStore = diagnosticStore;
    this.eventSender = eventSender;
    this.eventsUri = eventsUri;
    this.flushIntervalMillis = flushIntervalMillis;
    this.privateAttributes = privateAttributes == null ? Collections.emptyList() : new ArrayList<>(privateAttributes);
  }
}