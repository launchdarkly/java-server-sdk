package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

class Util {
  /**
   * Converts either a unix epoch millis number or RFC3339/ISO8601 timestamp as {@link JsonPrimitive} to a {@link DateTime} object.
   * @param maybeDate wraps either a nubmer or a string that may contain a valid timestamp.
   * @return null if input is not a valid format.
   */
  static DateTime jsonPrimitiveToDateTime(JsonPrimitive maybeDate) {
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
