package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.server.integrations.HooksConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.Hook;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the SDK's 'hooks' configuration.
 * <p>
 * Use {@link HooksConfigurationBuilder} to construct an instance.
 */
public class HookConfiguration {

  private final List<Hook> hooks;

  /**
   * @param hooks the list of {@link Hook} that will be registered.
   */
  public HookConfiguration(List<Hook> hooks) {
    this.hooks = Collections.unmodifiableList(hooks);
  }

  /**
   * @return an immutable list of hooks
   */
  public List<Hook> getHooks() {
    return hooks;
  }
}
