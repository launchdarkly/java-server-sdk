package com.launchdarkly.client;

public interface VersionedDataKind<T extends VersionedData> {
  String getNamespace();
  Class<T> getItemClass();
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
}
