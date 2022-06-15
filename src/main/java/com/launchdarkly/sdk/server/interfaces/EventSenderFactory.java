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
   * Called by the SDK to create the implementation object.
   * 
   * @param basicConfiguration the basic global SDK configuration properties
   * @param httpConfiguration HTTP configuration properties
   * @return an {@link EventSender}
   */
  EventSender createEventSender(BasicConfiguration basicConfiguration, HttpConfiguration httpConfiguration);

  /**
   * Alternate factory interface for compatibility with the SDK's new logging facade. This is defined as a
   * separate interface for now because modifying EventSenderFactory would be a breaking change. This will
   * be simplified in the next major version. 
   * 
   * @since 5.10.0
   */
  public interface WithLogger {
    /**
     * Called by the SDK to create the implementation object.
     * 
     * @param basicConfiguration the basic global SDK configuration properties
     * @param httpConfiguration HTTP configuration properties
     * @param logger the configured logger
     * @return an {@link EventSender}
     */
    EventSender createEventSender(
        BasicConfiguration basicConfiguration,
        HttpConfiguration httpConfiguration,
        LDLogger logger);
  }
}
