package com.launchdarkly.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Assert;
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
    store.init(flags);
  }
  
  @Test
  public void storeNotInitiallyInitialized() {
    assertFalse(store.initialized());
  }
  
  @Test
  public void storeInitializedAfterInit() {
    initStore();
    assertTrue(store.initialized());
  }
  
  @Test
  public void getExistingFeature() {
    initStore();
    FeatureFlag result = store.get(feature1.getKey());
    assertEquals(feature1.getKey(), result.getKey());
  }
  
  @Test
  public void getNonexistingFeature() {
    initStore();
    assertNull(store.get("biz"));
  }
  
  @Test
  public void upsertWithNewerVersion() {
    initStore();
    FeatureFlag newVer = new FeatureFlagBuilder(feature1)
        .version(feature1.getVersion() + 1)
        .build();
    store.upsert(newVer.getKey(), newVer);
    FeatureFlag result = store.get(newVer.getKey());
    assertEquals(newVer.getVersion(), result.getVersion());
  }
  
  @Test
  public void upsertWithOlderVersion() {
    initStore();
    FeatureFlag oldVer = new FeatureFlagBuilder(feature1)
        .version(feature1.getVersion() - 1)
        .build();
    store.upsert(oldVer.getKey(), oldVer);
    FeatureFlag result = store.get(oldVer.getKey());
    assertEquals(feature1.getVersion(), result.getVersion());
  }
  
  @Test
  public void upsertNewFeature() {
    initStore();
    FeatureFlag newFeature = new FeatureFlagBuilder("biz")
        .version(99)
        .build();
    store.upsert(newFeature.getKey(), newFeature);
    FeatureFlag result = store.get(newFeature.getKey());
    assertEquals(newFeature.getKey(), result.getKey());
  }
  
  @Test
  public void deleteWithNewerVersion() {
    initStore();
    store.delete(feature1.getKey(), feature1.getVersion() + 1);
    assertNull(store.get(feature1.getKey()));
  }
  
  @Test
  public void deleteWithOlderVersion() {
    initStore();
    store.delete(feature1.getKey(), feature1.getVersion() - 1);
    assertNotNull(store.get(feature1.getKey()));
  }
  
  @Test
  public void deleteUnknownFeature() {
    initStore();
    store.delete("biz", 11);
    assertNull(store.get("biz"));
  }
  
  @Test
  public void upsertOlderVersionAfterDelete() {
    initStore();
    store.delete(feature1.getKey(), feature1.getVersion() + 1);
    store.upsert(feature1.getKey(), feature1);
    assertNull(store.get(feature1.getKey()));
  }
}
