package com.launchdarkly.client.integrations;

import com.launchdarkly.client.DataStoreTestTypes.TestItem;
import com.launchdarkly.client.TestUtil.DataBuilder;
import com.launchdarkly.client.VersionedData;
import com.launchdarkly.client.VersionedDataKind;
import com.launchdarkly.client.utils.FeatureStoreCore;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.launchdarkly.client.DataStoreTestTypes.OTHER_TEST_ITEMS;
import static com.launchdarkly.client.DataStoreTestTypes.TEST_ITEMS;
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
public abstract class PersistentDataStoreTestBase<T extends FeatureStoreCore> {
  protected T store;
  
  protected TestItem item1 = new TestItem("first", "key1", 10);
  
  protected TestItem item2 = new TestItem("second", "key2", 10);
  
  protected TestItem otherItem1 = new TestItem("other-first", "key1", 11);
  
  /**
   * Test subclasses must override this method to create an instance of the feature store class
   * with default properties.
   */
  protected abstract T makeStore();

  /**
   * Test subclasses should implement this if the feature store class supports a key prefix option
   * for keeping data sets distinct within the same database.
   */
  protected abstract T makeStoreWithPrefix(String prefix);
  
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
    assertFalse(store.initializedInternal());
  }
  
  @Test
  public void storeInitializedAfterInit() {
    store.initInternal(new DataBuilder().buildUnchecked());
    assertTrue(store.initializedInternal());
  }
  
  @Test
  public void initCompletelyReplacesPreviousData() {
    clearAllData();
    
    Map<VersionedDataKind<?>, Map<String, VersionedData>> allData =
        new DataBuilder().add(TEST_ITEMS, item1, item2).add(OTHER_TEST_ITEMS, otherItem1).buildUnchecked();
    store.initInternal(allData);
    
    TestItem item2v2 = item2.withVersion(item2.version + 1);
    allData = new DataBuilder().add(TEST_ITEMS, item2v2).add(OTHER_TEST_ITEMS).buildUnchecked();
    store.initInternal(allData);
    
    assertNull(store.getInternal(TEST_ITEMS, item1.key));
    assertEquals(item2v2, store.getInternal(TEST_ITEMS, item2.key));
    assertNull(store.getInternal(OTHER_TEST_ITEMS, otherItem1.key));
  }

  @Test
  public void oneInstanceCanDetectIfAnotherInstanceHasInitializedTheStore() {
    clearAllData();
    T store2 = makeStore();
    
    assertFalse(store.initializedInternal());
    
    store2.initInternal(new DataBuilder().add(TEST_ITEMS, item1).buildUnchecked());
    
    assertTrue(store.initializedInternal());
  }

  @Test
  public void oneInstanceCanDetectIfAnotherInstanceHasInitializedTheStoreEvenIfEmpty() {
    clearAllData();
    T store2 = makeStore();
    
    assertFalse(store.initializedInternal());
    
    store2.initInternal(new DataBuilder().buildUnchecked());
    
    assertTrue(store.initializedInternal());
  }
  
  @Test
  public void getExistingItem() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    assertEquals(item1, store.getInternal(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void getNonexistingItem() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    assertNull(store.getInternal(TEST_ITEMS, "biz"));
  }
  
  @Test
  public void getAll() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).add(OTHER_TEST_ITEMS, otherItem1).buildUnchecked());
    Map<String, VersionedData> items = store.getAllInternal(TEST_ITEMS);
    assertEquals(2, items.size());
    assertEquals(item1, items.get(item1.key));
    assertEquals(item2, items.get(item2.key));
  }
  
  @Test
  public void getAllWithDeletedItem() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    TestItem deletedItem = item1.withVersion(item1.version + 1).withDeleted(true);
    store.upsertInternal(TEST_ITEMS, deletedItem);
    Map<String, VersionedData> items = store.getAllInternal(TEST_ITEMS);
    assertEquals(2, items.size());
    assertEquals(item2, items.get(item2.key));
    assertEquals(deletedItem, items.get(item1.key));
  }
  
  @Test
  public void upsertWithNewerVersion() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    TestItem newVer = item1.withVersion(item1.version + 1).withName("modified");
    store.upsertInternal(TEST_ITEMS, newVer);
    assertEquals(newVer, store.getInternal(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void upsertWithOlderVersion() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    TestItem oldVer = item1.withVersion(item1.version - 1).withName("modified");
    store.upsertInternal(TEST_ITEMS, oldVer);
    assertEquals(item1, store.getInternal(TEST_ITEMS, oldVer.key));
  }
  
  @Test
  public void upsertNewItem() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    TestItem newItem = new TestItem("new-name", "new-key", 99);
    store.upsertInternal(TEST_ITEMS, newItem);
    assertEquals(newItem, store.getInternal(TEST_ITEMS, newItem.key));
  }
  
  @Test
  public void deleteWithNewerVersion() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    TestItem deletedItem = item1.withVersion(item1.version + 1).withDeleted(true);
    store.upsertInternal(TEST_ITEMS, deletedItem);
    assertEquals(deletedItem, store.getInternal(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void deleteWithOlderVersion() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    TestItem deletedItem = item1.withVersion(item1.version - 1).withDeleted(true);
    store.upsertInternal(TEST_ITEMS, deletedItem);
    assertEquals(item1, store.getInternal(TEST_ITEMS, item1.key));
  }
  
  @Test
  public void deleteUnknownItem() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    TestItem deletedItem = new TestItem(null, "deleted-key", 11, true);
    store.upsertInternal(TEST_ITEMS, deletedItem);
    assertEquals(deletedItem, store.getInternal(TEST_ITEMS, deletedItem.key));
  }
  
  @Test
  public void upsertOlderVersionAfterDelete() {
    store.initInternal(new DataBuilder().add(TEST_ITEMS, item1, item2).buildUnchecked());
    TestItem deletedItem = item1.withVersion(item1.version + 1).withDeleted(true);
    store.upsertInternal(TEST_ITEMS, deletedItem);
    store.upsertInternal(TEST_ITEMS, item1);
    assertEquals(deletedItem, store.getInternal(TEST_ITEMS, item1.key));
  }

  // The following two tests verify that the update version checking logic works correctly when
  // another client instance is modifying the same data. They will run only if the test class
  // supports setUpdateHook().
  
  @Test
  public void handlesUpsertRaceConditionAgainstExternalClientWithLowerVersion() throws Exception {
    final T store2 = makeStore();
    
    int startVersion = 1;
    final int store2VersionStart = 2;
    final int store2VersionEnd = 4;
    int store1VersionEnd = 10;
    
    final TestItem startItem = new TestItem("me", "foo", startVersion);
    
    Runnable concurrentModifier = new Runnable() {
      int versionCounter = store2VersionStart;
      public void run() {
        if (versionCounter <= store2VersionEnd) {
          store2.upsertInternal(TEST_ITEMS, startItem.withVersion(versionCounter));
          versionCounter++;
        }
      }
    };
    
    try {
      assumeTrue(setUpdateHook(store, concurrentModifier));
      
      store.initInternal(new DataBuilder().add(TEST_ITEMS, startItem).buildUnchecked());
      
      TestItem store1End = startItem.withVersion(store1VersionEnd);
      store.upsertInternal(TEST_ITEMS, store1End);
      
      VersionedData result = store.getInternal(TEST_ITEMS, startItem.key);
      assertEquals(store1VersionEnd, result.getVersion());
    } finally {
      store2.close();
    }
  }
  
  @Test
  public void handlesUpsertRaceConditionAgainstExternalClientWithHigherVersion() throws Exception {
    final T store2 = makeStore();
    
    int startVersion = 1;
    final int store2Version = 3;
    int store1VersionEnd = 2;
    
    final TestItem startItem = new TestItem("me", "foo", startVersion);
    
    Runnable concurrentModifier = new Runnable() {
      public void run() {
        store2.upsertInternal(TEST_ITEMS, startItem.withVersion(store2Version));
      }
    };
    
    try {
      assumeTrue(setUpdateHook(store, concurrentModifier));
      
      store.initInternal(new DataBuilder().add(TEST_ITEMS, startItem).buildUnchecked());
      
      TestItem store1End = startItem.withVersion(store1VersionEnd);
      store.upsertInternal(TEST_ITEMS, store1End);
      
      VersionedData result = store.getInternal(TEST_ITEMS, startItem.key);
      assertEquals(store2Version, result.getVersion());
    } finally {
      store2.close();
    }
  }
  
  @Test
  public void storesWithDifferentPrefixAreIndependent() throws Exception {
    T store1 = makeStoreWithPrefix("aaa");
    Assume.assumeNotNull(store1);
    T store2 = makeStoreWithPrefix("bbb");
    clearAllData();
    
    try {
      assertFalse(store1.initializedInternal());
      assertFalse(store2.initializedInternal());
      
      TestItem item1a = new TestItem("a1", "flag-a", 1);
      TestItem item1b = new TestItem("b", "flag-b", 1);
      TestItem item2a = new TestItem("a2", "flag-a", 2);
      TestItem item2c = new TestItem("c", "flag-c", 2);
      
      store1.initInternal(new DataBuilder().add(TEST_ITEMS, item1a, item1b).buildUnchecked());
      assertTrue(store1.initializedInternal());
      assertFalse(store2.initializedInternal());
      
      store2.initInternal(new DataBuilder().add(TEST_ITEMS, item2a, item2c).buildUnchecked());
      assertTrue(store1.initializedInternal());
      assertTrue(store2.initializedInternal());
      
      Map<String, VersionedData> items1 = store1.getAllInternal(TEST_ITEMS);
      Map<String, VersionedData> items2 = store2.getAllInternal(TEST_ITEMS);
      assertEquals(2, items1.size());
      assertEquals(2, items2.size());
      assertEquals(item1a, items1.get(item1a.key));
      assertEquals(item1b, items1.get(item1b.key));
      assertEquals(item2a, items2.get(item2a.key));
      assertEquals(item2c, items2.get(item2c.key));
      
      assertEquals(item1a, store1.getInternal(TEST_ITEMS, item1a.key));
      assertEquals(item1b, store1.getInternal(TEST_ITEMS, item1b.key));
      assertEquals(item2a, store2.getInternal(TEST_ITEMS, item2a.key));
      assertEquals(item2c, store2.getInternal(TEST_ITEMS, item2c.key));
    } finally {
      store1.close();
      store2.close();
    }
  }
}
