package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.LDClientInterface;

/**
 * This interface is implemented by any {@link FlagChangeListener} that needs to know when it has been
 * registered or unregistered with a client instance.
 * 
 * @see LDClientInterface#registerFlagChangeListener(FlagChangeListener)
 * @see LDClientInterface#unregisterFlagChangeListener(FlagChangeListener)
 * @since 5.0.0
 */
public interface FlagChangeListenerRegistration {
  /**
   * The SDK calls this method when {@link LDClientInterface#registerFlagChangeListener(FlagChangeListener)}
   * has been called for this listener.
   * <p>
   * The listener is not yet registered at this point so it cannot have received any events. 
   * 
   * @param client the client instance that is registering the listener
   */
  void onRegister(LDClientInterface client);
  
  /**
   * The SDK calls this method when {@link LDClientInterface#unregisterFlagChangeListener(FlagChangeListener)}
   * has been called for this listener.
   * <p>
   * The listener is already unregistered at this point so it will not receive any more events.
   * 
   * @param client the client instance that has unregistered the listener
   */
  void onUnregister(LDClientInterface client);
}
