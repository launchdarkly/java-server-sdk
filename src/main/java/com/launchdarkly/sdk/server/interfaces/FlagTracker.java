package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.Components;

/**
 * An interface for tracking changes in feature flag configurations.
 * <p>
 * An implementation of this interface is returned by {@link com.launchdarkly.sdk.server.interfaces.LDClientInterface#getFlagTracker()}.
 * Application code never needs to implement this interface.
 * 
 * @since 5.0.0
 */
public interface FlagTracker {
  /**
   * Registers a listener to be notified of feature flag changes in general.
   * <p>
   * The listener will be notified whenever the SDK receives any change to any feature flag's configuration,
   * or to a user segment that is referenced by a feature flag. If the updated flag is used as a prerequisite
   * for other flags, the SDK assumes that those flags may now behave differently and sends flag change events
   * for them as well.
   * <p>
   * Note that this does not necessarily mean the flag's value has changed for any particular evaluation
   * context, only that some part of the flag configuration was changed so that it <i>may</i> return a
   * different value than it previously returned for some context. If you want to track flag value changes,
   * use {@link #addFlagValueChangeListener(String, LDContext, FlagValueChangeListener)} instead.
   * <p>
   * If using the file data source ({@link com.launchdarkly.sdk.server.integrations.FileData}), any change in
   * a data file will be treated as a change to every flag. Again, use
   * {@link #addFlagValueChangeListener(String, LDContext, FlagValueChangeListener)} (or just re-evaluate the flag
   * yourself) if you want to know whether this is a change that really affects a flag's value.  
   * <p>
   * Change events only work if the SDK is actually connecting to LaunchDarkly (or using the file data source).
   * If the SDK is only reading flags from a database ({@link Components#externalUpdatesOnly()}) then it cannot
   * know when there is a change, because flags are read on an as-needed basis.
   * <p>
   * The listener will be called from a worker thread.
   * <p>
   * Calling this method for an already-registered listener has no effect.
   * 
   * @param listener the event listener to register
   * @see #removeFlagChangeListener(FlagChangeListener)
   * @see FlagChangeListener
   * @see #addFlagValueChangeListener(String, LDContext, FlagValueChangeListener)
   */
  public void addFlagChangeListener(FlagChangeListener listener);
  
  /**
   * Unregisters a listener so that it will no longer be notified of feature flag changes.
   * <p>
   * Calling this method for a listener that was not previously registered has no effect.
   * 
   * @param listener the event listener to unregister
   * @see #addFlagChangeListener(FlagChangeListener)
   * @see #addFlagValueChangeListener(String, LDContext, FlagValueChangeListener)
   * @see FlagChangeListener
   */
  public void removeFlagChangeListener(FlagChangeListener listener);
  
  /**
   * Registers a listener to be notified of a change in a specific feature flag's value for a specific
   * evaluation context.
   * <p>
   * When you call this method, it first immediately evaluates the feature flag. It then uses
   * {@link #addFlagChangeListener(FlagChangeListener)} to start listening for feature flag configuration
   * changes, and whenever the specified feature flag changes, it re-evaluates the flag for the same context.
   * It then calls your {@link FlagValueChangeListener} if and only if the resulting value has changed.
   * <p>
   * All feature flag evaluations require an instance of {@link LDContext}. If the feature flag you are
   * tracking does not have any user targeting rules, you must still pass a dummy context such as
   * {@code LDContext.create("for-global-flags")}. If you do not want the user to appear on your dashboard,
   * use the {@code anonymous} property: {@code LDContext.builder("for-global-flags").anonymous(true).build()}.
   * <p>
   * The returned {@link FlagChangeListener} represents the subscription that was created by this method
   * call; to unsubscribe, pass that object (not your {@code FlagValueChangeListener}) to
   * {@link #removeFlagChangeListener(FlagChangeListener)}.
   * 
   * @param flagKey the flag key to be evaluated
   * @param context the evaluation context
   * @param listener an object that you provide which will be notified of changes
   * @return a {@link FlagChangeListener} that can be used to unregister the listener
   * @since 6.0.0
   */
  public FlagChangeListener addFlagValueChangeListener(String flagKey, LDContext context, FlagValueChangeListener listener);
}
