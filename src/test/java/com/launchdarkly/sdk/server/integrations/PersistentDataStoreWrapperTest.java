package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.DataStoreTestTypes.TestItem;
import com.launchdarkly.sdk.server.integrations.CacheMonitor;
import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.sdk.server.integrations.PersistentDataStoreWrapper;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.SerializedItemDescriptor;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.launchdarkly.sdk.server.DataStoreTestTypes.TEST_ITEMS;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toDataMap;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toItemsMap;
import static com.launchdarkly.sdk.server.DataStoreTestTypes.toSerialized;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class PersistentDataStoreWrapperTest {

  private final RuntimeException FAKE_ERROR = new RuntimeException("fake error");
  
  private final TestMode testMode;
  private final MockCore core;
  private final PersistentDataStoreWrapper wrapper;
  
  static class TestMode {
    final boolean cached;
    final boolean cachedIndefinitely;
    final boolean persistOnlyAsString;
    
    TestMode(boolean cached, boolean cachedIndefinitely, boolean persistOnlyAsString) {
      this.cached = cached;
      this.cachedIndefinitely = cachedIndefinitely;
      this.persistOnlyAsString = persistOnlyAsString;
    }
    
    boolean isCached() {
      return cached;
    }

    boolean isCachedWithFiniteTtl() {
      return cached && !cachedIndefinitely;
    }

    boolean isCachedIndefinitely() {
      return cached && cachedIndefinitely;
    }
    
    Duration getCacheTtl() {
      return cached ? (cachedIndefinitely ? Duration.ofMillis(-1) : Duration.ofSeconds(30)) : Duration.ZERO;
    }
    
    @Override
    public String toString() {
      return "TestMode(" +
          (cached ? (cachedIndefinitely ? "CachedIndefinitely" : "Cached") : "Uncached") +
          (persistOnlyAsString ? ",persistOnlyAsString" : "") + ")";
    }
  }
  
  @Parameters(name="cached={0}")
  public static Iterable<TestMode> data() {
    return ImmutableList.of(
        new TestMode(true, false, false),
        new TestMode(true, false, true),
        new TestMode(true, true, false),
        new TestMode(true, true, true),
        new TestMode(false, false, false),
        new TestMode(false, false, true)
        );
  }
  
  public PersistentDataStoreWrapperTest(TestMode testMode) {
    this.testMode = testMode;
    this.core = new MockCore();
    this.core.persistOnlyAsString = testMode.persistOnlyAsString;
    this.wrapper = new PersistentDataStoreWrapper(core, testMode.getCacheTtl(),
        PersistentDataStoreBuilder.StaleValuesPolicy.EVICT, null);
  }
  
  @Test
  public void get() {
    TestItem itemv1 = new TestItem("key", 1);
    TestItem itemv2 = itemv1.withVersion(2);
    
    core.forceSet(TEST_ITEMS, itemv1);
    assertThat(wrapper.get(TEST_ITEMS, itemv1.key), equalTo(itemv1.toItemDescriptor()));
    
    core.forceSet(TEST_ITEMS, itemv2);
    
    // if cached, we will not see the new underlying value yet
    ItemDescriptor result = wrapper.get(TEST_ITEMS, itemv1.key);
    ItemDescriptor expected = (testMode.isCached() ? itemv1 : itemv2).toItemDescriptor();
    assertThat(result, equalTo(expected));
  }
  
  @Test
  public void getDeletedItem() {
    String key = "key";

    core.forceSet(TEST_ITEMS, key, toSerialized(TEST_ITEMS, ItemDescriptor.deletedItem(1)));
    assertThat(wrapper.get(TEST_ITEMS, key), equalTo(ItemDescriptor.deletedItem(1)));

    TestItem itemv2 = new TestItem(key, 2);
    core.forceSet(TEST_ITEMS, itemv2);
    
    // if cached, we will not see the new underlying value yet
    ItemDescriptor result = wrapper.get(TEST_ITEMS, key);
    ItemDescriptor expected = testMode.isCached() ? ItemDescriptor.deletedItem(1) : itemv2.toItemDescriptor();
    assertThat(result, equalTo(expected));
  }

  @Test
  public void getMissingItem() {
    String key = "key";
    
    assertThat(wrapper.get(TEST_ITEMS, key), nullValue());

    TestItem item = new TestItem(key, 1);
    core.forceSet(TEST_ITEMS, item);
    
    // if cached, the cache can retain a null result
    ItemDescriptor result = wrapper.get(TEST_ITEMS, item.key);
    assertThat(result, testMode.isCached() ? nullValue(ItemDescriptor.class) : equalTo(item.toItemDescriptor()));
  }
  
  @Test
  public void cachedGetUsesValuesFromInit() {
    assumeThat(testMode.isCached(), is(true));
    
    TestItem item1 = new TestItem("key1", 1);
    TestItem item2 = new TestItem("key2", 1);
    wrapper.init(new DataBuilder().add(TEST_ITEMS, item1, item2).build());
    
    core.forceRemove(TEST_ITEMS, item1.key);
    
    assertThat(wrapper.get(TEST_ITEMS, item1.key), equalTo(item1.toItemDescriptor()));
  }
  
  @Test
  public void getAll() {
    TestItem item1 = new TestItem("key1", 1);
    TestItem item2 = new TestItem("key2", 1);

    core.forceSet(TEST_ITEMS, item1);
    core.forceSet(TEST_ITEMS, item2);
    Map<String, ItemDescriptor> items = toItemsMap(wrapper.getAll(TEST_ITEMS));
    Map<String, ItemDescriptor> expected = ImmutableMap.<String, ItemDescriptor>of(
        item1.key, item1.toItemDescriptor(), item2.key, item2.toItemDescriptor());
    assertThat(items, equalTo(expected));
    
    core.forceRemove(TEST_ITEMS, item2.key);
    items = toItemsMap(wrapper.getAll(TEST_ITEMS));
    if (testMode.isCached()) {
      assertThat(items, equalTo(expected));
    } else {
      Map<String, ItemDescriptor> expected1 = ImmutableMap.<String, ItemDescriptor>of(item1.key, item1.toItemDescriptor());
      assertThat(items, equalTo(expected1));
    }
  }

  @Test
  public void getAllDoesNotRemoveDeletedItems() {
    String key1 = "key1", key2 = "key2";    
    TestItem item1 = new TestItem(key1, 1);
    
    core.forceSet(TEST_ITEMS, item1);
    core.forceSet(TEST_ITEMS, key2, toSerialized(TEST_ITEMS, ItemDescriptor.deletedItem(1)));
    Map<String, ItemDescriptor> items = toItemsMap(wrapper.getAll(TEST_ITEMS));
    Map<String, ItemDescriptor> expected = ImmutableMap.<String, ItemDescriptor>of(
        key1, item1.toItemDescriptor(), key2, ItemDescriptor.deletedItem(1));
    assertThat(items, equalTo(expected));
  }
  
  @Test
  public void cachedAllUsesValuesFromInit() {
    assumeThat(testMode.isCached(), is(true));
    
    TestItem item1 = new TestItem("key1", 1);
    TestItem item2 = new TestItem("key2", 1);
    FullDataSet<ItemDescriptor> allData = new DataBuilder().add(TEST_ITEMS, item1, item2).build();
    wrapper.init(allData);

    core.forceRemove(TEST_ITEMS, item2.key);
    
    Map<String, ItemDescriptor> items = toItemsMap(wrapper.getAll(TEST_ITEMS));
    Map<String, ItemDescriptor> expected = toDataMap(allData).get(TEST_ITEMS);
    assertThat(items, equalTo(expected));
  }

  @Test
  public void cachedStoreWithFiniteTtlDoesNotUpdateCacheIfCoreInitFails() {
    assumeThat(testMode.isCachedWithFiniteTtl(), is(true));
    
    TestItem item = new TestItem("key", 1);
    
    core.fakeError = FAKE_ERROR;
    try {
      wrapper.init(new DataBuilder().add(TEST_ITEMS, item).build());
      fail("expected exception");
    } catch(RuntimeException e) {
      assertThat(e, is(FAKE_ERROR));
    }
    
    core.fakeError = null;
    assertThat(toItemsMap(wrapper.getAll(TEST_ITEMS)).size(), equalTo(0));
  }

  @Test
  public void cachedStoreWithInfiniteTtlUpdatesCacheEvenIfCoreInitFails() {
    assumeThat(testMode.isCachedIndefinitely(), is(true));
    
    TestItem item = new TestItem("key", 1);
    
    core.fakeError = FAKE_ERROR;
    try {
      wrapper.init(new DataBuilder().add(TEST_ITEMS, item).build());
      fail("expected exception");
    } catch(RuntimeException e) {
      assertThat(e, is(FAKE_ERROR));
    }
    
    core.fakeError = null;
    Map<String, ItemDescriptor> expected = ImmutableMap.<String, ItemDescriptor>of(item.key, item.toItemDescriptor());
    assertThat(toItemsMap(wrapper.getAll(TEST_ITEMS)), equalTo(expected));
  }

  @Test
  public void upsertSuccessful() {
    TestItem itemv1 = new TestItem("key", 1);
    TestItem itemv2 = itemv1.withVersion(2);
    
    wrapper.upsert(TEST_ITEMS, itemv1.key, itemv1.toItemDescriptor());
    assertThat(core.data.get(TEST_ITEMS).get(itemv1.key), equalTo(itemv1.toSerializedItemDescriptor()));
    
    wrapper.upsert(TEST_ITEMS, itemv1.key, itemv2.toItemDescriptor());
    assertThat(core.data.get(TEST_ITEMS).get(itemv1.key), equalTo(itemv2.toSerializedItemDescriptor()));
    
    // if we have a cache, verify that the new item is now cached by writing a different value
    // to the underlying data - Get should still return the cached item
    if (testMode.isCached()) {
      TestItem itemv3 = itemv1.withVersion(3);
      core.forceSet(TEST_ITEMS, itemv3);
    }
    
    assertThat(wrapper.get(TEST_ITEMS, itemv1.key), equalTo(itemv2.toItemDescriptor()));
  }
  
  @Test
  public void cachedUpsertUnsuccessful() {
    assumeThat(testMode.isCached(), is(true));
    
    // This is for an upsert where the data in the store has a higher version. In an uncached
    // store, this is just a no-op as far as the wrapper is concerned so there's nothing to
    // test here. In a cached store, we need to verify that the cache has been refreshed
    // using the data that was found in the store.
    TestItem itemv1 = new TestItem("key", 1);
    TestItem itemv2 = itemv1.withVersion(2);
    
    wrapper.upsert(TEST_ITEMS, itemv1.key, itemv2.toItemDescriptor());
    assertThat(core.data.get(TEST_ITEMS).get(itemv2.key), equalTo(itemv2.toSerializedItemDescriptor()));
    
    boolean success = wrapper.upsert(TEST_ITEMS, itemv1.key, itemv1.toItemDescriptor());
    assertThat(success, is(false));
    assertThat(core.data.get(TEST_ITEMS).get(itemv1.key), equalTo(itemv2.toSerializedItemDescriptor())); // value in store remains the same
    
    TestItem itemv3 = itemv1.withVersion(3);
    core.forceSet(TEST_ITEMS, itemv3); // bypasses cache so we can verify that itemv2 is in the cache
    
    assertThat(wrapper.get(TEST_ITEMS, itemv1.key), equalTo(itemv2.toItemDescriptor()));
  }
  
  @Test
  public void cachedStoreWithFiniteTtlDoesNotUpdateCacheIfCoreUpdateFails() {
    assumeThat(testMode.isCachedWithFiniteTtl(), is(true));
    
    TestItem itemv1 = new TestItem("key", 1);
    TestItem itemv2 = itemv1.withVersion(2);
    
    wrapper.init(new DataBuilder().add(TEST_ITEMS, itemv1).build());

    core.fakeError = FAKE_ERROR;
    try {
      wrapper.upsert(TEST_ITEMS, itemv1.key, itemv2.toItemDescriptor());
      fail("expected exception");
    } catch(RuntimeException e) {
      assertThat(e, is(FAKE_ERROR));
    }
    core.fakeError = null;
    
    // cache still has old item, same as underlying store
    assertThat(wrapper.get(TEST_ITEMS, itemv1.key), equalTo(itemv1.toItemDescriptor()));
  }
  
  @Test
  public void cachedStoreWithInfiniteTtlUpdatesCacheEvenIfCoreUpdateFails() {
    assumeThat(testMode.isCachedIndefinitely(), is(true));
    
    TestItem itemv1 = new TestItem("key", 1);
    TestItem itemv2 = itemv1.withVersion(2);
    
    wrapper.init(new DataBuilder().add(TEST_ITEMS, itemv1).build());

    core.fakeError = FAKE_ERROR;
    try {
      wrapper.upsert(TEST_ITEMS, itemv1.key, itemv2.toItemDescriptor());
      Assert.fail("expected exception");
    } catch(RuntimeException e) {
      assertThat(e, is(FAKE_ERROR));
    }
    core.fakeError = null;
    
    // underlying store has old item but cache has new item
    assertThat(wrapper.get(TEST_ITEMS, itemv1.key), equalTo(itemv2.toItemDescriptor()));
  }

  @Test
  public void cachedStoreWithFiniteTtlRemovesCachedAllDataIfOneItemIsUpdated() {
    assumeThat(testMode.isCachedWithFiniteTtl(), is(true));
    
    TestItem item1v1 = new TestItem("key1", 1);
    TestItem item1v2 = item1v1.withVersion(2);
    TestItem item2v1 = new TestItem("key2", 1);
    TestItem item2v2 = item2v1.withVersion(2);

    wrapper.init(new DataBuilder().add(TEST_ITEMS, item1v1, item2v1).build());
    wrapper.getAll(TEST_ITEMS); // now the All data is cached
    
    // do an upsert for item1 - this should drop the previous all() data from the cache
    wrapper.upsert(TEST_ITEMS, item1v1.key, item1v2.toItemDescriptor());

    // modify item2 directly in the underlying data
    core.forceSet(TEST_ITEMS, item2v2);

    // now, all() should reread the underlying data so we see both changes
    Map<String, ItemDescriptor> expected = ImmutableMap.<String, ItemDescriptor>of(
        item1v1.key, item1v2.toItemDescriptor(), item2v1.key, item2v2.toItemDescriptor());
    assertThat(toItemsMap(wrapper.getAll(TEST_ITEMS)), equalTo(expected));
  }

  @Test
  public void cachedStoreWithInfiniteTtlUpdatesCachedAllDataIfOneItemIsUpdated() {
    assumeThat(testMode.isCachedIndefinitely(), is(true));
    
    TestItem item1v1 = new TestItem("key1", 1);
    TestItem item1v2 = item1v1.withVersion(2);
    TestItem item2v1 = new TestItem("key2", 1);
    TestItem item2v2 = item2v1.withVersion(2);

    wrapper.init(new DataBuilder().add(TEST_ITEMS, item1v1, item2v1).build());
    wrapper.getAll(TEST_ITEMS); // now the All data is cached
    
    // do an upsert for item1 - this should update the underlying data *and* the cached all() data
    wrapper.upsert(TEST_ITEMS, item1v1.key, item1v2.toItemDescriptor());

    // modify item2 directly in the underlying data
    core.forceSet(TEST_ITEMS, item2v2);

    // now, all() should *not* reread the underlying data - we should only see the change to item1
    Map<String, ItemDescriptor> expected = ImmutableMap.<String, ItemDescriptor>of(
        item1v1.key, item1v2.toItemDescriptor(), item2v1.key, item2v1.toItemDescriptor());
    assertThat(toItemsMap(wrapper.getAll(TEST_ITEMS)), equalTo(expected));
  }

  @Test
  public void delete() {
    TestItem itemv1 = new TestItem("key", 1);
    
    core.forceSet(TEST_ITEMS, itemv1);
    assertThat(wrapper.get(TEST_ITEMS, itemv1.key), equalTo(itemv1.toItemDescriptor()));
    
    ItemDescriptor deletedItem = ItemDescriptor.deletedItem(2);
    wrapper.upsert(TEST_ITEMS, itemv1.key, deletedItem);

    // some stores will persist a special placeholder string, others will store the metadata separately
    SerializedItemDescriptor serializedDeletedItem = testMode.persistOnlyAsString ?
          toSerialized(TEST_ITEMS, ItemDescriptor.deletedItem(deletedItem.getVersion())) :
          new SerializedItemDescriptor(deletedItem.getVersion(), true, null);
    assertThat(core.data.get(TEST_ITEMS).get(itemv1.key), equalTo(serializedDeletedItem));
    
    // make a change that bypasses the cache
    TestItem itemv3 = itemv1.withVersion(3);
    core.forceSet(TEST_ITEMS, itemv3);
    
    ItemDescriptor result = wrapper.get(TEST_ITEMS, itemv1.key);
    assertThat(result, equalTo(testMode.isCached() ? deletedItem : itemv3.toItemDescriptor()));
  }
  
  @Test
  public void initializedCallsInternalMethodOnlyIfNotAlreadyInited() {
    assumeThat(testMode.isCached(), is(false));
    
    assertThat(wrapper.isInitialized(), is(false));
    assertThat(core.initedQueryCount, equalTo(1));
    
    core.inited = true;
    assertThat(wrapper.isInitialized(), is(true));
    assertThat(core.initedQueryCount, equalTo(2));
    
    core.inited = false;
    assertThat(wrapper.isInitialized(), is(true));
    assertThat(core.initedQueryCount, equalTo(2));
  }
  
  @Test
  public void initializedDoesNotCallInternalMethodAfterInitHasBeenCalled() {
    assumeThat(testMode.isCached(), is(false));
    
    assertThat(wrapper.isInitialized(), is(false));
    assertThat(core.initedQueryCount, equalTo(1));
    
    wrapper.init(new DataBuilder().build());
    
    assertThat(wrapper.isInitialized(), is(true));
    assertThat(core.initedQueryCount, equalTo(1));
  }
  
  @Test
  public void initializedCanCacheFalseResult() throws Exception {
    assumeThat(testMode.isCached(), is(true));
    
    // We need to create a different object for this test so we can set a short cache TTL
    try (PersistentDataStoreWrapper wrapper1 = new PersistentDataStoreWrapper(core,
        Duration.ofMillis(500), PersistentDataStoreBuilder.StaleValuesPolicy.EVICT, null)) {
      assertThat(wrapper1.isInitialized(), is(false));
      assertThat(core.initedQueryCount, equalTo(1));
      
      core.inited = true;
      assertThat(core.initedQueryCount, equalTo(1));
      
      Thread.sleep(600);
      
      assertThat(wrapper1.isInitialized(), is(true));
      assertThat(core.initedQueryCount, equalTo(2));

      // From this point on it should remain true and the method should not be called
      assertThat(wrapper1.isInitialized(), is(true));
      assertThat(core.initedQueryCount, equalTo(2));
    }
  }
  
  @Test
  public void canGetCacheStats() throws Exception {
    assumeThat(testMode.isCachedWithFiniteTtl(), is(true));
    
    CacheMonitor cacheMonitor = new CacheMonitor();
    
    try (PersistentDataStoreWrapper w = new PersistentDataStoreWrapper(core,
        Duration.ofSeconds(30), PersistentDataStoreBuilder.StaleValuesPolicy.EVICT, cacheMonitor)) {
      CacheMonitor.CacheStats stats = cacheMonitor.getCacheStats();
      
      assertThat(stats, equalTo(new CacheMonitor.CacheStats(0, 0, 0, 0, 0, 0)));
      
      // Cause a cache miss
      w.get(TEST_ITEMS, "key1");
      stats = cacheMonitor.getCacheStats();
      assertThat(stats.getHitCount(), equalTo(0L));
      assertThat(stats.getMissCount(), equalTo(1L));
      assertThat(stats.getLoadSuccessCount(), equalTo(1L)); // even though it's a miss, it's a "success" because there was no exception
      assertThat(stats.getLoadExceptionCount(), equalTo(0L));
      
      // Cause a cache hit
      core.forceSet(TEST_ITEMS, new TestItem("key2", 1));
      w.get(TEST_ITEMS, "key2"); // this one is a cache miss, but causes the item to be loaded and cached
      w.get(TEST_ITEMS, "key2"); // now it's a cache hit
      stats = cacheMonitor.getCacheStats();
      assertThat(stats.getHitCount(), equalTo(1L));
      assertThat(stats.getMissCount(), equalTo(2L));
      assertThat(stats.getLoadSuccessCount(), equalTo(2L));
      assertThat(stats.getLoadExceptionCount(), equalTo(0L));
      
      // Cause a load exception
      core.fakeError = new RuntimeException("sorry");
      try {
        w.get(TEST_ITEMS, "key3"); // cache miss -> tries to load the item -> gets an exception
        fail("expected exception");
      } catch (RuntimeException e) {
        assertThat(e.getCause(), is((Throwable)core.fakeError));
      }
      stats = cacheMonitor.getCacheStats();
      assertThat(stats.getHitCount(), equalTo(1L));
      assertThat(stats.getMissCount(), equalTo(3L));
      assertThat(stats.getLoadSuccessCount(), equalTo(2L));
      assertThat(stats.getLoadExceptionCount(), equalTo(1L));
    }
  }
  
  static class MockCore implements PersistentDataStore {
    Map<DataKind, Map<String, SerializedItemDescriptor>> data = new HashMap<>();
    boolean inited;
    int initedQueryCount;
    boolean persistOnlyAsString;
    RuntimeException fakeError;
    
    @Override
    public void close() throws IOException {
    }

    @Override
    public SerializedItemDescriptor get(DataKind kind, String key) {
      maybeThrow();
      if (data.containsKey(kind)) {
        SerializedItemDescriptor item = data.get(kind).get(key);
        if (item != null) {
          if (persistOnlyAsString) {
            // This simulates the kind of store implementation that can't track metadata separately  
            return new SerializedItemDescriptor(0, false, item.getSerializedItem());
          } else {
            return item;
          }
        }
      }
      return null;
    }

    @Override
    public KeyedItems<SerializedItemDescriptor> getAll(DataKind kind) {
      maybeThrow();
      return data.containsKey(kind) ? new KeyedItems<>(ImmutableList.copyOf(data.get(kind).entrySet())) : new KeyedItems<>(null);
    }

    @Override
    public void init(FullDataSet<SerializedItemDescriptor> allData) {
      maybeThrow();
      data.clear();
      for (Map.Entry<DataKind, KeyedItems<SerializedItemDescriptor>> entry: allData.getData()) {
        DataKind kind = entry.getKey();
        HashMap<String, SerializedItemDescriptor> items = new LinkedHashMap<>();
        for (Map.Entry<String, SerializedItemDescriptor> e: entry.getValue().getItems()) {
          items.put(e.getKey(), storableItem(kind, e.getValue()));
        }
        data.put(kind, items);
      }
      inited = true;
    }

    @Override
    public boolean upsert(DataKind kind, String key, SerializedItemDescriptor item) {
      maybeThrow();
      if (!data.containsKey(kind)) {
        data.put(kind, new HashMap<>());
      }
      Map<String, SerializedItemDescriptor> items = data.get(kind);
      SerializedItemDescriptor oldItem = items.get(key);
      if (oldItem != null && oldItem.getVersion() >= item.getVersion()) {
        return false;
      }
      items.put(key, storableItem(kind, item));
      return true;
    }

    @Override
    public boolean isInitialized() {
      maybeThrow();
      initedQueryCount++;
      return inited;
    }
    
    public void forceSet(DataKind kind, TestItem item) {
      forceSet(kind, item.key, item.toSerializedItemDescriptor());
    }

    public void forceSet(DataKind kind, String key, SerializedItemDescriptor item) {
      if (!data.containsKey(kind)) {
        data.put(kind, new HashMap<>());
      }
      Map<String, SerializedItemDescriptor> items = data.get(kind);
      items.put(key, storableItem(kind, item));
    }

    public void forceRemove(DataKind kind, String key) {
      if (data.containsKey(kind)) {
        data.get(kind).remove(key);
      }
    }
    
    private SerializedItemDescriptor storableItem(DataKind kind, SerializedItemDescriptor item) {
      if (item.isDeleted() && !persistOnlyAsString) {
        // This simulates the kind of store implementation that *can* track metadata separately, so we don't
        // have to persist the placeholder string for deleted items
        return new SerializedItemDescriptor(item.getVersion(), true, null);
      }
      return item;
    }
    
    private void maybeThrow() {
      if (fakeError != null) {
        throw fakeError;
      }
    }
  }
}
