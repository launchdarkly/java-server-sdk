package com.launchdarkly.client;

import com.google.gson.JsonObject;

class CustomEvent extends Event {
  private final JsonObject data;

  CustomEvent(String key, LDUser user, JsonObject data) {
    super("custom", key, user);
    this.data = data;
  }
}
