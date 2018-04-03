package com.launchdarkly.client;

import com.google.gson.JsonElement;

abstract class EventFactory {
  public static final EventFactory DEFAULT = new DefaultEventFactory();
  
  protected abstract long getTimestamp();
  
  public Event.FeatureRequest newFeatureRequestEvent(FeatureFlag flag, LDUser user, FeatureFlag.VariationAndValue result, JsonElement defaultVal) {
    return new Event.FeatureRequest(getTimestamp(), flag.getKey(), user, flag.getVersion(),
        result == null ? null : result.getVariation(), result == null ? null : result.getValue(),
        defaultVal, null, flag.isTrackEvents(), flag.getDebugEventsUntilDate());
  }
  
  public Event.FeatureRequest newUnknownFeatureRequestEvent(String key, LDUser user, JsonElement defaultValue) {
    return new Event.FeatureRequest(getTimestamp(), key, user, null, null, defaultValue, defaultValue, null, false, null);
  }
  
  public Event.FeatureRequest newPrerequisiteFeatureRequestEvent(FeatureFlag prereqFlag, LDUser user, FeatureFlag.VariationAndValue result,
      FeatureFlag prereqOf) {
    return new Event.FeatureRequest(getTimestamp(), prereqFlag.getKey(), user, prereqFlag.getVersion(),
        result == null ? null : result.getVariation(), result == null ? null : result.getValue(),
        null, prereqOf.getKey(), prereqFlag.isTrackEvents(), prereqFlag.getDebugEventsUntilDate());
  }
  
  public Event.Custom newCustomEvent(String key, LDUser user, JsonElement data) {
    return new Event.Custom(getTimestamp(), key, user, data);
  }
  
  public Event.Identify newIdentifyEvent(LDUser user) {
    return new Event.Identify(getTimestamp(), user);
  }
  
  public static class DefaultEventFactory extends EventFactory {
    @Override
    protected long getTimestamp() {
      return System.currentTimeMillis();
    }
  }
}
