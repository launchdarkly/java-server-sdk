package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder;

/**
 * Encapsulates the SDK's application metadata.
 * <p>
 * See {@link ApplicationInfoBuilder} for more details on these properties.
 * 
 * @since 5.8.0
 */
public final class ApplicationInfo {
  private String applicationId;
  private String applicationVersion;

  /**
   * Used internally by the SDK to store application metadata.
   *
   * @param applicationId the application ID
   * @param applicationVersion the application version
   * @see ApplicationInfoBuilder
   */
  public ApplicationInfo(String applicationId, String applicationVersion) {
    this.applicationId = applicationId;
    this.applicationVersion = applicationVersion;
  }

  /**
   * A unique identifier representing the application where the LaunchDarkly SDK is running.
   * 
   * @return the application identifier, or null
   */
  public String getApplicationId() {
    return applicationId;
  }

  /**
   * A unique identifier representing the version of the application where the
   * LaunchDarkly SDK is running.
   * 
   * @return the application version, or null
   */
  public String getApplicationVersion() {
    return applicationVersion;
  }
}
