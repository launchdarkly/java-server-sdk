package com.launchdarkly.client;


class Event {
  Long creationDate;
  String key;
  String kind;
  LDUser user;

  Event(String kind, String key, LDUser user) {
    this.creationDate = System.currentTimeMillis();
    this.key = key;
    this.kind = kind;
    this.user = user;
  }
}