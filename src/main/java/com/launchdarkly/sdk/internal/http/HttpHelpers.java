package com.launchdarkly.sdk.internal.http;

import java.net.URI;

/**
 * Helper methods related to HTTP.
 * <p>
 * This class is for internal use only and should not be documented in the SDK API. It is not
 * supported for any use outside of the LaunchDarkly SDKs, and is subject to change without notice.
 */
public abstract class HttpHelpers {
  private HttpHelpers() {}

  /**
   * Safely concatenates a path, ensuring that there is exactly one slash between components.
   * 
   * @param baseUri the base URI
   * @param path the path to add
   * @return a new URI
   */
  public static URI concatenateUriPath(URI baseUri, String path) {
    String uriStr = baseUri.toString();
    String addPath = path.startsWith("/") ? path.substring(1) : path;
    return URI.create(uriStr + (uriStr.endsWith("/") ? "" : "/") + addPath);
  }

  /**
   * Tests whether a string contains only characters that are safe to use in an HTTP header value.
   * <p>
   * This is specifically testing whether the string would be considered a valid HTTP header value
   * by the OkHttp client. The actual HTTP spec does not prohibit characters >= 127; OkHttp's
   * check is overly strict, as was pointed out in https://github.com/square/okhttp/issues/2016.
   * But all OkHttp 3.x and 4.x versions so far have continued to enforce that check. Control
   * characters other than a tab are always illegal.
   *
   * The value we're mainly concerned with is the SDK key (Authorization header). If an SDK key
   * accidentally has (for instance) a newline added to it, we don't want to end up having OkHttp
   * throw an exception mentioning the value, which might get logged (https://github.com/square/okhttp/issues/6738).
   * 
   * @param value a string
   * @return true if valid
   */
  public static boolean isAsciiHeaderValue(String value) {
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if ((ch < 0x20 || ch > 0x7e) && ch != '\t') {
        return false;
      }
    }
    return true;
  }
  
}
