package com.launchdarkly.client;

import com.google.gson.annotations.SerializedName;

class FeatureRequestEvent<E> extends Event {
  E value;
  @SerializedName("default")
  E defaultVal;

  FeatureRequestEvent(String key, LDUser user, E value, E defaultVal) {
    super("feature", key, user);
    this.value = value;
    this.defaultVal = defaultVal;
  }
}
