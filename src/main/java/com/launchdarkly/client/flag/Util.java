package com.launchdarkly.client.flag;


import com.google.gson.JsonPrimitive;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

class Util {
  protected static DateTime jsonPrimitiveToDateTime(JsonPrimitive maybeDate) {
    if (maybeDate.isNumber()) {
      long millis = maybeDate.getAsLong();
      return new DateTime(millis);
    } else if (maybeDate.isString()) {
      try {
        return new DateTime(maybeDate.getAsString(), DateTimeZone.UTC);
      } catch (Throwable t) {
        return null;
      }
    } else {
      return null;
    }
  }
}
