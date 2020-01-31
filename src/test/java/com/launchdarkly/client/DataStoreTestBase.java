package com.launchdarkly.client;

import com.launchdarkly.client.DataStoreTestTypes.TestItem;
import com.launchdarkly.client.TestUtil.DataBuilder;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.launchdarkly.client.DataStoreTestTypes.OTHER_TEST_ITEMS;
import static com.launchdarkly.client.DataStoreTestTypes.TEST_ITEMS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests for FeatureStore implementations. For database implementations, use the more
 * comprehensive FeatureStoreDatabaseTestBase.
 */
@SuppressWarnings("javadoc")
public abstract class DataStoreTestBase {

  protected DataStore store;
  protected boolean cached;

  protected TestItem item1 = new TestItem("first", "key1", 10);
  
  protected TestItem item2 = new TestItem("second", "key2", 10);
  
  protected TestItem otherItem1 = new TestItem("other-first", "key1", 11);
  
  public DataStoreTestBase() {
    this(false);
  }
  
  public DataStoreTestBase(boolean cached) {
    this.cached = cached;
  }
  
  /**
   * Test subclasses must override this method to create an instance of the feature store class, with
   * caching either enabled or disabled depending on the "cached" property.
   * @return
   */
  protected abstract DataStore makeStore();
  
  /**
   * Test classes should override this to clear all data from the underlying database, if it is
   * possible for data to exist there before the feature store is created (i.e. if
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
        new DataBuilder().add(TEST_ITEMS, item1, item2).add(OTHER_TEST_ITEMS, otherItem1).build();
    store.init(allData);
    
    TestItem item2v2 = item2.withVersion(item2.version + 1);
    allData = new DataBuilder().add(TEST_ITEMS, item2v2).add(OTHER_TEST_ITEMS).build();
    store.init(allData);
    
    assertNull(store.get(TEST_ITEMS, item1.key));
    assertEquals(item2v2, store.get(TEST_ITEMS, item2.key));
    assertNull(store.get(OTHER_TEST_ITEMS, otherItem1.key));
  }
  
  @Test
  public void getExistingItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    assertEquals(item1, store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void getNonexistingItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    assertNull(store.get(TEST_ITEMS, "biz"));
  }
  
  @Test
  public void getAll() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).add(OTHER_TEST_ITEMS, otherItem1).build());
    Map<String, TestItem> items = store.all(TEST_ITEMS);
    assertEquals(2, items.size());
    assertEquals(item1, items.get(item1.key));
    assertEquals(item2, items.get(item2.key));
  }
  
  @Test
  public void getAllWithDeletedItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    store.delete(TEST_ITEMS, item1.key, item1.getVersion() + 1);
    Map<String, TestItem> items = store.all(TEST_ITEMS);
    assertEquals(1, items.size());
    assertEquals(item2, items.get(item2.key));
  }
  
  @Test
  public void upsertWithNewerVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    TestItem newVer = item1.withVersion(item1.version + 1);
    store.upsert(TEST_ITEMS, newVer);
    assertEquals(newVer, store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void upsertWithOlderVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    TestItem oldVer = item1.withVersion(item1.version - 1);
    store.upsert(TEST_ITEMS, oldVer);
    assertEquals(item1, store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void upsertNewItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    TestItem newItem = new TestItem("new-name", "new-key", 99);
    store.upsert(TEST_ITEMS, newItem);
    assertEquals(newItem, store.get(TEST_ITEMS, newItem.key));
  }
  
  @Test
  public void deleteWithNewerVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    store.delete(TEST_ITEMS, item1.key, item1.version + 1);
    assertNull(store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void deleteWithOlderVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    store.delete(TEST_ITEMS, item1.key, item1.version - 1);
    assertNotNull(store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void deleteUnknownItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    store.delete(TEST_ITEMS, "biz", 11);
    assertNull(store.get(TEST_ITEMS, "biz"));
  }
  
  @Test
  public void upsertOlderVersionAfterDelete() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    store.delete(TEST_ITEMS, item1.key, item1.version + 1);
    store.upsert(TEST_ITEMS, item1);
    assertNull(store.get(TEST_ITEMS, item1.key));
  }
}
