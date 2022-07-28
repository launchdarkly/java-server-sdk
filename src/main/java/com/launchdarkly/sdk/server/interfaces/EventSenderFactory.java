package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.logging.LDLogger;

/**
 * Interface for a factory that creates some implementation of {@link EventSender}.
 *
 * @see com.launchdarkly.sdk.server.integrations.EventProcessorBuilder#eventSender(EventSenderFactory)
 * @since 4.14.0
 */
public interface EventSenderFactory {
  /**
   * Older method for creating the implementation object. This is superseded by the method that
   * includes a logger instance.
   * 
   * @param basicConfiguration the basic global SDK configuration properties
   * @param httpConfiguration HTTP configuration properties
   * @return an {@link EventSender}
   * @deprecated use the overload that includes a logger
   */
  @Deprecated
  EventSender createEventSender(BasicConfiguration basicConfiguration, HttpConfiguration httpConfiguration);

  /**
   * Called by the SDK to create the implementation object.
   * 
   * @param basicConfiguration the basic global SDK configuration properties
   * @param httpConfiguration HTTP configuration properties
   * @param logger the configured logger
   * @return an {@link EventSender}
   * @since 5.10.0
   */
  default EventSender createEventSender(
      BasicConfiguration basicConfiguration,
      HttpConfiguration httpConfiguration,
      LDLogger logger) {
    return createEventSender(basicConfiguration, httpConfiguration);
  }
}
