package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;

/**
 * The descriptor for a specific kind of {@link VersionedData} objects that may exist in a {@link FeatureStore}.
 * You will not need to refer to this type unless you are directly manipulating a {@code FeatureStore}
 * or writing your own {@code FeatureStore} implementation. If you are implementing a custom store, for
 * maximum forward compatibility you should only refer to {@link VersionedData}, {@link VersionedDataKind},
 * and {@link VersionedDataKind#ALL}, and avoid any dependencies on specific type descriptor instances
 * or any specific fields of the types they describe.
 * @since 3.0.0
 */
public abstract class VersionedDataKind<T extends VersionedData> {
  
  /**
   * A short string that serves as the unique name for the collection of these objects, e.g. "features".
   * @return a namespace string
   */
  public abstract String getNamespace();
  
  /**
   * The Java class for objects of this type.
   * @return a Java class
   */
  public abstract Class<T> getItemClass();
  
  /**
   * The path prefix for objects of this type in events received from the streaming API.
   * @return the URL path
   */
  public abstract String getStreamApiPath();
  
  /**
   * Return an instance of this type with the specified key and version, and deleted=true.
   * @param key the unique key
   * @param version the version number
   * @return a new instance
   */
  public abstract T makeDeletedItem(String key, int version);
  
  /**
   * Used internally to match data URLs in the streaming API.
   * @param path path from an API message
   * @return the parsed key if the path refers to an object of this kind, otherwise null 
   */
  String getKeyFromStreamApiPath(String path) {
    return path.startsWith(getStreamApiPath()) ? path.substring(getStreamApiPath().length()) : null;
  }
  
  /**
   * The {@link VersionedDataKind} instance that describes feature flag data.
   */
  public static VersionedDataKind<FeatureFlag> FEATURES = new VersionedDataKind<FeatureFlag>() {
    
    public String getNamespace() {
      return "features";
    }
    
    public Class<FeatureFlag> getItemClass() {
      return FeatureFlag.class;
    }
    
    public String getStreamApiPath() {
      return "/flags/";
    }
    
    public FeatureFlag makeDeletedItem(String key, int version) {
      return new FeatureFlagBuilder(key).deleted(true).version(version).build();
    }
  };
  
  /**
   * The {@link VersionedDataKind} instance that describes user segment data.
   */
  public static VersionedDataKind<Segment> SEGMENTS = new VersionedDataKind<Segment>() {
    
    public String getNamespace() {
      return "segments";
    }
    
    public Class<Segment> getItemClass() {
      return Segment.class;
    }
    
    public String getStreamApiPath() {
      return "/segments/";
    }
    
    public Segment makeDeletedItem(String key, int version) {
      return new Segment.Builder(key).deleted(true).version(version).build();
    }
  };
  
  /**
   * A list of all existing instances of {@link VersionedDataKind}.
   * @since 4.1.0
   */
  public static Iterable<VersionedDataKind<?>> ALL = ImmutableList.of(FEATURES, SEGMENTS);
}
