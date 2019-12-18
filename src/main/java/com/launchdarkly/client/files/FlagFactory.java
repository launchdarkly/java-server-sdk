package com.launchdarkly.client.files;

import com.launchdarkly.client.DataModel;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.value.LDValue;

/**
 * Creates flag or segment objects from raw JSON.
 * 
 * Note that the {@code FeatureFlag} and {@code Segment} classes are not public in the Java
 * client, so we refer to those class objects indirectly via {@code VersionedDataKind}; and
 * if we want to construct a flag from scratch, we can't use the constructor but instead must
 * build some JSON and then parse that.
 */
class FlagFactory {
  public static VersionedData flagFromJson(String jsonString) {
    return DataModel.DataKinds.FEATURES.deserialize(jsonString);
  }
  
  public static VersionedData flagFromJson(LDValue jsonTree) {
    return flagFromJson(jsonTree.toJsonString());
  }
  
  /**
   * Constructs a flag that always returns the same value. This is done by giving it a single
   * variation and setting the fallthrough variation to that.
   */
  public static VersionedData flagWithValue(String key, LDValue jsonValue) {
    LDValue o = LDValue.buildObject()
          .put("key", key)
          .put("on", true)
          .put("variations", LDValue.buildArray().add(jsonValue).build())
          .put("fallthrough", LDValue.buildObject().put("variation", 0).build())
          .build();
    // Note that LaunchDarkly normally prevents you from creating a flag with just one variation,
    // but it's the application that validates that; the SDK doesn't care.
    return flagFromJson(o);
  }
  
  public static VersionedData segmentFromJson(String jsonString) {
    return DataModel.DataKinds.SEGMENTS.deserialize(jsonString);
  }
  
  public static VersionedData segmentFromJson(LDValue jsonTree) {
    return segmentFromJson(jsonTree.toJsonString());
  }
}
