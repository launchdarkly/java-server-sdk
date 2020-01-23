package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.value.LDValue;

/**
 * Optional interface for components to describe their own configuration.
 * <p>
 * The SDK uses a simplified JSON representation of its configuration when recording diagnostics data.
 * Any class that implements {@link com.launchdarkly.client.FeatureStoreFactory},
 * {@link com.launchdarkly.client.UpdateProcessorFactory}, {@link com.launchdarkly.client.EventProcessorFactory},
 * or {@link com.launchdarkly.client.interfaces.PersistentDataStoreFactory} may choose to contribute
 * values to this representation, although the SDK may or may not use them. For components that do not
 * implement this interface, the SDK may instead describe them using {@code getClass().getSimpleName()}.
 * <p>
 * The {@link #describeConfiguration()} method should return either null or a simple JSON value that
 * describes the basic nature of this component implementation (e.g. "Redis"). Currently the only
 * supported JSON value type is {@link com.launchdarkly.client.value.LDValueType#STRING}. Values over
 * 100 characters will be truncated.
 * 
 * @since 4.12.0
 */
public interface DiagnosticDescription {
  /**
   * Used internally by the SDK to inspect the configuration.
   * @return an {@link LDValue} or null
   */
  LDValue describeConfiguration();
}
