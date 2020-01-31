package com.launchdarkly.client;

import com.google.common.collect.ImmutableSet;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

// Used internally to encapsulate the various config/builder properties for events.
final class EventsConfiguration {
  final boolean allAttributesPrivate;
  final int capacity;
  final URI eventsUri;
  final Duration flushInterval;
  final boolean inlineUsersInEvents;
  final ImmutableSet<String> privateAttrNames;
  final int samplingInterval;
  final int userKeysCapacity;
  final Duration userKeysFlushInterval;
  final Duration diagnosticRecordingInterval;
  
  EventsConfiguration(boolean allAttributesPrivate, int capacity, URI eventsUri, Duration flushInterval,
      boolean inlineUsersInEvents, Set<String> privateAttrNames, int samplingInterval,
      int userKeysCapacity, Duration userKeysFlushInterval, Duration diagnosticRecordingInterval) {
    super();
    this.allAttributesPrivate = allAttributesPrivate;
    this.capacity = capacity;
    this.eventsUri = eventsUri == null ? LDConfig.DEFAULT_EVENTS_URI : eventsUri;
    this.flushInterval = flushInterval;
    this.inlineUsersInEvents = inlineUsersInEvents;
    this.privateAttrNames = privateAttrNames == null ? ImmutableSet.<String>of() : ImmutableSet.copyOf(privateAttrNames);
    this.samplingInterval = samplingInterval;
    this.userKeysCapacity = userKeysCapacity;
    this.userKeysFlushInterval = userKeysFlushInterval;
    this.diagnosticRecordingInterval = diagnosticRecordingInterval;
  }
}