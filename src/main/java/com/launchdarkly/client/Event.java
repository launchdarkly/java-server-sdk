package com.launchdarkly.client;

class Event {
  final long creationDate;
  final LDUser user;

  Event(long creationDate, LDUser user) {
    this.creationDate = creationDate;
    this.user = user;
  }
}