package com.launchdarkly.sdk.server.interfaces;

/**
 * An event listener that is notified when a feature flag's value has changed for a specific
 * evaluation context.
 * <p>
 * Use this in conjunction with {@link FlagTracker#addFlagValueChangeListener(String, com.launchdarkly.sdk.LDContext, FlagValueChangeListener)}
 * if you want the client to re-evaluate a flag <i>for a specific evaluation context</i> whenever the
 * flag's configuration has changed, and notify you only if the new value is different from the old
 * value. The listener will not be notified if the flag's configuration is changed in some way that does
 * not affect its value for that context.
 * 
 * <pre><code>
 *     String flagKey = "my-important-flag";
 *     LDContext contextForFlagEvaluation = LDContext.create("context-key-for-global-flag-state");
 *     FlagValueChangeListener listenForNewValue = event -&gt; {
 *         if (event.getKey().equals(flagKey)) {
 *             doSomethingWithNewValue(event.getNewValue().booleanValue());
 *         }
 *     };
 *     client.getFlagTracker().addFlagValueChangeListener(flagKey,
 *         contextForFlagEvaluation, listenForNewValue);
 * </code></pre>
 * 
 * In the above example, the value provided in {@code event.getNewValue()} is the result of calling
 * {@code client.jsonValueVariation(flagKey, contextForFlagEvaluation, LDValue.ofNull())} after the flag
 * has changed.
 * 
 * @since 5.0.0
 * @see FlagChangeListener
 * @see FlagTracker#addFlagValueChangeListener(String, com.launchdarkly.sdk.LDContext, FlagValueChangeListener)
 */
public interface FlagValueChangeListener {
  /**
   * The SDK calls this method when a feature flag's value has changed with regard to the specified
   * evaluation context.
   * 
   * @param event the event parameters
   */
  void onFlagValueChange(FlagValueChangeEvent event);
}
