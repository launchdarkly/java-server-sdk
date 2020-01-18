package com.launchdarkly.client;

import com.launchdarkly.client.TestUtil.DataBuilder;
import com.launchdarkly.client.interfaces.DataStore;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Map;

import static com.launchdarkly.client.DataModel.DataKinds.FEATURES;
import static com.launchdarkly.client.ModelBuilders.flagBuilder;
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
    
    store2.init(new DataBuilder().add(FEATURES, feature1).build());
    
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
    
    final DataModel.FeatureFlag flag1 = flagBuilder("foo").version(startVersion).build();
    
    Runnable concurrentModifier = new Runnable() {
      int versionCounter = store2VersionStart;
      public void run() {
        if (versionCounter <= store2VersionEnd) {
          DataModel.FeatureFlag f = flagBuilder(flag1).version(versionCounter).build();
          store2.upsert(FEATURES, f);
          versionCounter++;
        }
      }
    };
    
    try {
      assumeTrue(setUpdateHook(store, concurrentModifier));
      
      store.init(new DataBuilder().add(FEATURES, flag1).build());
      
      DataModel.FeatureFlag store1End = flagBuilder(flag1).version(store1VersionEnd).build();
      store.upsert(FEATURES, store1End);
      
      DataModel.FeatureFlag result = store.get(FEATURES, flag1.getKey());
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
    
    final DataModel.FeatureFlag flag1 = flagBuilder("foo").version(startVersion).build();
    
    Runnable concurrentModifier = () -> {
      DataModel.FeatureFlag f = flagBuilder(flag1).version(store2Version).build();
      store2.upsert(FEATURES, f);
    };
    
    try {
      assumeTrue(setUpdateHook(store, concurrentModifier));
      
      store.init(new DataBuilder().add(FEATURES, flag1).build());
      
      DataModel.FeatureFlag store1End = flagBuilder(flag1).version(store1VersionEnd).build();
      store.upsert(FEATURES, store1End);
      
      DataModel.FeatureFlag result = store.get(FEATURES, flag1.getKey());
      assertEquals(store2Version, result.getVersion());
    } finally {
      store2.close();
    }
  }
  
  @Test
  public void storesWithDifferentPrefixAreIndependent() throws Exception {
    assumeFalse(cached);
    
    DataStore store1 = makeStoreWithPrefix("aaa");
    Assume.assumeNotNull(store1);
    DataStore store2 = makeStoreWithPrefix("bbb");
    clearAllData();
    
    try {
      assertFalse(store1.initialized());
      assertFalse(store2.initialized());
      
      DataModel.FeatureFlag flag1a = flagBuilder("flag-a").version(1).build();
      DataModel.FeatureFlag flag1b = flagBuilder("flag-b").version(1).build();
      DataModel.FeatureFlag flag2a = flagBuilder("flag-a").version(2).build();
      DataModel.FeatureFlag flag2c = flagBuilder("flag-c").version(2).build();
      
      store1.init(new DataBuilder().add(FEATURES, flag1a, flag1b).build());
      assertTrue(store1.initialized());
      assertFalse(store2.initialized());
      
      store2.init(new DataBuilder().add(FEATURES, flag2a, flag2c).build());
      assertTrue(store1.initialized());
      assertTrue(store2.initialized());
      
      Map<String, DataModel.FeatureFlag> items1 = store1.all(FEATURES);
      Map<String, DataModel.FeatureFlag> items2 = store2.all(FEATURES);
      assertEquals(2, items1.size());
      assertEquals(2, items2.size());
      assertEquals(flag1a.getVersion(), items1.get(flag1a.getKey()).getVersion());
      assertEquals(flag1b.getVersion(), items1.get(flag1b.getKey()).getVersion());
      assertEquals(flag2a.getVersion(), items2.get(flag2a.getKey()).getVersion());
      assertEquals(flag2c.getVersion(), items2.get(flag2c.getKey()).getVersion());
      
      assertEquals(flag1a.getVersion(), store1.get(FEATURES, flag1a.getKey()).getVersion());
      assertEquals(flag1b.getVersion(), store1.get(FEATURES, flag1b.getKey()).getVersion());
      assertEquals(flag2a.getVersion(), store2.get(FEATURES, flag2a.getKey()).getVersion());
      assertEquals(flag2c.getVersion(), store2.get(FEATURES, flag2c.getKey()).getVersion());
    } finally {
      store1.close();
      store2.close();
    }
  }
}
