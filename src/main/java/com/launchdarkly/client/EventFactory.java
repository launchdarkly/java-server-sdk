package com.launchdarkly.client;

import com.google.gson.JsonElement;

abstract class EventFactory {
  public static final EventFactory DEFAULT = new DefaultEventFactory();
  
  protected abstract long getTimestamp();
  
  public FeatureRequestEvent newFeatureRequestEvent(FeatureFlag flag, LDUser user, FeatureFlag.VariationAndValue result, JsonElement defaultVal) {
    return new FeatureRequestEvent(getTimestamp(), flag.getKey(), user, flag.getVersion(),
        result == null ? null : result.getVariation(), result == null ? null : result.getValue(),
        defaultVal, null, flag.isTrackEvents(), flag.getDebugEventsUntilDate());
  }
  
  public FeatureRequestEvent newUnknownFeatureRequestEvent(String key, LDUser user, JsonElement defaultValue) {
    return new FeatureRequestEvent(getTimestamp(), key, user, null, null, defaultValue, defaultValue, null, false, null);
  }
  
  public FeatureRequestEvent newPrerequisiteFeatureRequestEvent(FeatureFlag prereqFlag, LDUser user, FeatureFlag.VariationAndValue result,
      FeatureFlag prereqOf) {
    return new FeatureRequestEvent(getTimestamp(), prereqFlag.getKey(), user, prereqFlag.getVersion(),
        result == null ? null : result.getVariation(), result == null ? null : result.getValue(),
        null, prereqOf.getKey(), prereqFlag.isTrackEvents(), prereqFlag.getDebugEventsUntilDate());
  }
  
  public CustomEvent newCustomEvent(String key, LDUser user, JsonElement data) {
    return new CustomEvent(getTimestamp(), key, user, data);
  }
  
  public IdentifyEvent newIdentifyEvent(LDUser user) {
    return new IdentifyEvent(getTimestamp(), user.getKeyAsString(), user);
  }
  
  public static class DefaultEventFactory extends EventFactory {
    @Override
    protected long getTimestamp() {
      return System.currentTimeMillis();
    }
  }
}
