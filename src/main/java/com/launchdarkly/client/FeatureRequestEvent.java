package com.launchdarkly.client;

import com.google.gson.JsonElement;

class FeatureRequestEvent extends Event {
  final String key;
  final Integer variation;
  final JsonElement value;
  final JsonElement defaultVal;
  final Integer version;
  final String prereqOf;
  final boolean trackEvents;
  final Long debugEventsUntilDate;
  
  FeatureRequestEvent(long timestamp, String key, LDUser user, Integer version, Integer variation, JsonElement value,
      JsonElement defaultVal, String prereqOf, boolean trackEvents, Long debugEventsUntilDate) {
    super(timestamp, user);
    this.key = key;
    this.version = version;
    this.variation = variation;
    this.value = value;
    this.defaultVal = defaultVal;
    this.prereqOf = prereqOf;
    this.trackEvents = trackEvents;
    this.debugEventsUntilDate = debugEventsUntilDate;
  }
}
