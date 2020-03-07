package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.LDClientInterface;

/**
 * This interface can be implemented by any event listener that needs to know when it has been
 * registered or unregistered with a client instance.
 * 
 * @see LDClientInterface#registerFlagChangeListener(FlagChangeListener)
 * @see LDClientInterface#unregisterFlagChangeListener(FlagChangeListener)
 * @since 5.0.0
 */
public interface ListenerRegistration {
  /**
   * The SDK calls this method when the listener is being registered with a client so it will receive events.
   * <p>
   * The listener is not yet registered at this point so it cannot have received any events yet. 
   * 
   * @param client the client instance that is registering the listener
   */
  void onRegister(LDClientInterface client);
  
  /**
   * The SDK calls this method when the listener has been unregistered with a client so it will no longer
   * receive events.
   * 
   * @param client the client instance that has unregistered the listener
   */
  void onUnregister(LDClientInterface client);
}
