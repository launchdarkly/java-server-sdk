package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.subsystems.EventSender;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

// Used internally to encapsulate the various config/builder properties for events.
final class EventsConfiguration {
  final boolean allAttributesPrivate;
  final int capacity;
  final EventSender eventSender;
  final URI eventsUri;
  final Duration flushInterval;
  final ImmutableSet<UserAttribute> privateAttributes;
  final int userKeysCapacity;
  final Duration userKeysFlushInterval;
  final Duration diagnosticRecordingInterval;
  
  EventsConfiguration(
      boolean allAttributesPrivate,
      int capacity,
      EventSender eventSender,
      URI eventsUri,
      Duration flushInterval,
      Set<UserAttribute> privateAttributes,
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
    this.privateAttributes = privateAttributes == null ? ImmutableSet.of() : ImmutableSet.copyOf(privateAttributes);
    this.userKeysCapacity = userKeysCapacity;
    this.userKeysFlushInterval = userKeysFlushInterval;
    this.diagnosticRecordingInterval = diagnosticRecordingInterval;
  }
}