package com.launchdarkly.sdk.server.interfaces;

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
   * The current {@link com.launchdarkly.sdk.server.LDClient} instance's SDK key.
   * 
   * @return the SDK key
   */
  public String getSdkKey();
  
  /**
   * True if the SDK was configured to be completely offline.
   * 
   * @return the offline status
   * @see com.launchdarkly.sdk.server.LDConfig.Builder#offline(boolean)
   */
  public boolean isOffline();
  
  /**
   * The configured networking properties that apply to all components.
   * 
   * @return the HTTP configuration
   */
  public HttpConfiguration getHttpConfiguration();
  
  /**
   * The configured logging properties that apply to all components.
   * @return the logging configuration
   */
  public LoggingConfiguration getLoggingConfiguration();
  
  /**
   * The thread priority that should be used for any worker threads created by SDK components.
   * 
   * @return the thread priority
   * @see com.launchdarkly.sdk.server.LDConfig.Builder#threadPriority(int)
   */
  public int getThreadPriority();
}
