package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

class FeatureRequestEvent extends Event {
  JsonElement value;
  @SerializedName("default")
  JsonElement defaultVal;

  FeatureRequestEvent(String key, LDUser user, JsonElement value, JsonElement defaultVal) {
    super("feature", key, user);
    this.value = value;
    this.defaultVal = defaultVal;
  }
}
