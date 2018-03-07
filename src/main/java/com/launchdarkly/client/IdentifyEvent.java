package com.launchdarkly.client;

class IdentifyEvent extends Event {

  IdentifyEvent(LDUser user) {
    super("identify", user.getKeyAsString(), user);
  }
  
  IdentifyEvent(long timestamp, String key, LDUser user) {
    super(timestamp, "identify", key, user);
  }
}
