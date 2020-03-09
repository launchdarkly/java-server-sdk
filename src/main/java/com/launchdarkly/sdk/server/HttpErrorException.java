package com.launchdarkly.sdk.server;

@SuppressWarnings("serial")
final class HttpErrorException extends Exception {
  private final int status;
  
  public HttpErrorException(int status) {
    super("HTTP error " + status);
    this.status = status;
  }
  
  public int getStatus() {
    return status;
  }
}
