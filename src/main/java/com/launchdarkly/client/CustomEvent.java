package com.launchdarkly.client;

import com.google.gson.JsonElement;

class CustomEvent extends Event {
  final JsonElement data;

  CustomEvent(String key, LDUser user, JsonElement data) {
    super("custom", key, user);
    this.data = data;
  }
  
  CustomEvent(long timestamp, String key, LDUser user, JsonElement data) {
    super(timestamp, "custom", key, user);
    this.data = data;
  }
}
