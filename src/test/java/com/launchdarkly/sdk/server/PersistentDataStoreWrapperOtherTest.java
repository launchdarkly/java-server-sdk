package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.DataStoreTestTypes.TestItem;
import com.launchdarkly.sdk.server.integrations.MockPersistentDataStore;
import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder.StaleValuesPolicy;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.testhelpers.TypeBehavior;

import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.launchdarkly.sdk.server.DataStoreTestTypes.TEST_ITEMS;
import static com.launchdarkly.sdk.server.TestComponents.sharedExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

/**
 * These tests are for PersistentDataStoreWrapper functionality that doesn't fit into the parameterized
 * PersistentDataStoreWrapperTest suite.
 */
@SuppressWarnings("javadoc")
public class PersistentDataStoreWrapperOtherTest extends BaseTest {
  private static final RuntimeException FAKE_ERROR = new RuntimeException("fake error");
  
  private final MockPersistentDataStore core;
  
  public PersistentDataStoreWrapperOtherTest() {
    this.core = new MockPersistentDataStore();
  }

  private PersistentDataStoreWrapper makeWrapper(Duration cacheTtl, StaleValuesPolicy policy) {
    return new PersistentDataStoreWrapper(
        core,
        cacheTtl,
        policy,
        false,
        status -> {},
        sharedExecutor,
        testLogger
        );
  }
  
  @Test
  public void cacheKeyEquality() {
    List<TypeBehavior.ValueFactory<PersistentDataStoreWrapper.CacheKey>> allPermutations = new ArrayList<>();
    for (DataKind kind: new DataKind[] { DataModel.FEATURES, DataModel.SEGMENTS }) {
      for (String key: new String[] { "a", "b" }) {
        allPermutations.add(() -> PersistentDataStoreWrapper.CacheKey.forItem(kind, key));
      }
    }
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
  
  @Test
  public void cacheInRefreshModeRefreshesExpiredItem() throws Exception {
    try (PersistentDataStoreWrapper wrapper = makeWrapper(Duration.ofMillis(20), StaleValuesPolicy.REFRESH)) {
      TestItem itemv1 = new TestItem("key", 1);
      TestItem itemv2 = new TestItem(itemv1.key, 2);
      core.forceSet(TEST_ITEMS, itemv1);
      
      assertEquals(0, core.getQueryCount);
      
      ItemDescriptor result1 = wrapper.get(TEST_ITEMS, itemv1.key);
      assertThat(result1, equalTo(itemv1.toItemDescriptor()));
      assertEquals(1, core.getQueryCount);
      
      // item is now in the cache
      // change the item in the underlying store
      core.forceSet(TEST_ITEMS, itemv2);

      // wait for the cached item to expire
      Thread.sleep(50);
      
      // it has not yet tried to requery the store, because we didn't use ASYNC_REFRESH
      assertEquals(1, core.getQueryCount);
      
      // try to get it again - it refreshes the cache with the new data
      ItemDescriptor result2 = wrapper.get(TEST_ITEMS, itemv1.key);
      assertThat(result2, equalTo(itemv2.toItemDescriptor()));
    }
  }
  
  @Test
  public void cacheInRefreshModeKeepsExpiredItemInCacheIfRefreshFails() throws Exception {
    try (PersistentDataStoreWrapper wrapper = makeWrapper(Duration.ofMillis(20), StaleValuesPolicy.REFRESH)) {
      TestItem item = new TestItem("key", 1);
      core.forceSet(TEST_ITEMS, item);
      
      assertEquals(0, core.getQueryCount);

      ItemDescriptor result1 = wrapper.get(TEST_ITEMS, item.key);
      assertThat(result1, equalTo(item.toItemDescriptor()));
      assertEquals(1, core.getQueryCount);
      
      // item is now in the cache
      // now make it so the core will return an error if get() is called
      core.fakeError = FAKE_ERROR;
      
      // wait for the cached item to expire
      Thread.sleep(50);

      // it has not yet tried to requery the store, because we didn't use REFRESH_ASYNC
      assertEquals(1, core.getQueryCount);
      
      // try to get it again - the query fails, but in REFRESH mode it swallows the error and keeps the old cached value
      ItemDescriptor result2 = wrapper.get(TEST_ITEMS, item.key);
      assertThat(result2, equalTo(item.toItemDescriptor()));
    }
  }
}
