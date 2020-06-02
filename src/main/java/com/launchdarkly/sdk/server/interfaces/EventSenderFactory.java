package com.launchdarkly.sdk.server.interfaces;

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
}
