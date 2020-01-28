package com.launchdarkly.client;

import com.google.common.collect.ImmutableSet;

import java.net.URI;
import java.util.Set;

// Used internally to encapsulate the various config/builder properties for events.
final class EventsConfiguration {
  final boolean allAttributesPrivate;
  final int capacity;
  final URI eventsUri;
  final int flushIntervalSeconds;
  final boolean inlineUsersInEvents;
  final ImmutableSet<String> privateAttrNames;
  final int samplingInterval;
  final int userKeysCapacity;
  final int userKeysFlushIntervalSeconds;
  
  EventsConfiguration(boolean allAttributesPrivate, int capacity, URI eventsUri, int flushIntervalSeconds,
      boolean inlineUsersInEvents, Set<String> privateAttrNames, int samplingInterval,
      int userKeysCapacity, int userKeysFlushIntervalSeconds) {
    super();
    this.allAttributesPrivate = allAttributesPrivate;
    this.capacity = capacity;
    this.eventsUri = eventsUri == null ? LDConfig.DEFAULT_EVENTS_URI : eventsUri;
    this.flushIntervalSeconds = flushIntervalSeconds;
    this.inlineUsersInEvents = inlineUsersInEvents;
    this.privateAttrNames = privateAttrNames == null ? ImmutableSet.<String>of() : ImmutableSet.copyOf(privateAttrNames);
    this.samplingInterval = samplingInterval;
    this.userKeysCapacity = userKeysCapacity;
    this.userKeysFlushIntervalSeconds = userKeysFlushIntervalSeconds;
  }
}