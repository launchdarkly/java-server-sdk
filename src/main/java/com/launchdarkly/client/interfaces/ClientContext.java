package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.LDConfig;

/**
 * Context information provided by the {@link com.launchdarkly.client.LDClient} when creating components.
 * <p>
 * This is passed as a parameter to {@link DataStoreFactory#createDataStore(ClientContext)}, etc. The
 * actual implementation class may contain other properties that are only relevant to the built-in SDK
 * components and are therefore not part of the public interface; this allows the SDK to add its own
 * context information as needed without disturbing the public API.
 * 
 * @since 5.0.0
 */
public interface ClientContext {
  /**
   * The current {@link com.launchdarkly.client.LDClient} instance's SDK key.
   * @return the SDK key
   */
  public String getSdkKey();
  
  /**
   * The client configuration.
   * @return the configuration
   */
  public LDConfig getConfiguration();
}
