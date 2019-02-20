package com.launchdarkly.client;

import com.google.gson.JsonElement;

abstract class EventFactory {
  public static final EventFactory DEFAULT = new DefaultEventFactory(false);
  public static final EventFactory DEFAULT_WITH_REASONS = new DefaultEventFactory(true);
  
  protected abstract long getTimestamp();
  protected abstract boolean isIncludeReasons();
  
  public Event.FeatureRequest newFeatureRequestEvent(FeatureFlag flag, LDUser user, JsonElement value,
      Integer variationIndex, EvaluationReason reason, JsonElement defaultValue, String prereqOf) {
    boolean requireExperimentData = isExperiment(flag, reason);
    return new Event.FeatureRequest(
        getTimestamp(),
        flag.getKey(),
        user,
        flag.getVersion(),
        variationIndex,
        value,
        defaultValue,
        prereqOf,
        requireExperimentData || flag.isTrackEvents(),
        flag.getDebugEventsUntilDate(),
        (requireExperimentData || isIncludeReasons()) ? reason : null,
        false
    );
  }
  
  public Event.FeatureRequest newFeatureRequestEvent(FeatureFlag flag, LDUser user, EvaluationDetail<JsonElement> result, JsonElement defaultVal) {
    return newFeatureRequestEvent(flag, user, result == null ? null : result.getValue(),
        result == null ? null : result.getVariationIndex(), result == null ? null : result.getReason(),
        defaultVal, null);
  }
  
  public Event.FeatureRequest newDefaultFeatureRequestEvent(FeatureFlag flag, LDUser user, JsonElement defaultValue,
      EvaluationReason.ErrorKind errorKind) {
    return new Event.FeatureRequest(getTimestamp(), flag.getKey(), user, flag.getVersion(),
        null, defaultValue, defaultValue, null, flag.isTrackEvents(), flag.getDebugEventsUntilDate(),
        isIncludeReasons() ? EvaluationReason.error(errorKind) : null, false);
  }
  
  public Event.FeatureRequest newUnknownFeatureRequestEvent(String key, LDUser user, JsonElement defaultValue,
      EvaluationReason.ErrorKind errorKind) {
    return new Event.FeatureRequest(getTimestamp(), key, user, null, null, defaultValue, defaultValue, null, false, null,
        isIncludeReasons() ? EvaluationReason.error(errorKind) : null, false);
  }
  
  public Event.FeatureRequest newPrerequisiteFeatureRequestEvent(FeatureFlag prereqFlag, LDUser user, EvaluationDetail<JsonElement> result,
      FeatureFlag prereqOf) {
    return newFeatureRequestEvent(prereqFlag, user, result == null ? null : result.getValue(),
        result == null ? null : result.getVariationIndex(), result == null ? null : result.getReason(),
        null, prereqOf.getKey());
  }

  public Event.FeatureRequest newDebugEvent(Event.FeatureRequest from) {
    return new Event.FeatureRequest(from.creationDate, from.key, from.user, from.version, from.variation, from.value,
        from.defaultVal, from.prereqOf, from.trackEvents, from.debugEventsUntilDate, from.reason, true);
  }
  
  public Event.Custom newCustomEvent(String key, LDUser user, JsonElement data) {
    return new Event.Custom(getTimestamp(), key, user, data);
  }
  
  public Event.Identify newIdentifyEvent(LDUser user) {
    return new Event.Identify(getTimestamp(), user);
  }
  
  private boolean isExperiment(FeatureFlag flag, EvaluationReason reason) {
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
        Rule rule = flag.getRules().get(ruleIndex);
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
