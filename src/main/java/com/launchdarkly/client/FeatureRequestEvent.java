package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

class FeatureRequestEvent extends Event {
  JsonElement value;
  @SerializedName("default")
  JsonElement defaultVal;

  @SerializedName("version")
  Integer version;

  @SerializedName("prereqOf")
  String prereqOf;

  FeatureRequestEvent(String key, LDUser user, JsonElement value, JsonElement defaultVal, Integer version, String prereqOf) {
    super("feature", key, user);
    this.value = value;
    this.defaultVal = defaultVal;
    this.version = version;
    this.prereqOf = prereqOf;
  }
}
