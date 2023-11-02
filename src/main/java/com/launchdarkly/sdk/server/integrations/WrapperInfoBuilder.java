package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.interfaces.WrapperInfo;

/**
 * Contains methods for configuring wrapper information.
 * <p>
 * This builder is primarily intended for use by LaunchDarkly in developing wrapper SDKs.
 * <p>
 * If the WrapperBuilder is used, then it will replace the wrapper information from the HttpPropertiesBuilder.
 * Additionally, any wrapper SDK may overwrite any application developer provided wrapper information.
 */
 public abstract class WrapperInfoBuilder {
  protected String wrapperName;
  protected String wrapperVersion;

  /**
   * Set the name of the wrapper.
   *
   * @param wrapperName the name of the wrapper
   * @return the builder
   */
  public WrapperInfoBuilder wrapperName(String wrapperName) {
    this.wrapperName = wrapperName;
    return this;
  }

  /**
   * Set the version of the wrapper.
   * <p>
   * This information will not be used unless the wrapperName is also set.
   *
   * @param wrapperVersion the version of the wrapper
   * @return the builder
   */
  public WrapperInfoBuilder wrapperVersion(String wrapperVersion) {
    this.wrapperVersion = wrapperVersion;
    return this;
  }

  public abstract WrapperInfo build();
}
