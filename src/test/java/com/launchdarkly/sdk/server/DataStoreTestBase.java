package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.DataStoreTestTypes.TestItem;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.launchdarkly.sdk.server.DataStoreTestTypes.OTHER_TEST_ITEMS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.TEST_ITEMS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toItemsMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests for FeatureStore implementations. For database implementations, use the more
 * comprehensive FeatureStoreDatabaseTestBase.
 */
@SuppressWarnings("javadoc")
public abstract class DataStoreTestBase {

  protected DataStore store;
  
  protected TestItem item1 = new TestItem("key1", "first", 10);
  
  protected TestItem item2 = new TestItem("key2", "second", 10);
  
  protected TestItem otherItem1 = new TestItem("key1", "other-first", 11);
    
  /**
   * Test subclasses must override this method to create an instance of the feature store class.
   * @return
   */
  protected abstract DataStore makeStore();
  
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
    assertFalse(store.isInitialized());
  }
  
  @Test
  public void storeInitializedAfterInit() {
    store.init(new DataBuilder().build());
    assertTrue(store.isInitialized());
  }
  
  @Test
  public void initCompletelyReplacesPreviousData() {
    FullDataSet<ItemDescriptor> allData =
        new DataBuilder().add(TEST_ITEMS, item1, item2).add(OTHER_TEST_ITEMS, otherItem1).build();
    store.init(allData);
    
    TestItem item2v2 = item2.withVersion(item2.version + 1);
    allData = new DataBuilder().add(TEST_ITEMS, item2v2).add(OTHER_TEST_ITEMS).build();
    store.init(allData);
    
    assertNull(store.get(TEST_ITEMS, item1.key));
    assertEquals(item2v2.toItemDescriptor(), store.get(TEST_ITEMS, item2.key));
    assertNull(store.get(OTHER_TEST_ITEMS, otherItem1.key));
  }
  
  @Test
  public void getExistingItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    assertEquals(item1.toItemDescriptor(), store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void getNonexistingItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    assertNull(store.get(TEST_ITEMS, "biz"));
  }
  
  @Test
  public void getAll() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).add(OTHER_TEST_ITEMS, otherItem1).build());
    Map<String, ItemDescriptor> items = toItemsMap(store.getAll(TEST_ITEMS));
    assertEquals(2, items.size());
    assertEquals(item1.toItemDescriptor(), items.get(item1.key));
    assertEquals(item2.toItemDescriptor(), items.get(item2.key));
  }
  
  @Test
  public void getAllWithDeletedItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    ItemDescriptor deletedItem = ItemDescriptor.deletedItem(item1.getVersion() + 1);
    store.upsert(TEST_ITEMS, item1.key, deletedItem);
    Map<String, ItemDescriptor> items = toItemsMap(store.getAll(TEST_ITEMS));
    assertEquals(2, items.size());
    assertEquals(deletedItem, items.get(item1.key));
    assertEquals(item2.toItemDescriptor(), items.get(item2.key));
  }
  
  @Test
  public void upsertWithNewerVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    TestItem newVer = item1.withVersion(item1.version + 1);
    store.upsert(TEST_ITEMS, item1.key, newVer.toItemDescriptor());
    assertEquals(newVer.toItemDescriptor(), store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void upsertWithOlderVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    TestItem oldVer = item1.withVersion(item1.version - 1);
    store.upsert(TEST_ITEMS, item1.key, oldVer.toItemDescriptor());
    assertEquals(item1.toItemDescriptor(), store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void upsertNewItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    TestItem newItem = new TestItem("new-name", "new-key", 99);
    store.upsert(TEST_ITEMS, newItem.key, newItem.toItemDescriptor());
    assertEquals(newItem.toItemDescriptor(), store.get(TEST_ITEMS, newItem.key));
  }
  
  @Test
  public void deleteWithNewerVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    ItemDescriptor deletedItem = ItemDescriptor.deletedItem(item1.version + 1);
    store.upsert(TEST_ITEMS, item1.key, deletedItem);
    assertEquals(deletedItem, store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void deleteWithOlderVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    ItemDescriptor deletedItem = ItemDescriptor.deletedItem(item1.version - 1);
    store.upsert(TEST_ITEMS, item1.key, deletedItem);
    assertEquals(item1.toItemDescriptor(), store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void deleteUnknownItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    ItemDescriptor deletedItem = ItemDescriptor.deletedItem(item1.version - 1);
    store.upsert(TEST_ITEMS, "biz", deletedItem);
    assertEquals(deletedItem, store.get(TEST_ITEMS, "biz"));
  }
  
  @Test
  public void upsertOlderVersionAfterDelete() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    ItemDescriptor deletedItem = ItemDescriptor.deletedItem(item1.version + 1);
    store.upsert(TEST_ITEMS, item1.key, deletedItem);
    store.upsert(TEST_ITEMS, item1.key, item1.toItemDescriptor());
    assertEquals(deletedItem, store.get(TEST_ITEMS, item1.key));
  }
}
