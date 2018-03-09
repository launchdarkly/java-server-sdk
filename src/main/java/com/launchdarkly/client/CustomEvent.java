package com.launchdarkly.client;

import com.google.gson.JsonElement;

class CustomEvent extends Event {
  final String key;
  final JsonElement data;

  CustomEvent(long timestamp, String key, LDUser user, JsonElement data) {
    super(timestamp, user);
    this.key = key;
    this.data = data;
  }
}
