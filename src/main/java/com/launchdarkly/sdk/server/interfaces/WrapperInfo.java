package com.launchdarkly.sdk.server.interfaces;

/**
 * Contains wrapper SDK information.
 * <p>
 * This is intended for use within the SDK.
 */
final public class WrapperInfo {
  private final String wrapperName;
  private final String wrapperVersion;

  /**
   * Get the name of the wrapper.
   *
   * @return the wrapper name
   */
  public String getWrapperName() {
    return wrapperName;
  }

  /**
   * Get the version of the wrapper.
   *
   * @return the wrapper version
   */
  public String getWrapperVersion() {
    return wrapperVersion;
  }

  /**
   * Used internally by the SDK to track wrapper information.
   *
   * @param wrapperName the name of the wrapper
   * @param wrapperVersion the version of the wrapper
   */
  public WrapperInfo(String wrapperName, String wrapperVersion) {
    this.wrapperName = wrapperName;
    this.wrapperVersion = wrapperVersion;
  }
}
