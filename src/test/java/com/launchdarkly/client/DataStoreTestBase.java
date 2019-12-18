package com.launchdarkly.client;

import com.launchdarkly.client.TestUtil.DataBuilder;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.launchdarkly.client.DataModel.DataKinds.FEATURES;
import static com.launchdarkly.client.DataModel.DataKinds.SEGMENTS;
import static com.launchdarkly.client.ModelBuilders.flagBuilder;
import static com.launchdarkly.client.ModelBuilders.segmentBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests for DataStore implementations. For database implementations, use the more
 * comprehensive DataStoreDatabaseTestBase.
 */
@SuppressWarnings("javadoc")
public abstract class DataStoreTestBase<T extends DataStore> {

  protected T store;
  protected boolean cached;
  
  protected DataModel.FeatureFlag feature1 = flagBuilder("foo")
      .version(10)
      .salt("abc")
      .build();
  
  protected DataModel.FeatureFlag feature2 = flagBuilder("bar")
      .version(10)
      .salt("abc")
      .build();
  
  protected DataModel.Segment segment1 = segmentBuilder("foo")
      .version(11)
      .build();
  
  public DataStoreTestBase() {
    this(false);
  }
  
  public DataStoreTestBase(boolean cached) {
    this.cached = cached;
  }
  
  /**
   * Test subclasses must override this method to create an instance of the data store class, with
   * caching either enabled or disabled depending on the "cached" property.
   * @return
   */
  protected abstract T makeStore();
  
  /**
   * Test classes should override this to clear all data from the underlying database, if it is
   * possible for data to exist there before the data store is created (i.e. if
   * isUnderlyingDataSharedByAllInstances() returns true).
   */
  protected void clearAllData() {
  }
  
  @Before
  public void setup() {
    store = makeStore();
  }
  
  @After
  public void teardown() throws Exception {
    store.close();
  }
  
  @Test
  public void storeNotInitializedBeforeInit() {
    clearAllData();
    assertFalse(store.initialized());
  }
  
  @Test
  public void storeInitializedAfterInit() {
    store.init(new DataBuilder().build());
    assertTrue(store.initialized());
  }
  
  @Test
  public void initCompletelyReplacesPreviousData() {
    clearAllData();
    
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData =
        new DataBuilder().add(FEATURES, feature1, feature2).add(SEGMENTS, segment1).build();
    store.init(allData);
    
    DataModel.FeatureFlag feature2v2 = flagBuilder(feature2).version(feature2.getVersion() + 1).build();
    allData = new DataBuilder().add(FEATURES, feature2v2).add(SEGMENTS).build();
    store.init(allData);
    
    assertNull(store.get(FEATURES, feature1.getKey()));
    DataModel.FeatureFlag item2 = store.get(FEATURES, feature2.getKey());
    assertNotNull(item2);
    assertEquals(feature2v2.getVersion(), item2.getVersion());
    assertNull(store.get(SEGMENTS, segment1.getKey()));
  }
  
  @Test
  public void getExistingFeature() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    DataModel.FeatureFlag result = store.get(FEATURES, feature1.getKey());
    assertEquals(feature1.getKey(), result.getKey());
  }
  
  @Test
  public void getNonexistingFeature() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    assertNull(store.get(FEATURES, "biz"));
  }
  
  @Test
  public void getAll() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).add(SEGMENTS, segment1).build());
    Map<String, DataModel.FeatureFlag> items = store.all(FEATURES);
    assertEquals(2, items.size());
    DataModel.FeatureFlag item1 = items.get(feature1.getKey());
    assertNotNull(item1);
    assertEquals(feature1.getVersion(), item1.getVersion());
    DataModel.FeatureFlag item2 = items.get(feature2.getKey());
    assertNotNull(item2);
    assertEquals(feature2.getVersion(), item2.getVersion());
  }
  
  @Test
  public void getAllWithDeletedItem() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    store.delete(FEATURES, feature1.getKey(), feature1.getVersion() + 1);
    Map<String, DataModel.FeatureFlag> items = store.all(FEATURES);
    assertEquals(1, items.size());
    DataModel.FeatureFlag item2 = items.get(feature2.getKey());
    assertNotNull(item2);
    assertEquals(feature2.getVersion(), item2.getVersion());
  }
  
  @Test
  public void upsertWithNewerVersion() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    DataModel.FeatureFlag newVer = flagBuilder(feature1)
        .version(feature1.getVersion() + 1)
        .build();
    store.upsert(FEATURES, newVer);
    DataModel.FeatureFlag result = store.get(FEATURES, newVer.getKey());
    assertEquals(newVer.getVersion(), result.getVersion());
  }
  
  @Test
  public void upsertWithOlderVersion() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    DataModel.FeatureFlag oldVer = flagBuilder(feature1)
        .version(feature1.getVersion() - 1)
        .build();
    store.upsert(FEATURES, oldVer);
    DataModel.FeatureFlag result = store.get(FEATURES, oldVer.getKey());
    assertEquals(feature1.getVersion(), result.getVersion());
  }
  
  @Test
  public void upsertNewFeature() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    DataModel.FeatureFlag newFeature = flagBuilder("biz")
        .version(99)
        .build();
    store.upsert(FEATURES, newFeature);
    DataModel.FeatureFlag result = store.get(FEATURES, newFeature.getKey());
    assertEquals(newFeature.getKey(), result.getKey());
  }
  
  @Test
  public void deleteWithNewerVersion() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    store.delete(FEATURES, feature1.getKey(), feature1.getVersion() + 1);
    assertNull(store.get(FEATURES, feature1.getKey()));
  }
  
  @Test
  public void deleteWithOlderVersion() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    store.delete(FEATURES, feature1.getKey(), feature1.getVersion() - 1);
    assertNotNull(store.get(FEATURES, feature1.getKey()));
  }
  
  @Test
  public void deleteUnknownFeature() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    store.delete(FEATURES, "biz", 11);
    assertNull(store.get(FEATURES, "biz"));
  }
  
  @Test
  public void upsertOlderVersionAfterDelete() {
    store.init(new DataBuilder().add(FEATURES, feature1, feature2).build());
    store.delete(FEATURES, feature1.getKey(), feature1.getVersion() + 1);
    store.upsert(FEATURES, feature1);
    assertNull(store.get(FEATURES, feature1.getKey()));
  }
}
