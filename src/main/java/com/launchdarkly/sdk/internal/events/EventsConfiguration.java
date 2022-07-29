package com.launchdarkly.sdk.internal.events;

import com.launchdarkly.sdk.AttributeRef;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Internal representation of the configuration properties for {@link DefaultEventProcessor}.
 * This class is not exposed in the public SDK API.
 */
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
  
  /**
   * Creates an instance.
   * 
   * @param allAttributesPrivate true if all attributes are private
   * @param capacity event buffer capacity (if zero or negative, a value of 1 is used to prevent errors)
   * @param contextDeduplicator optional EventContextDeduplicator; null for client-side SDK
   * @param diagnosticRecordingIntervalMillis diagnostic recording interval
   * @param diagnosticStore optional DiagnosticStore; null if diagnostics are disabled
   * @param eventSender event delivery component; must not be null
   * @param eventsUri events base URI
   * @param flushIntervalMillis event flush interval
   * @param privateAttributes list of private attribute references; may be null
   */
  public EventsConfiguration(
      boolean allAttributesPrivate,
      int capacity,
      EventContextDeduplicator contextDeduplicator,
      long diagnosticRecordingIntervalMillis,
      DiagnosticStore diagnosticStore,
      EventSender eventSender,
      URI eventsUri,
      long flushIntervalMillis,
      Collection<AttributeRef> privateAttributes
      ) {
    super();
    this.allAttributesPrivate = allAttributesPrivate;
    this.capacity = capacity >= 0 ? capacity : 1;
    this.contextDeduplicator = contextDeduplicator;
    this.diagnosticRecordingIntervalMillis = diagnosticRecordingIntervalMillis;
    this.diagnosticStore = diagnosticStore;
    this.eventSender = eventSender;
    this.eventsUri = eventsUri;
    this.flushIntervalMillis = flushIntervalMillis;
    this.privateAttributes = privateAttributes == null ? Collections.emptyList() : new ArrayList<>(privateAttributes);
  }
}