package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import okhttp3.Headers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

class Util {
  /**
   * Converts either a unix epoch millis number or RFC3339/ISO8601 timestamp as {@link JsonPrimitive} to a {@link DateTime} object.
   * @param maybeDate wraps either a number or a string that may contain a valid timestamp.
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

  static Headers.Builder getHeadersBuilderFor(String sdkKey, LDConfig config) {
    Headers.Builder builder = new Headers.Builder()
        .add("Authorization", sdkKey)
        .add("User-Agent", "JavaClient/" + LDClient.CLIENT_VERSION);

    if (config.wrapperName != null) {
      String wrapperVersion = "";
      if (config.wrapperVersion != null) {
        wrapperVersion = "/" + config.wrapperVersion;
      }
      builder.add("X-LaunchDarkly-Wrapper", config.wrapperName + wrapperVersion);
    }

    return builder;
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
