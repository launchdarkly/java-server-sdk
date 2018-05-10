package com.launchdarkly.client;

/**
 * Interface for a factory that creates some implementation of {@link EventProcessor}.
 * @see Components
 * @since 4.0.0
 */
public interface EventProcessorFactory {
  /**
   * Creates an implementation instance.
   * @param sdkKey the SDK key for your LaunchDarkly environment
   * @param config the LaunchDarkly configuration
   * @return an {@link EventProcessor}
   */
  EventProcessor createEventProcessor(String sdkKey, LDConfig config);
}
