package com.launchdarkly.sdk.internal.http;

import com.launchdarkly.logging.LDLogger;

/**
 * Contains shared helpers related to HTTP response validation.
 * <p>
 * This class is for internal use only and should not be documented in the SDK API. It is not
 * supported for any use outside of the LaunchDarkly SDKs, and is subject to change without notice.
 */
public abstract class HttpErrors {
  private HttpErrors() {}
  
  /**
   * Represents an HTTP response error as an exception.
   */
  @SuppressWarnings("serial")
  public static final class HttpErrorException extends Exception {
    private final int status;
    
    /**
     * Constructs an instance.
     * @param status the status code
     */
    public HttpErrorException(int status) {
      super("HTTP error " + status);
      this.status = status;
    }
    
    /**
     * Returns the status code.
     * @return the status code
     */
    public int getStatus() {
      return status;
    }
  }
  
  /**
   * Tests whether an HTTP error status represents a condition that might resolve on its own if we retry.
   * @param statusCode the HTTP status
   * @return true if retrying makes sense; false if it should be considered a permanent failure
   */
  public static boolean isHttpErrorRecoverable(int statusCode) {
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
   * Logs an HTTP error or network error at the appropriate level and determines whether it is recoverable
   * (as defined by {@link #isHttpErrorRecoverable(int)}).
   *  
   * @param logger the logger to log to
   * @param errorDesc description of the error
   * @param errorContext a phrase like "when doing such-and-such"
   * @param statusCode HTTP status code, or 0 for a network error
   * @param recoverableMessage a phrase like "will retry" to use if the error is recoverable
   * @return true if the error is recoverable
   */
  public static boolean checkIfErrorIsRecoverableAndLog(
      LDLogger logger,
      String errorDesc,
      String errorContext,
      int statusCode,
      String recoverableMessage
      ) {
    if (statusCode > 0 && !isHttpErrorRecoverable(statusCode)) {
      logger.error("Error {} (giving up permanently): {}", errorContext, errorDesc);
      return false;
    } else {
      logger.warn("Error {} ({}): {}", errorContext, recoverableMessage, errorDesc);
      return true;
    }
  }
  
  /**
   * Returns a text description of an HTTP error.
   * 
   * @param statusCode the status code
   * @return the error description
   */
  public static String httpErrorDescription(int statusCode) {
    return "HTTP error " + statusCode +
        (statusCode == 401 || statusCode == 403 ? " (invalid SDK key)" : "");
  }
}
