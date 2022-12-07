package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.BaseTest;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.DataStoreTestTypes.TestItem;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.SerializedItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.launchdarkly.sdk.server.DataStoreTestTypes.OTHER_TEST_ITEMS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.TEST_ITEMS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toItemsMap;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toSerialized;
import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Similar to FeatureStoreTestBase, but exercises only the underlying database implementation of a persistent
 * data store. The caching behavior, which is entirely implemented by CachingStoreWrapper, is covered by
 * CachingStoreWrapperTest. 
 */
@SuppressWarnings("javadoc")
public abstract class PersistentDataStoreTestBase<T extends PersistentDataStore> extends BaseTest {
  protected T store;
  
  protected TestItem item1 = new TestItem("key1", "first", 10);
  
  protected TestItem item2 = new TestItem("key2", "second", 10);
  
  protected TestItem otherItem1 = new TestItem("key1", "other-first", 11);
  
  private ClientContext makeClientContext() {
    return clientContext("", baseConfig().build());
  }
  
  @SuppressWarnings("unchecked")
  private T makeConfiguredStore() {
    return (T)buildStore(null).build(makeClientContext());
  }

  @SuppressWarnings("unchecked")
  private T makeConfiguredStoreWithPrefix(String prefix) {
    ComponentConfigurer<PersistentDataStore> builder = buildStore(prefix);
    if (builder == null) {
      return null;
    }
    return (T)builder.build(makeClientContext());
  }
  
  /**
   * Test subclasses should override this method to prepare an instance of the data store class.
   * They are allowed to return null if {@code prefix} is non-null and they do not support prefixes.
   * 
   * @param prefix a database prefix or null
   * @return a factory for creating the data store
   */
  protected ComponentConfigurer<PersistentDataStore> buildStore(String prefix) {
    return null;
  }
  
  /**
   * Test classes should override this to clear all data from the underlying database.
   */
  protected abstract void clearAllData();

  /**
   * Test classes should override this (and return true) if it is possible to instrument the feature
   * store to execute the specified Runnable during an upsert operation, for concurrent modification tests.
   */
  protected boolean setUpdateHook(T storeUnderTest, Runnable hook) {
    return false;
  }
  
  private void assertEqualsSerializedItem(TestItem item, SerializedItemDescriptor serializedItemDesc) {
    // This allows for the fact that a PersistentDataStore may not be able to get the item version without
    // deserializing it, so we allow the version to be zero.
    assertEquals(item.toSerializedItemDescriptor().getSerializedItem(), serializedItemDesc.getSerializedItem());
    if (serializedItemDesc.getVersion() != 0) {
      assertEquals(item.version, serializedItemDesc.getVersion());
    }
  }
  
  private void assertEqualsDeletedItem(SerializedItemDescriptor expected, SerializedItemDescriptor serializedItemDesc) {
    // As above, the PersistentDataStore may not have separate access to the version and deleted state;
    // PersistentDataStoreWrapper compensates for this when it deserializes the item.
    if (serializedItemDesc.getSerializedItem() == null) {
      assertTrue(serializedItemDesc.isDeleted());
      assertEquals(expected.getVersion(), serializedItemDesc.getVersion());
    } else {
      ItemDescriptor itemDesc = TEST_ITEMS.deserialize(serializedItemDesc.getSerializedItem());
      assertEquals(ItemDescriptor.deletedItem(expected.getVersion()), itemDesc);
    }
  }
  
  @Before
  public void setup() {
    store = makeConfiguredStore();
  }
  
  @After
  public void teardown() throws Exception {
    if (store != null) {
      store.close();
    }
  }
  
  @Test
  public void storeNotInitializedBeforeInit() {
    clearAllData();
    assertFalse(store.isInitialized());
  }
  
  @Test
  public void storeInitializedAfterInit() {
    store.init(new DataBuilder().buildSerialized());
    assertTrue(store.isInitialized());
  }
  
  @Test
  public void initCompletelyReplacesPreviousData() {
    clearAllData();
    
    FullDataSet<SerializedItemDescriptor> allData =
        new DataBuilder().add(TEST_ITEMS, item1, item2).add(OTHER_TEST_ITEMS, otherItem1).buildSerialized();
    store.init(allData);
    
    TestItem item2v2 = item2.withVersion(item2.version + 1);
    allData = new DataBuilder().add(TEST_ITEMS, item2v2).add(OTHER_TEST_ITEMS).buildSerialized();
    store.init(allData);
    
    assertNull(store.get(TEST_ITEMS, item1.key));
    assertEqualsSerializedItem(item2v2, store.get(TEST_ITEMS, item2.key));
    assertNull(store.get(OTHER_TEST_ITEMS, otherItem1.key));
  }

