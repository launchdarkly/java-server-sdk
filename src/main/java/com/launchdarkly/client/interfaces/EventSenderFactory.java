package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.integrations.EventProcessorBuilder;

/**
 * Interface for a factory that creates some implementation of {@link EventSender}.
 *
 * @see EventProcessorBuilder#eventSender(EventSenderFactory)
 * @since 4.14.0
 */
public interface EventSenderFactory {
  /**
   * Called by the SDK to create the implementation object.
   * 
   * @param sdkKey the configured SDK key
   * @param httpConfiguration HTTP configuration properties
   * @return an {@link EventSender}
   */
  EventSender createEventSender(String sdkKey, HttpConfiguration httpConfiguration);
}
