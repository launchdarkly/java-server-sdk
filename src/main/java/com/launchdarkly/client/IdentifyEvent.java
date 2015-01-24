package com.launchdarkly.client;

import com.google.gson.JsonObject;

class IdentifyEvent extends Event {

  IdentifyEvent(LDUser user) {
    super("identify", user.getKey(), user);
  }
}
