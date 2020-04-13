package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeListener;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of the flag change listener wrapper provided by
 * {@link Components#flagValueMonitoringListener(LDClientInterface, String, com.launchdarkly.sdk.LDUser, com.launchdarkly.sdk.server.interfaces.FlagValueChangeListener)}.
 * This class is deliberately not public, it is an implementation detail.
 */
final class FlagValueMonitoringListener implements FlagChangeListener {
  private final LDClientInterface client;
  private final AtomicReference<LDValue> currentValue = new AtomicReference<>(LDValue.ofNull());
  private final String flagKey;
  private final LDUser user;
  private final FlagValueChangeListener valueChangeListener;
  
  public FlagValueMonitoringListener(LDClientInterface client, String flagKey, LDUser user, FlagValueChangeListener valueChangeListener) {
    this.client = client;
    this.flagKey = flagKey;
    this.user = user;
    this.valueChangeListener = valueChangeListener;
    currentValue.set(client.jsonValueVariation(flagKey, user, LDValue.ofNull()));
  }
  
  @Override
  public void onFlagChange(FlagChangeEvent event) {
    if (event.getKey().equals(flagKey)) {
      LDValue newValue = client.jsonValueVariation(flagKey, user, LDValue.ofNull());
      LDValue previousValue = currentValue.getAndSet(newValue);
      if (!newValue.equals(previousValue)) {
        valueChangeListener.onFlagValueChange(new FlagValueChangeEvent(flagKey, previousValue, newValue)); 
      }
    }
  }
}
