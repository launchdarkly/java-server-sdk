package com.launchdarkly.client.files;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.launchdarkly.client.DataModel;
import com.launchdarkly.client.interfaces.VersionedData;

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
  
  public static VersionedData flagFromJson(JsonElement jsonTree) {
    return flagFromJson(jsonTree.toString());
  }
  
  /**
   * Constructs a flag that always returns the same value. This is done by giving it a single
   * variation and setting the fallthrough variation to that.
   */
  public static VersionedData flagWithValue(String key, JsonElement jsonValue) {
    JsonObject o = new JsonObject();
    o.addProperty("key", key);
    o.addProperty("on", true);
    JsonArray vs = new JsonArray();
    vs.add(jsonValue);
    o.add("variations", vs);
    // Note that LaunchDarkly normally prevents you from creating a flag with just one variation,
    // but it's the application that validates that; the SDK doesn't care.
    JsonObject ft = new JsonObject();
    ft.addProperty("variation", 0);
    o.add("fallthrough", ft);
    return flagFromJson(o);
  }
  
  public static VersionedData segmentFromJson(String jsonString) {
    return DataModel.DataKinds.SEGMENTS.deserialize(jsonString);
  }
  
  public static VersionedData segmentFromJson(JsonElement jsonTree) {
    return segmentFromJson(jsonTree.toString());
  }
}
