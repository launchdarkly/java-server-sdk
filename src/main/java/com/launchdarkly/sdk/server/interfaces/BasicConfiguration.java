package com.launchdarkly.sdk.server.interfaces;

/**
 * The most basic properties of the SDK client that are available to all SDK component factories.
 * 
 * @since 5.0.0
 */
public final class BasicConfiguration {
  private final String sdkKey;
  private final boolean offline;
  private final int threadPriority;
  
  /**
   * Constructs an instance.
   * 
   * @param sdkKey the SDK key
   * @param offline true if the SDK was configured to be completely offline
   * @param threadPriority the thread priority that should be used for any worker threads created by SDK components
   */
  public BasicConfiguration(String sdkKey, boolean offline, int threadPriority) {
    this.sdkKey = sdkKey;
    this.offline = offline;
    this.threadPriority = threadPriority;
  }

  /**
   * Returns the configured SDK key.
   * 
   * @return the SDK key
   */
  public String getSdkKey() {
    return sdkKey;
  }

  /**
   * Returns true if the client was configured to be completely offline.
   * 
   * @return true if offline
   * @see com.launchdarkly.sdk.server.LDConfig.Builder#offline(boolean)
   */
  public boolean isOffline() {
    return offline;
  }
  
  /**
   * The thread priority that should be used for any worker threads created by SDK components.
   * 
   * @return the thread priority
   * @see com.launchdarkly.sdk.server.LDConfig.Builder#threadPriority(int)
   */
  public int getThreadPriority() {
    return threadPriority;
  }
}
