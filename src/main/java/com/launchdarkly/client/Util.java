package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.value.LDValue;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import okhttp3.Request;

class Util {
  /**
   * Converts either a unix epoch millis number or RFC3339/ISO8601 timestamp as {@link JsonPrimitive} to a {@link DateTime} object.
   * @param maybeDate wraps either a nubmer or a string that may contain a valid timestamp.
   * @return null if input is not a valid format.
   */
  static DateTime jsonPrimitiveToDateTime(LDValue maybeDate) {
    if (maybeDate.isNumber()) {
      return new DateTime((long)maybeDate.doubleValue());
    } else if (maybeDate.isString()) {
      try {
        return new DateTime(maybeDate.stringValue(), DateTimeZone.UTC);
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
   * @param statusCode the HTTP status
   * @return true if retrying makes sense; false if it should be considered a permanent failure
   */
  static boolean isHttpErrorRecoverable(int statusCode) {
    if (statusCode >= 400 && statusCode < 500) {
      switch (statusCode) {
      case 400: // bad request
      case 408: // request timeout
      case 429: // too many requests
        return true;
      default:
        return false; // all other 4xx errors are unrecoverable
      }
    }
    return true;
  }
  
  /**
   * Builds an appropriate log message for an HTTP error status.
   * @param statusCode the HTTP status
   * @param context description of what we were trying to do
   * @param recoverableMessage description of our behavior if the error is recoverable; typically "will retry"
   * @return a message string
   */
  static String httpErrorMessage(int statusCode, String context, String recoverableMessage) {
    StringBuilder sb = new StringBuilder();
    sb.append("Received HTTP error ").append(statusCode);
    switch (statusCode) {
    case 401:
    case 403:
      sb.append(" (invalid SDK key)");
    }
    sb.append(" for ").append(context).append(" - ");
    sb.append(isHttpErrorRecoverable(statusCode) ? recoverableMessage : "giving up permanently");
    return sb.toString();
  }
}
