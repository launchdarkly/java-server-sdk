package com.launchdarkly.client;

class Event {
  long creationDate;
  LDUser user;

  Event(long creationDate, LDUser user) {
    this.creationDate = creationDate;
    this.user = user;
  }
}