package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.Event;

abstract class EventFactory {
  public static final EventFactory DEFAULT = new DefaultEventFactory(false);
  public static final EventFactory DEFAULT_WITH_REASONS = new DefaultEventFactory(true);
  
  protected final boolean disabled;
  protected final boolean includeReasons;
  protected abstract long getTimestamp();
  
  protected EventFactory(boolean disabled, boolean includeReasons) {
    this.disabled = disabled;
    this.includeReasons = includeReasons;
  }
  
  public Event.FeatureRequest newFeatureRequestEvent(DataModel.FeatureFlag flag, LDUser user, LDValue value,
      int variationIndex, EvaluationReason reason, LDValue defaultValue, String prereqOf) {
    if (disabled) {
      return null;
    }
    boolean requireExperimentData = isExperiment(flag, reason);
    return new Event.FeatureRequest(
        getTimestamp(),
        flag.getKey(),
        user,
        flag.getVersion(),
        variationIndex,
        value,
        defaultValue,
        (requireExperimentData || includeReasons) ? reason : null,
        prereqOf,
        requireExperimentData || flag.isTrackEvents(),
        flag.getDebugEventsUntilDate() == null ? 0 : flag.getDebugEventsUntilDate().longValue(),
        false
    );
  }
  
  public Event.FeatureRequest newFeatureRequestEvent(DataModel.FeatureFlag flag, LDUser user, Evaluator.EvalResult result, LDValue defaultVal) {
    if (disabled) {
      return null;
    }
    return newFeatureRequestEvent(flag, user, result == null ? null : result.getValue(),
        result == null ? -1 : result.getVariationIndex(), result == null ? null : result.getReason(),
        defaultVal, null);
  }
  
  public Event.FeatureRequest newDefaultFeatureRequestEvent(DataModel.FeatureFlag flag, LDUser user, LDValue defaultValue,
      EvaluationReason.ErrorKind errorKind) {
    if (disabled) {
      return null;
    }
    return new Event.FeatureRequest(
        getTimestamp(),
        flag.getKey(),
        user,
        flag.getVersion(),
        -1,
        defaultValue,
        defaultValue,
        includeReasons ? EvaluationReason.error(errorKind) : null,
        null,
        flag.isTrackEvents(),
        flag.getDebugEventsUntilDate() == null ? 0 : flag.getDebugEventsUntilDate().longValue(),
        false
    );
  }
  
  public Event.FeatureRequest newUnknownFeatureRequestEvent(String key, LDUser user, LDValue defaultValue,
      EvaluationReason.ErrorKind errorKind) {
    if (disabled) {
      return null;
    }
    return new Event.FeatureRequest(
        getTimestamp(),
        key,
        user,
        -1,
        -1,
        defaultValue,
        defaultValue,
        includeReasons ? EvaluationReason.error(errorKind) : null,
        null,
        false,
        0,
        false
    );
  }
  
  public Event.FeatureRequest newPrerequisiteFeatureRequestEvent(DataModel.FeatureFlag prereqFlag, LDUser user,
      Evaluator.EvalResult details, DataModel.FeatureFlag prereqOf) {
    if (disabled) {
      return null;
    }
    return newFeatureRequestEvent(
        prereqFlag,
        user,
        details == null ? null : details.getValue(),
        details == null ? -1 : details.getVariationIndex(),
        details == null ? null : details.getReason(),
        LDValue.ofNull(),
        prereqOf.getKey()
    );
  }

  public Event.FeatureRequest newDebugEvent(Event.FeatureRequest from) {
    if (disabled) {
      return null;
    }
    return new Event.FeatureRequest(
        from.getCreationDate(), from.getKey(), from.getUser(), from.getVersion(), from.getVariation(), from.getValue(),
        from.getDefaultVal(), from.getReason(), from.getPrereqOf(), from.isTrackEvents(), from.getDebugEventsUntilDate(), true);
  }
  
  public Event.Custom newCustomEvent(String key, LDUser user, LDValue data, Double metricValue) {
    if (disabled) {
      return null;
    }
    return new Event.Custom(getTimestamp(), key, user, data, metricValue);
  }
  
  public Event.Identify newIdentifyEvent(LDUser user) {
    if (disabled) {
      return null;
    }
    return new Event.Identify(getTimestamp(), user);
  }
  
  private boolean isExperiment(DataModel.FeatureFlag flag, EvaluationReason reason) {
    if (reason == null) {
      // doesn't happen in real life, but possible in testing
      return false;
    }
    switch (reason.getKind()) { 
    case FALLTHROUGH:
      return flag.isTrackEventsFallthrough();
    case RULE_MATCH:
      int ruleIndex = reason.getRuleIndex();
      // Note, it is OK to rely on the rule index rather than the unique ID in this context, because the
      // FeatureFlag that is passed to us here *is* necessarily the same version of the flag that was just
      // evaluated, so we cannot be out of sync with its rule list.
      if (ruleIndex >= 0 && ruleIndex < flag.getRules().size()) {
        DataModel.Rule rule = flag.getRules().get(ruleIndex);
        return rule.isTrackEvents();
      }
      return false;
    default:
      return false;
    }
  }

  static final class DefaultEventFactory extends EventFactory {
    public DefaultEventFactory(boolean includeReasons) {
      super(false, includeReasons);
    }
    
    @Override
    protected long getTimestamp() {
      return System.currentTimeMillis();
    }
  }
  
  static final class DisabledEventFactory extends EventFactory {
    static final DisabledEventFactory INSTANCE = new DisabledEventFactory();
    
    private DisabledEventFactory() {
      super(true, false);
    }
    
    @Override
    protected long getTimestamp() {
      return 0;
    }
  }
}
