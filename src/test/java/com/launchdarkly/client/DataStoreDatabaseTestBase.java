package com.launchdarkly.client;

import com.launchdarkly.client.DataStoreTestTypes.TestItem;
import com.launchdarkly.client.TestUtil.DataBuilder;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.VersionedData;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Map;

import static com.launchdarkly.client.DataStoreTestTypes.TEST_ITEMS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Extends DataStoreTestBase with tests for data stores where multiple store instances can
 * use the same underlying data store (i.e. database implementations in general).
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public abstract class DataStoreDatabaseTestBase extends DataStoreTestBase {

  @Parameters(name="cached={0}")
  public static Iterable<Boolean> data() {
    return Arrays.asList(new Boolean[] { false, true });
  }
  
  public DataStoreDatabaseTestBase(boolean cached) {
    super(cached);
  }
  
  /**
   * Test subclasses should override this method if the data store class supports a key prefix option
   * for keeping data sets distinct within the same database.
   */
  protected DataStore makeStoreWithPrefix(String prefix) {
    return null;
  }

  /**
   * Test classes should override this to return false if the data store class does not have a local
   * caching option (e.g. the in-memory store).
   * @return
   */
  protected boolean isCachingSupported() {
    return true;
  }
  
  /**
   * Test classes should override this to clear all data from the underlying database, if it is
   * possible for data to exist there before the data store is created (i.e. if
   * isUnderlyingDataSharedByAllInstances() returns true).
   */
  protected void clearAllData() {
  }
  
  /**
   * Test classes should override this (and return true) if it is possible to instrument the feature
   * store to execute the specified Runnable during an upsert operation, for concurrent modification tests.
   */
  protected boolean setUpdateHook(DataStore storeUnderTest, Runnable hook) {
    return false;
  }
  
  @Before
  public void setup() {
    assumeTrue(isCachingSupported() || !cached);
    super.setup();
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
  public void oneInstanceCanDetectIfAnotherInstanceHasInitializedTheStore() {
    assumeFalse(cached); // caching would cause the inited state to only be detected after the cache has expired
    
    clearAllData();
    DataStore store2 = makeStore();
    
    assertFalse(store.initialized());
    
    store2.init(new DataBuilder().add(TEST_ITEMS, item1).build());
    
    assertTrue(store.initialized());
  }

  @Test
  public void oneInstanceCanDetectIfAnotherInstanceHasInitializedTheStoreEvenIfEmpty() {
    assumeFalse(cached); // caching would cause the inited state to only be detected after the cache has expired
    
    clearAllData();
    DataStore store2 = makeStore();
    
    assertFalse(store.initialized());
    
    store2.init(new DataBuilder().build());
    
    assertTrue(store.initialized());
  }
  
  // The following two tests verify that the update version checking logic works correctly when
  // another client instance is modifying the same data. They will run only if the test class
  // supports setUpdateHook().
  
  @Test
  public void handlesUpsertRaceConditionAgainstExternalClientWithLowerVersion() throws Exception {
    final DataStore store2 = makeStore();
    
    int startVersion = 1;
    final int store2VersionStart = 2;
    final int store2VersionEnd = 4;
    int store1VersionEnd = 10;
    
    final TestItem startItem = new TestItem("me", "foo", startVersion);
    
    Runnable concurrentModifier = new Runnable() {
      int versionCounter = store2VersionStart;
      public void run() {
        if (versionCounter <= store2VersionEnd) {
          store2.upsert(TEST_ITEMS, startItem.withVersion(versionCounter));
          versionCounter++;
        }
      }
    };
    
    try {
      assumeTrue(setUpdateHook(store, concurrentModifier));
      
      store.init(new DataBuilder().add(TEST_ITEMS, startItem).build());
      
      TestItem store1End = startItem.withVersion(store1VersionEnd);
      store.upsert(TEST_ITEMS, store1End);
      
      VersionedData result = store.get(TEST_ITEMS, startItem.key);
      assertEquals(store1VersionEnd, result.getVersion());
    } finally {
      store2.close();
    }
  }
  
  @Test
  public void handlesUpsertRaceConditionAgainstExternalClientWithHigherVersion() throws Exception {
    final DataStore store2 = makeStore();
    
    int startVersion = 1;
    final int store2Version = 3;
    int store1VersionEnd = 2;
    
    final TestItem startItem = new TestItem("me", "foo", startVersion);
    
    Runnable concurrentModifier = new Runnable() {
      public void run() {
        store2.upsert(TEST_ITEMS, startItem.withVersion(store2Version));
      }
    };
    
    try {
      assumeTrue(setUpdateHook(store, concurrentModifier));
      
      store.init(new DataBuilder().add(TEST_ITEMS, startItem).build());
      
      TestItem store1End = startItem.withVersion(store1VersionEnd);
      store.upsert(TEST_ITEMS, store1End);
      
      VersionedData result = store.get(TEST_ITEMS, startItem.key);
      assertEquals(store2Version, result.getVersion());
    } finally {
      store2.close();
    }
  }
  
  @Test
  public void storesWithDifferentPrefixAreIndependent() throws Exception {
    DataStore store1 = makeStoreWithPrefix("aaa");
    Assume.assumeNotNull(store1);
    DataStore store2 = makeStoreWithPrefix("bbb");
    clearAllData();
    
    try {
      assertFalse(store1.initialized());
      assertFalse(store2.initialized());
      
      TestItem item1a = new TestItem("a1", "flag-a", 1);
      TestItem item1b = new TestItem("b", "flag-b", 1);
      TestItem item2a = new TestItem("a2", "flag-a", 2);
      TestItem item2c = new TestItem("c", "flag-c", 2);
      
      store1.init(new DataBuilder().add(TEST_ITEMS, item1a, item1b).build());
      assertTrue(store1.initialized());
      assertFalse(store2.initialized());
      
      store2.init(new DataBuilder().add(TEST_ITEMS, item2a, item2c).build());
      assertTrue(store1.initialized());
      assertTrue(store2.initialized());
      
      Map<String, TestItem> items1 = store1.all(TEST_ITEMS);
      Map<String, TestItem> items2 = store2.all(TEST_ITEMS);
      assertEquals(2, items1.size());
      assertEquals(2, items2.size());
      assertEquals(item1a, items1.get(item1a.key));
      assertEquals(item1b, items1.get(item1b.key));
      assertEquals(item2a, items2.get(item2a.key));
      assertEquals(item2c, items2.get(item2c.key));
      
      assertEquals(item1a, store1.get(TEST_ITEMS, item1a.key));
      assertEquals(item1b, store1.get(TEST_ITEMS, item1b.key));
      assertEquals(item2a, store2.get(TEST_ITEMS, item2a.key));
      assertEquals(item2c, store2.get(TEST_ITEMS, item2c.key));
    } finally {
      store1.close();
      store2.close();
    }
  }
}
