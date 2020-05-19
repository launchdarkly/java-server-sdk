package com.launchdarkly.sdk.server.interfaces;

/**
 * An event listener that is notified when a feature flag's configuration has changed.
 * <p>
 * As described in {@link FlagTracker#addFlagChangeListener(FlagChangeListener)}, this notification
 * does not mean that the flag now returns a different value for any particular user, only that it
 * <i>may</i> do so. LaunchDarkly feature flags can be configured to return a single value for all
 * users, or to have complex targeting behavior. To know what effect the change would have for any
 * given set of user properties, you would need to re-evaluate the flag by calling one of the
 * {@code variation} methods on the client.
 * 
 * <pre><code>
 *     FlagChangeListener listenForChanges = event -&gt; {
 *         System.out.println("a flag has changed: " + event.getKey());
 *     };
 *     client.getFlagTracker().addFlagChangeListener(listenForChanges);
 * </code></pre>
 *
 * In simple use cases where you know that the flag configuration does not vary per user, or where you
 * know ahead of time what user properties you will evaluate the flag with, it may be more convenient
 * to use {@link FlagValueChangeListener}.
 *
 * @since 5.0.0
 * @see FlagValueChangeListener
 * @see FlagTracker#addFlagChangeListener(FlagChangeListener)
 * @see FlagTracker#removeFlagChangeListener(FlagChangeListener)
 */
public interface FlagChangeListener {
  /**
   * The SDK calls this method when a feature flag's configuration has changed in some way.
   * 
   * @param event the event parameters
   */
  void onFlagChange(FlagChangeEvent event);
}
