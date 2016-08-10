package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;

class Util {
  /**
   * Converts either a unix epoch millis number or RFC3339/ISO8601 timestamp as {@link JsonPrimitive} to a {@link DateTime} object.
   * @param maybeDate wraps either a nubmer or a string that may contain a valid timestamp.
   * @return null if input is not a valid format.
   */
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

  /**
   * Logs an error if the response is not 2xx and returns false. Otherwise returns true.
   *
   * @param logger   for logging responses.
   * @param request  http request
   * @param response http response
   * @return whether or not the response's status code is acceptable.
   */
  static boolean handleResponse(Logger logger, HttpRequestBase request, CloseableHttpResponse response) {
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode / 100 == 2) {
      return true;
    }
    if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
      logger.error("[401] Invalid SDK key when accessing URI: " + request.getURI().toString());
    } else {
      logger.error("[" + statusCode + "] " + response.getStatusLine().getReasonPhrase() + " When accessing URI: " + request.getURI().toString());
    }
    return false;
  }
}
