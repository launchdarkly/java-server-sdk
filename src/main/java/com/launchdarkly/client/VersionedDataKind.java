package com.launchdarkly.client;

/**
 * The descriptor for a specific kind of {@link VersionedData} objects that may exist in a store.
 */
public interface VersionedDataKind<T extends VersionedData> {
  /**
   * A short string that serves as the unique name for the collection of these objects, e.g. "features".
   */
  String getNamespace();
  /**
   * The Java class for objects of this type.
   */
  Class<T> getItemClass();
  /**
   * Return an instance of this type with the specified key and version, and deleted=true.
   */
  T makeDeletedItem(String key, int version);
  
  
  public static VersionedDataKind<FeatureFlag> FEATURES = new VersionedDataKind<FeatureFlag>() {
    
    public String getNamespace() {
      return "features";
    }
    
    public Class<FeatureFlag> getItemClass() {
      return FeatureFlag.class;
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
    
    public Segment makeDeletedItem(String key, int version) {
      return new Segment.Builder(key).deleted(true).version(version).build();
    }
  };
}
