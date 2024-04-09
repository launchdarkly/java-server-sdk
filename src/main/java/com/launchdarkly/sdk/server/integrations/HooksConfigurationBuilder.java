package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.subsystems.HookConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains methods for configuring the SDK's 'hooks'.
 * <p>
 * If you want to add hooks, use {@link Components#hooks()}, configure accordingly, and pass it
 * to {@link com.launchdarkly.sdk.server.LDConfig.Builder#hooks(HooksConfigurationBuilder)}.
 *
 * <pre><code>
 *     List hooks = createSomeHooks();
 *     LDConfig config = new LDConfig.Builder()
 *         .hooks(
 *             Components.hooks()
 *                 .setHooks(hooks)
 *         )
 *         .build();
 * </code></pre>
 * <p>
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#hooks()}.
 */
public abstract class HooksConfigurationBuilder {

  /**
   * The current set of hooks the builder has.
   */
  protected List<Hook> hooks = Collections.emptyList();

  /**
   * Adds the provided list of hooks to the configuration.  Note that the order of hooks is important and controls
   * the order in which they will be executed.  See {@link Hook} for more details.
   *
   * @param hooks to be added to the configuration
   * @return the builder
   */
  public HooksConfigurationBuilder setHooks(List<Hook> hooks) {
    // copy to avoid list manipulations impacting the SDK
    this.hooks = Collections.unmodifiableList(new ArrayList<>(hooks));
    return this;
  }

  /**
   * @return the hooks configuration
   */
  abstract public HookConfiguration build();
}
