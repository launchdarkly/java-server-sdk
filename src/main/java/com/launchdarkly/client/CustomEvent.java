package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

class CustomEvent extends Event {
  private final JsonElement data;

  CustomEvent(String key, LDUser user, JsonElement data) {
    super("custom", key, user);
    this.data = data;
  }
}
