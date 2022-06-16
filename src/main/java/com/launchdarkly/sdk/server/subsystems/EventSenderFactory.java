package com.launchdarkly.sdk.server.subsystems;

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
   * @param clientContext allows access to the client configuration
   * @return an {@link EventSender}
   */
  EventSender createEventSender(ClientContext clientContext);
}
