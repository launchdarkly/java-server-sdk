package com.launchdarkly.client;


class Event {
  long creationDate;
  String key;
  String kind;
  LDUser user;

  Event(String kind, String key, LDUser user) {
    this.creationDate = System.currentTimeMillis();
    this.key = key;
    this.kind = kind;
    this.user = user;
  }

  Event(long creationDate, String kind, String key, LDUser user) {
    this.creationDate = creationDate;
    this.key = key;
    this.kind = kind;
    this.user = user;
  }
}