package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.LDValue;

/**
 * Optional interface for components to describe their own configuration.
 * <p>
 * The SDK uses a simplified JSON representation of its configuration when recording diagnostics data.
 * Any class that implements {@link ComponentConfigurer} may choose to contribute
 * values to this representation, although the SDK may or may not use them. For components that do not
 * implement this interface, the SDK may instead describe them using {@code getClass().getSimpleName()}.
 * <p>
 * The {@link #describeConfiguration(ClientContext)} method should return either null or a JSON value. For
 * custom components, the value must be a string that describes the basic nature of this component
 * implementation (e.g. "Redis"). Built-in LaunchDarkly components may instead return a JSON object
 * containing multiple properties specific to the LaunchDarkly diagnostic schema.
 * 
 * @since 4.12.0
 */
public interface DiagnosticDescription {
  /**
   * Used internally by the SDK to inspect the configuration.
   * @param clientContext allows access to the client configuration
   * @return an {@link LDValue} or null
   */
  LDValue describeConfiguration(ClientContext clientContext);
}
