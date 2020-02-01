package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.Components;

/**
 * Interface for a factory that creates some implementation of {@link EventProcessor}.
 * @see Components
 * @since 4.0.0
 */
public interface EventProcessorFactory {
  /**
   * Creates an implementation instance.
   * 
   * @param context allows access to the client configuration
   * @return an {@link EventProcessor}
   */
  EventProcessor createEventProcessor(ClientContext context);
}
