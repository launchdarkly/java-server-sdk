package com.launchdarkly.client;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

class Event {
  String creationDate;
  String key;
  String kind;
  LDUser user;
  private static final TimeZone tz = TimeZone.getTimeZone("UTC");
  private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

  static {
    df.setTimeZone(tz);
  }

  Event(String kind, String key, LDUser user) {
    this.creationDate = df.format(new Date());
    this.key = key;
    this.kind = kind;
    this.user = user;
  }
}