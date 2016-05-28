package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

public class FeatureRequestEvent extends Event {
  JsonElement value;
  @SerializedName("default")
  JsonElement defaultVal;

  public FeatureRequestEvent(String key, LDUser user, JsonElement value, JsonElement defaultVal) {
    super("feature", key, user);
    this.value = value;
    this.defaultVal = defaultVal;
  }
}
