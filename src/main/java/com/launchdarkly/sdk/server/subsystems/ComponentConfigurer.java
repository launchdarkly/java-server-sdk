package com.launchdarkly.sdk.server.subsystems;

/**
 * The common interface for SDK component factories and configuration builders. Applications should not
 * need to implement this interface.
 *
 * @param <T> the type of SDK component or configuration object being constructed
 * @since 6.0.0
 */
public interface ComponentConfigurer<T> {
  /**
   * Called internally by the SDK to create an implementation instance. Applications should not need
   * to call this method.
   * 
   * @param clientContext provides configuration properties and other components from the current
   *   SDK client instance
   * @return a instance of the component type
   */
  T build(ClientContext clientContext);
}
