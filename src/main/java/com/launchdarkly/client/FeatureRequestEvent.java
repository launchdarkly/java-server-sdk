package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

class FeatureRequestEvent extends Event {
  Integer variation;
  
  JsonElement value;
  @SerializedName("default")
  JsonElement defaultVal;

  @SerializedName("version")
  Integer version;

  @SerializedName("prereqOf")
  String prereqOf;

  boolean trackEvents;
  Long debugEventsUntilDate;
  
  FeatureRequestEvent(long timestamp, String key, LDUser user, Integer version, Integer variation, JsonElement value,
      JsonElement defaultVal, String prereqOf, boolean trackEvents, Long debugEventsUntilDate) {
    super(timestamp, "feature", key, user);
    this.version = version;
    this.variation = variation;
    this.value = value;
    this.defaultVal = defaultVal;
    this.prereqOf = prereqOf;
    this.trackEvents = trackEvents;
    this.debugEventsUntilDate = debugEventsUntilDate;
  }
}
