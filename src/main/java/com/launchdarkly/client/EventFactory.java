package com.launchdarkly.client;

import com.google.gson.JsonElement;

abstract class EventFactory {
  public static final EventFactory DEFAULT = new DefaultEventFactory(false);
  public static final EventFactory DEFAULT_WITH_REASONS = new DefaultEventFactory(true);
  
  protected abstract long getTimestamp();
  protected abstract boolean isIncludeReasons();
  
  public Event.FeatureRequest newFeatureRequestEvent(FeatureFlag flag, LDUser user, EvaluationDetail<JsonElement> result, JsonElement defaultVal) {
    return new Event.FeatureRequest(getTimestamp(), flag.getKey(), user, flag.getVersion(),
        result == null ? null : result.getVariationIndex(), result == null ? null : result.getValue(),
        defaultVal, null, flag.isTrackEvents(), flag.getDebugEventsUntilDate(),
        isIncludeReasons() ? result.getReason() : null, false);
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
    return new Event.FeatureRequest(getTimestamp(), prereqFlag.getKey(), user, prereqFlag.getVersion(),
        result == null ? null : result.getVariationIndex(), result == null ? null : result.getValue(),
        null, prereqOf.getKey(), prereqFlag.isTrackEvents(), prereqFlag.getDebugEventsUntilDate(),
        isIncludeReasons() ? result.getReason() : null, false);
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
