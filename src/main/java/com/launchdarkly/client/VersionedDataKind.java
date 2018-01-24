package com.launchdarkly.client;

/**
 * The descriptor for a specific kind of {@link VersionedData} objects that may exist in a store.
 */
public abstract class VersionedDataKind<T extends VersionedData> {
  
  /**
   * A short string that serves as the unique name for the collection of these objects, e.g. "features".
   */
  public abstract String getNamespace();
  
  /**
   * The Java class for objects of this type.
   */
  public abstract Class<T> getItemClass();
  
  /**
   * The path prefix for objects of this type in events received from the streaming API.
   */
  public abstract String getStreamApiPath();
  
  /**
   * Return an instance of this type with the specified key and version, and deleted=true.
   */
  public abstract T makeDeletedItem(String key, int version);
  
  public String getKeyFromStreamApiPath(String path) {
    return path.startsWith(getStreamApiPath()) ? path.substring(getStreamApiPath().length()) : null;
  }
  
  
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
}
