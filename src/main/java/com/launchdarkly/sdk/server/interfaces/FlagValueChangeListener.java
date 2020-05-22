package com.launchdarkly.sdk.server.interfaces;

/**
 * An event listener that is notified when a feature flag's value has changed for a specific user.
 * <p>
 * Use this in conjunction with {@link FlagTracker#addFlagValueChangeListener(String, com.launchdarkly.sdk.LDUser, FlagValueChangeListener)}
 * if you want the client to re-evaluate a flag <i>for a specific set of user properties</i> whenever
 * the flag's configuration has changed, and notify you only if the new value is different from the old
 * value. The listener will not be notified if the flag's configuration is changed in some way that does
 * not affect its value for that user.
 * 
 * <pre><code>
 *     String flagKey = "my-important-flag";
 *     LDUser userForFlagEvaluation = new LDUser("user-key-for-global-flag-state");
 *     FlagValueChangeListener listenForNewValue = event -&gt; {
 *         if (event.getKey().equals(flagKey)) {
 *             doSomethingWithNewValue(event.getNewValue().booleanValue());
 *         }
 *     };
 *     client.getFlagTracker().addFlagValueChangeListener(flagKey,
 *         userForEvaluation, listenForNewValue));
 * </code></pre>
 * 
 * In the above example, the value provided in {@code event.getNewValue()} is the result of calling
 * {@code client.jsonValueVariation(flagKey, userForFlagEvaluation, LDValue.ofNull())} after the flag
 * has changed.
 * 
 * @since 5.0.0
 * @see FlagChangeListener
 * @see FlagTracker#addFlagValueChangeListener(String, com.launchdarkly.sdk.LDUser, FlagValueChangeListener)
 */
public interface FlagValueChangeListener {
  /**
   * The SDK calls this method when a feature flag's value has changed with regard to the specified user.
   * 
   * @param event the event parameters
   */
  void onFlagValueChange(FlagValueChangeEvent event);
}
