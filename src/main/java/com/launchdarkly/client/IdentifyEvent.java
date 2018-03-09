package com.launchdarkly.client;

class IdentifyEvent extends Event {

  IdentifyEvent(long timestamp, LDUser user) {
    super(timestamp, user);
  }
}
