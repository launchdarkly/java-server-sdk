package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.Event;

abstract class EventFactory {
  public static final EventFactory DEFAULT = new DefaultEventFactory(false);
  public static final EventFactory DEFAULT_WITH_REASONS = new DefaultEventFactory(true);
  
  protected abstract long getTimestamp();
  protected abstract boolean isIncludeReasons();
  
  public Event.FeatureRequest newFeatureRequestEvent(DataModel.FeatureFlag flag, LDUser user, LDValue value,
      Integer variationIndex, EvaluationReason reason, LDValue defaultValue, String prereqOf) {
    boolean requireExperimentData = isExperiment(flag, reason);
    return new Event.FeatureRequest(
        getTimestamp(),
        flag.getKey(),
        user,
        flag.getVersion(),
        variationIndex,
        value,
        defaultValue,
        (requireExperimentData || isIncludeReasons()) ? reason : null,
        prereqOf,
        requireExperimentData || flag.isTrackEvents(),
        flag.getDebugEventsUntilDate(),
        false
    );
  }
  
  public Event.FeatureRequest newFeatureRequestEvent(DataModel.FeatureFlag flag, LDUser user, Evaluator.EvalResult result, LDValue defaultVal) {
    return newFeatureRequestEvent(flag, user, result == null ? null : result.getValue(),
        result == null ? null : result.getVariationIndex(), result == null ? null : result.getReason(),
        defaultVal, null);
  }
  
  public Event.FeatureRequest newDefaultFeatureRequestEvent(DataModel.FeatureFlag flag, LDUser user, LDValue defaultValue,
      EvaluationReason.ErrorKind errorKind) {
    return new Event.FeatureRequest(getTimestamp(), flag.getKey(), user, flag.getVersion(),
        null, defaultValue, defaultValue, isIncludeReasons() ? EvaluationReason.error(errorKind) : null,
        null, flag.isTrackEvents(), flag.getDebugEventsUntilDate(), false);
  }
  
  public Event.FeatureRequest newUnknownFeatureRequestEvent(String key, LDUser user, LDValue defaultValue,
      EvaluationReason.ErrorKind errorKind) {
    return new Event.FeatureRequest(getTimestamp(), key, user, null, null, defaultValue, defaultValue,
        isIncludeReasons() ? EvaluationReason.error(errorKind) : null, null, false, null, false);
  }
  
  public Event.FeatureRequest newPrerequisiteFeatureRequestEvent(DataModel.FeatureFlag prereqFlag, LDUser user,
      Evaluator.EvalResult details, DataModel.FeatureFlag prereqOf) {
    return newFeatureRequestEvent(prereqFlag, user, details == null ? null : details.getValue(),
        details == null ? null : details.getVariationIndex(), details == null ? null : details.getReason(),
        LDValue.ofNull(), prereqOf.getKey());
  }

  public Event.FeatureRequest newDebugEvent(Event.FeatureRequest from) {
    return new Event.FeatureRequest(from.getCreationDate(), from.getKey(), from.getUser(), from.getVersion(), from.getVariation(), from.getValue(),
        from.getDefaultVal(), from.getReason(), from.getPrereqOf(), from.isTrackEvents(), from.getDebugEventsUntilDate(), true);
  }
  
  public Event.Custom newCustomEvent(String key, LDUser user, LDValue data, Double metricValue) {
    return new Event.Custom(getTimestamp(), key, user, data, metricValue);
  }
  
  public Event.Identify newIdentifyEvent(LDUser user) {
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
      if (!(reason instanceof EvaluationReason.RuleMatch)) {
        // shouldn't be possible
        return false;
      }
      EvaluationReason.RuleMatch rm = (EvaluationReason.RuleMatch)reason;
      int ruleIndex = rm.getRuleIndex();
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

  public static class DefaultEventFactory extends EventFactory {
    private final boolean includeReasons;
    
    public DefaultEventFactory(boolean includeReasons) {
      this.includeReasons = includeReasons;
    }
    
    @Override
    protected long getTimestamp() {
      return System.currentTimeMillis();
    }
    
    @Override
    protected boolean isIncludeReasons() {
      return includeReasons;
    }
  }
}