  @Test
  public void oneInstanceCanDetectIfAnotherInstanceHasInitializedTheStore() {
    clearAllData();
    T store2 = makeConfiguredStore();
    
    assertFalse(store.isInitialized());
    
    store2.init(new DataBuilder().add(TEST_ITEMS, item1).buildSerialized());
    
    assertTrue(store.isInitialized());
  }

  @Test
  public void oneInstanceCanDetectIfAnotherInstanceHasInitializedTheStoreEvenIfEmpty() {
    clearAllData();
    T store2 = makeConfiguredStore();
    
    assertFalse(store.isInitialized());
    
    store2.init(new DataBuilder().buildSerialized());
    
    assertTrue(store.isInitialized());
  }
  
  @Test
  public void getExistingItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    assertEqualsSerializedItem(item1, store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void getNonexistingItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    assertNull(store.get(TEST_ITEMS, "biz"));
  }
  
  @Test
  public void getAll() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).add(OTHER_TEST_ITEMS, otherItem1).buildSerialized());
    Map<String, SerializedItemDescriptor> items = toItemsMap(store.getAll(TEST_ITEMS));
    assertEquals(2, items.size());
    assertEqualsSerializedItem(item1, items.get(item1.key));
    assertEqualsSerializedItem(item2, items.get(item2.key));
  }
  
  @Test
  public void getAllWithDeletedItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    SerializedItemDescriptor deletedItem = toSerialized(TEST_ITEMS, ItemDescriptor.deletedItem(item1.version + 1));
    store.upsert(TEST_ITEMS, item1.key, deletedItem);
    Map<String, SerializedItemDescriptor> items = toItemsMap(store.getAll(TEST_ITEMS));
    assertEquals(2, items.size());
    assertEqualsSerializedItem(item2, items.get(item2.key));
    assertEqualsDeletedItem(deletedItem, items.get(item1.key));
  }
  
  @Test
  public void upsertWithNewerVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    TestItem newVer = item1.withVersion(item1.version + 1).withName("modified");
    store.upsert(TEST_ITEMS, item1.key, newVer.toSerializedItemDescriptor());
    assertEqualsSerializedItem(newVer, store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void upsertWithOlderVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    TestItem oldVer = item1.withVersion(item1.version - 1).withName("modified");
    store.upsert(TEST_ITEMS, item1.key, oldVer.toSerializedItemDescriptor());
    assertEqualsSerializedItem(item1, store.get(TEST_ITEMS, oldVer.key));
  }
  
  @Test
  public void upsertNewItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    TestItem newItem = new TestItem("new-name", "new-key", 99);
    store.upsert(TEST_ITEMS, newItem.key, newItem.toSerializedItemDescriptor());
    assertEqualsSerializedItem(newItem, store.get(TEST_ITEMS, newItem.key));
  }
  
  @Test
  public void deleteWithNewerVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    SerializedItemDescriptor deletedItem = toSerialized(TEST_ITEMS, ItemDescriptor.deletedItem(item1.version + 1));
    store.upsert(TEST_ITEMS, item1.key, deletedItem);
    assertEqualsDeletedItem(deletedItem, store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void deleteWithOlderVersion() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    SerializedItemDescriptor deletedItem = toSerialized(TEST_ITEMS, ItemDescriptor.deletedItem(item1.version - 1));
    store.upsert(TEST_ITEMS, item1.key, deletedItem);
    assertEqualsSerializedItem(item1, store.get(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void deleteUnknownItem() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    SerializedItemDescriptor deletedItem = toSerialized(TEST_ITEMS, ItemDescriptor.deletedItem(11));
    store.upsert(TEST_ITEMS, "deleted-key", deletedItem);
    assertEqualsDeletedItem(deletedItem, store.get(TEST_ITEMS, "deleted-key"));
  }
  
  @Test
  public void upsertOlderVersionAfterDelete() {
    store.init(new DataBuilder().add(TEST_ITEMS, item1, item2).buildSerialized());
    SerializedItemDescriptor deletedItem = toSerialized(TEST_ITEMS, ItemDescriptor.deletedItem(item1.version + 1));
    store.upsert(TEST_ITEMS, item1.key, deletedItem);
    store.upsert(TEST_ITEMS, item1.key, item1.toSerializedItemDescriptor());
    assertEqualsDeletedItem(deletedItem, store.get(TEST_ITEMS, item1.key));
  }

  // The following two tests verify that the update version checking logic works correctly when
  // another client instance is modifying the same data. They will run only if the test class
  // supports setUpdateHook().
  
  @Test
  public void handlesUpsertRaceConditionAgainstExternalClientWithLowerVersion() throws Exception {
    final T store2 = makeConfiguredStore();
    
    int startVersion = 1;
    final int store2VersionStart = 2;
    final int store2VersionEnd = 4;
    int store1VersionEnd = 10;
    
    final TestItem startItem = new TestItem("me", "foo", startVersion);
    
    Runnable concurrentModifier = new Runnable() {
      int versionCounter = store2VersionStart;
      public void run() {
        if (versionCounter <= store2VersionEnd) {
          store2.upsert(TEST_ITEMS, startItem.key, startItem.withVersion(versionCounter).toSerializedItemDescriptor());
          versionCounter++;
        }
      }
    };
    
    try {
      assumeTrue(setUpdateHook(store, concurrentModifier));
      
      store.init(new DataBuilder().add(TEST_ITEMS, startItem).buildSerialized());
      
      TestItem store1End = startItem.withVersion(store1VersionEnd);
      store.upsert(TEST_ITEMS, startItem.key, store1End.toSerializedItemDescriptor());
      
      SerializedItemDescriptor result = store.get(TEST_ITEMS, startItem.key);
      assertEqualsSerializedItem(startItem.withVersion(store1VersionEnd), result);
    } finally {
      store2.close();
    }
  }
  
  @Test
  public void handlesUpsertRaceConditionAgainstExternalClientWithHigherVersion() throws Exception {
    final T store2 = makeConfiguredStore();
    
    int startVersion = 1;
    final int store2Version = 3;
    int store1VersionEnd = 2;
    
    final TestItem startItem = new TestItem("me", "foo", startVersion);
    
    Runnable concurrentModifier = new Runnable() {
      public void run() {
        store2.upsert(TEST_ITEMS, startItem.key, startItem.withVersion(store2Version).toSerializedItemDescriptor());
      }
    };
    
    try {
      assumeTrue(setUpdateHook(store, concurrentModifier));
      
      store.init(new DataBuilder().add(TEST_ITEMS, startItem).buildSerialized());
      
      TestItem store1End = startItem.withVersion(store1VersionEnd);
      store.upsert(TEST_ITEMS, startItem.key, store1End.toSerializedItemDescriptor());
      
      SerializedItemDescriptor result = store.get(TEST_ITEMS, startItem.key);
      assertEqualsSerializedItem(startItem.withVersion(store2Version), result);
    } finally {
      store2.close();
    }
  }
  
  @Test
  public void storesWithDifferentPrefixAreIndependent() throws Exception {
    T store1 = makeConfiguredStoreWithPrefix("aaa");
    Assume.assumeNotNull(store1);
    T store2 = makeConfiguredStoreWithPrefix("bbb");
    clearAllData();
    
    try {
      assertFalse(store1.isInitialized());
      assertFalse(store2.isInitialized());
      
      TestItem item1a = new TestItem("a1", "flag-a", 1);
      TestItem item1b = new TestItem("b", "flag-b", 1);
      TestItem item2a = new TestItem("a2", "flag-a", 2);
      TestItem item2c = new TestItem("c", "flag-c", 2);
      
      store1.init(new DataBuilder().add(TEST_ITEMS, item1a, item1b).buildSerialized());
      assertTrue(store1.isInitialized());
      assertFalse(store2.isInitialized());
      
      store2.init(new DataBuilder().add(TEST_ITEMS, item2a, item2c).buildSerialized());
      assertTrue(store1.isInitialized());
      assertTrue(store2.isInitialized());
      
      Map<String, SerializedItemDescriptor> items1 = toItemsMap(store1.getAll(TEST_ITEMS));
      Map<String, SerializedItemDescriptor> items2 = toItemsMap(store2.getAll(TEST_ITEMS));
      assertEquals(2, items1.size());
      assertEquals(2, items2.size());
      assertEqualsSerializedItem(item1a, items1.get(item1a.key));
      assertEqualsSerializedItem(item1b, items1.get(item1b.key));
      assertEqualsSerializedItem(item2a, items2.get(item2a.key));
      assertEqualsSerializedItem(item2c, items2.get(item2c.key));
      
      assertEqualsSerializedItem(item1a, store1.get(TEST_ITEMS, item1a.key));
      assertEqualsSerializedItem(item1b, store1.get(TEST_ITEMS, item1b.key));
      assertEqualsSerializedItem(item2a, store2.get(TEST_ITEMS, item2a.key));
      assertEqualsSerializedItem(item2c, store2.get(TEST_ITEMS, item2c.key));
    } finally {
      store1.close();
      store2.close();
    }
  }
}
