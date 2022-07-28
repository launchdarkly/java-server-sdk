package com.launchdarkly.sdk.server.subsystems;

/**
 * General exception class for all errors in serializing or deserializing JSON.
 * <p>
 * The SDK uses this class to avoid depending on exception types from the underlying JSON framework
 * that it uses (currently Gson).
 * <p>
 * This is currently an unchecked exception, because adding checked exceptions to existing SDK
 * interfaces would be a breaking change. In the future it will become a checked exception, to make
 * error-handling requirements clearer. However, public SDK client methods will not throw this
 * exception in any case; it is only relevant when implementing custom components.
 */
@SuppressWarnings("serial")
public class SerializationException extends RuntimeException {
  /**
   * Creates an instance.
   * @param cause the underlying exception
   */
  public SerializationException(Throwable cause) {
    super(cause);
  }
}
