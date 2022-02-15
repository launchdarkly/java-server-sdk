package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.logging.LDLogger;

/**
 * Context information provided by the {@link com.launchdarkly.sdk.server.LDClient} when creating components.
 * <p>
 * This is passed as a parameter to {@link DataStoreFactory#createDataStore(ClientContext, DataStoreUpdates)},
 * etc. Component factories do not receive the entire {@link com.launchdarkly.sdk.server.LDConfig} because
 * it could contain factory objects that have mutable state, and because components should not be able
 * to access the configurations of unrelated components.
 * <p>
 * The actual implementation class may contain other properties that are only relevant to the built-in
 * SDK components and are therefore not part of the public interface; this allows the SDK to add its own
 * context information as needed without disturbing the public API.
 * 
 * @since 5.0.0
 */
public interface ClientContext {
  /**
   * The SDK's basic global properties.
   * 
   * @return the basic configuration
   */
  public BasicConfiguration getBasic();
  
  /**
   * The configured networking properties that apply to all components.
   * 
   * @return the HTTP configuration
   */
  public HttpConfiguration getHttp();
  
  /**
   * The configured logging properties that apply to all components.
   * @return the logging configuration
   */
  public LoggingConfiguration getLogging();
  
  public interface WithBaseLogger {
    public LDLogger getBaseLogger();
  }
}
