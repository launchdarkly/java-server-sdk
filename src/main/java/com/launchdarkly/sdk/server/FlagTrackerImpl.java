package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagTracker;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeListener;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

final class FlagTrackerImpl implements FlagTracker {
  private final EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeBroadcaster;
  private final BiFunction<String, LDContext, LDValue> evaluateFn;

  FlagTrackerImpl(
      EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeBroadcaster,
      BiFunction<String, LDContext, LDValue> evaluateFn
      ) {
    this.flagChangeBroadcaster = flagChangeBroadcaster;
    this.evaluateFn = evaluateFn;
  }

  @Override
  public void addFlagChangeListener(FlagChangeListener listener) {
    flagChangeBroadcaster.register(listener);
  }

  @Override
  public void removeFlagChangeListener(FlagChangeListener listener) {
    flagChangeBroadcaster.unregister(listener);
  }

  @Override
  public FlagChangeListener addFlagValueChangeListener(String flagKey, LDContext context, FlagValueChangeListener listener) {
    FlagValueChangeAdapter adapter = new FlagValueChangeAdapter(flagKey, context, listener);
    addFlagChangeListener(adapter);
    return adapter;
  }

  private final class FlagValueChangeAdapter implements FlagChangeListener {
    private final String flagKey; 
    private final LDContext context;
    private final FlagValueChangeListener listener;
    private final AtomicReference<LDValue> value;
    
    FlagValueChangeAdapter(String flagKey, LDContext context, FlagValueChangeListener listener) {
      this.flagKey = flagKey;
      this.context = context;
      this.listener = listener;
      this.value = new AtomicReference<>(evaluateFn.apply(flagKey, context));
    }
    
    @Override
    public void onFlagChange(FlagChangeEvent event) {
      if (event.getKey().equals(flagKey)) {
        LDValue newValue = evaluateFn.apply(flagKey, context);
        LDValue oldValue = value.getAndSet(newValue);
        if (!newValue.equals(oldValue)) {
          listener.onFlagValueChange(new FlagValueChangeEvent(flagKey, oldValue, newValue));
        }
      }
    }    
  }
}
