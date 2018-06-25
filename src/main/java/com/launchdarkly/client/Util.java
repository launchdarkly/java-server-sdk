package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import okhttp3.Request;

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
  
  static Request.Builder getRequestBuilder(String sdkKey) {
    return new Request.Builder()
        .addHeader("Authorization", sdkKey)
        .addHeader("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);
  }
  
  /**
   * Tests whether an HTTP error status represents a condition that might resolve on its own if we retry.
   * @param statusCode
   * @return true if retrying makes sense; false if it should be considered a permanent failure
   */
  static boolean isHttpErrorRecoverable(int statusCode) {
    if (statusCode >= 400 && statusCode < 500) {
      switch (statusCode) {
      case 408: // request timeout
      case 429: // too many requests
        return true;
      default:
        return false; // all other 4xx errors are unrecoverable
      }
    }
    return true;
  }
  
  static String httpErrorMessage(int statusCode, String context) {
    StringBuilder sb = new StringBuilder();
    sb.append("Received HTTP error ").append(statusCode);
    switch (statusCode) {
    case 401:
    case 403:
      sb.append(" (invalid SDK key)");
    }
    sb.append(" for ").append(context);
    sb.append(isHttpErrorRecoverable(statusCode) ? " - will retry " : " - will not retry");
    return sb.toString();
  }
}
