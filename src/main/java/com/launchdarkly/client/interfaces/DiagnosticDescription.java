package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.value.LDValue;

/**
 * Optional interface for components to describe their own configuration.
 * <p>
 * The SDK uses a simplified JSON representation of its configuration when recording diagnostics data.
 * Any class that implements {@link com.launchdarkly.client.interfaces.DataStoreFactory},
 * {@link com.launchdarkly.client.interfaces.DataSourceFactory}, {@link com.launchdarkly.client.interfaces.EventProcessorFactory},
 * or {@link com.launchdarkly.client.interfaces.PersistentDataStoreFactory} may choose to contribute
 * values to this representation, although the SDK may or may not use them. For components that do not
 * implement this interface, the SDK may instead describe them using {@code getClass().getSimpleName()}.
 * <p>
 * The {@link #describeConfiguration(LDConfig)} method should return either null or a JSON value. For
 * custom components, the value must be a string that describes the basic nature of this component
 * implementation (e.g. "Redis"). Built-in LaunchDarkly components may instead return a JSON object
 * containing multiple properties specific to the LaunchDarkly diagnostic schema.
 * 
 * @since 4.12.0
 */
public interface DiagnosticDescription {
  /**
   * Used internally by the SDK to inspect the configuration.
   * @param config the full configuration, in case this component depends on properties outside itself
   * @return an {@link LDValue} or null
   */
  LDValue describeConfiguration(LDConfig config);
}
