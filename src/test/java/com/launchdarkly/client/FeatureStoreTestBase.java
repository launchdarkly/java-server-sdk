package com.launchdarkly.client;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public abstract class FeatureStoreTestBase<T extends FeatureStore> {

  protected T store;
  
  protected FeatureFlag feature1 = new FeatureFlagBuilder("foo")
      .version(10)
      .salt("abc")
      .build();
  
  protected FeatureFlag feature2 = new FeatureFlagBuilder("bar")
      .version(10)
      .salt("abc")
      .build();
  
  protected void initStore() {
    HashMap<String, FeatureFlag> flags = new HashMap<>();
    flags.put(feature1.getKey(), feature1);
    flags.put(feature2.getKey(), feature2);
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData = new HashMap<>();
    allData.put(FEATURES, flags);
    store.init(allData);
  }
  
  @Test
  public void storeInitializedAfterInit() {
    initStore();
    assertTrue(store.initialized());
  }
  
  @Test
  public void getExistingFeature() {
    initStore();
    FeatureFlag result = store.get(FEATURES, feature1.getKey());
    assertEquals(feature1.getKey(), result.getKey());
  }
  
  @Test
  public void getNonexistingFeature() {
    initStore();
    assertNull(store.get(FEATURES, "biz"));
  }
  
  @Test
  public void upsertWithNewerVersion() {
    initStore();
    FeatureFlag newVer = new FeatureFlagBuilder(feature1)
        .version(feature1.getVersion() + 1)
        .build();
    store.upsert(FEATURES, newVer);
    FeatureFlag result = store.get(FEATURES, newVer.getKey());
    assertEquals(newVer.getVersion(), result.getVersion());
  }
  
  @Test
  public void upsertWithOlderVersion() {
    initStore();
    FeatureFlag oldVer = new FeatureFlagBuilder(feature1)
        .version(feature1.getVersion() - 1)
        .build();
    store.upsert(FEATURES, oldVer);
    FeatureFlag result = store.get(FEATURES, oldVer.getKey());
    assertEquals(feature1.getVersion(), result.getVersion());
  }
  
  @Test
  public void upsertNewFeature() {
    initStore();
    FeatureFlag newFeature = new FeatureFlagBuilder("biz")
        .version(99)
        .build();
    store.upsert(FEATURES, newFeature);
    FeatureFlag result = store.get(FEATURES, newFeature.getKey());
    assertEquals(newFeature.getKey(), result.getKey());
  }
  
  @Test
  public void deleteWithNewerVersion() {
    initStore();
    store.delete(FEATURES, feature1.getKey(), feature1.getVersion() + 1);
    assertNull(store.get(FEATURES, feature1.getKey()));
  }
  
  @Test
  public void deleteWithOlderVersion() {
    initStore();
    store.delete(FEATURES, feature1.getKey(), feature1.getVersion() - 1);
    assertNotNull(store.get(FEATURES, feature1.getKey()));
  }
  
  @Test
  public void deleteUnknownFeature() {
    initStore();
    store.delete(FEATURES, "biz", 11);
    assertNull(store.get(FEATURES, "biz"));
  }
  
  @Test
  public void upsertOlderVersionAfterDelete() {
    initStore();
    store.delete(FEATURES, feature1.getKey(), feature1.getVersion() + 1);
    store.upsert(FEATURES, feature1);
    assertNull(store.get(FEATURES, feature1.getKey()));
  }
}
