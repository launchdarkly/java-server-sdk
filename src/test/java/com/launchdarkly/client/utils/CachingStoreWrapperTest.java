package com.launchdarkly.client.utils;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.client.VersionedData;
import com.launchdarkly.client.VersionedDataKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeThat;

@RunWith(Parameterized.class)
public class CachingStoreWrapperTest {

  private final boolean cached;
  private final MockCore core;
  private final CachingStoreWrapper wrapper;
  
  @Parameters(name="cached={0}")
  public static Iterable<Boolean> data() {
    return Arrays.asList(new Boolean[] { false, true });
  }
  
  public CachingStoreWrapperTest(boolean cached) {
    this.cached = cached;
    this.core = new MockCore();
    this.wrapper = new CachingStoreWrapper(core, cached ? 30 : 0, TimeUnit.SECONDS, false, false);
  }
  
  @Test
  public void get() {
    MockItem itemv1 = new MockItem("flag", 1, false);
    MockItem itemv2 = new MockItem("flag", 2, false);
    
    core.forceSet(THINGS, itemv1);
    assertThat(wrapper.get(THINGS, itemv1.key), equalTo(itemv1));
    
    core.forceSet(THINGS, itemv2);
    MockItem result = wrapper.get(THINGS, itemv1.key);
    assertThat(result, equalTo(cached ? itemv1 : itemv2)); // if cached, we will not see the new underlying value yet
  }
  
  @Test
  public void getDeletedItem() {
    MockItem itemv1 = new MockItem("flag", 1, true);
    MockItem itemv2 = new MockItem("flag", 2, false);
    
    core.forceSet(THINGS, itemv1);
    assertThat(wrapper.get(THINGS, itemv1.key), nullValue()); // item is filtered out because deleted is true
    
    core.forceSet(THINGS, itemv2);
    MockItem result = wrapper.get(THINGS, itemv1.key);
    assertThat(result, cached ? nullValue(MockItem.class) : equalTo(itemv2)); // if cached, we will not see the new underlying value yet
  }

  @Test
  public void getMissingItem() {
    MockItem item = new MockItem("flag", 1, false);
    
    assertThat(wrapper.get(THINGS, item.getKey()), nullValue());
    
    core.forceSet(THINGS, item);
    MockItem result = wrapper.get(THINGS, item.key);
    assertThat(result, cached ? nullValue(MockItem.class) : equalTo(item)); // the cache can retain a null result
  }
  
  @Test
  public void cachedGetUsesValuesFromInit() {
    if (!cached) {
      return;
    }
    
    MockItem item1 = new MockItem("flag1", 1, false);
    MockItem item2 = new MockItem("flag2", 1, false);
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData = makeData(item1, item2);
    wrapper.init(allData);
    
    assertThat(core.data, equalTo(allData));
  }
  
  @Test
  public void getAll() {
    MockItem item1 = new MockItem("flag1", 1, false);
    MockItem item2 = new MockItem("flag2", 1, false);
    
    core.forceSet(THINGS, item1);
    core.forceSet(THINGS, item2);
    Map<String, MockItem> items = wrapper.all(THINGS);
    Map<String, MockItem> expected = ImmutableMap.<String, MockItem>of(item1.key, item1, item2.key, item2);
    assertThat(items, equalTo(expected));
    
    core.forceRemove(THINGS, item2.key);
    items = wrapper.all(THINGS);
    if (cached) {
      assertThat(items, equalTo(expected));
    } else {
      Map<String, MockItem> expected1 = ImmutableMap.<String, MockItem>of(item1.key, item1);
      assertThat(items, equalTo(expected1));
    }
  }
  
  @Test
  public void cachedAllUsesValuesFromInit() {
    if (!cached) {
      return;
    }
    
    MockItem item1 = new MockItem("flag1", 1, false);
    MockItem item2 = new MockItem("flag2", 1, false);
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData = makeData(item1, item2);
    wrapper.init(allData);

    core.forceRemove(THINGS, item2.key);
    
    Map<String, MockItem> items = wrapper.all(THINGS);
    Map<String, MockItem> expected = ImmutableMap.<String, MockItem>of(item1.key, item1, item2.key, item2);
    assertThat(items, equalTo(expected));
  }
  
  @Test
  public void cachedAllUsesFreshValuesIfThereHasBeenAnUpdate() {
    if (!cached) {
      return;
    }
    
    MockItem item1 = new MockItem("flag1", 1, false);
    MockItem item1v2 = new MockItem("flag1", 2, false);
    MockItem item2 = new MockItem("flag2", 1, false);
    MockItem item2v2 = new MockItem("flag2", 2, false);
    
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData = makeData(item1, item2);
    wrapper.init(allData);

    // make a change to item1 via the wrapper - this should flush the cache
    wrapper.upsert(THINGS, item1v2);
    
    // make a change to item2 that bypasses the cache
    core.forceSet(THINGS, item2v2);
    
    // we should now see both changes since the cache was flushed
    Map<String, MockItem> items = wrapper.all(THINGS);
    Map<String, MockItem> expected = ImmutableMap.<String, MockItem>of(item1.key, item1v2, item2.key, item2v2);
    assertThat(items, equalTo(expected));
  }
  
  @Test
  public void upsertSuccessful() {
    MockItem itemv1 = new MockItem("flag", 1, false);
    MockItem itemv2 = new MockItem("flag", 2, false);
    
    wrapper.upsert(THINGS, itemv1);
    assertThat((MockItem)core.data.get(THINGS).get(itemv1.key), equalTo(itemv1));
    
    wrapper.upsert(THINGS, itemv2);
    assertThat((MockItem)core.data.get(THINGS).get(itemv1.key), equalTo(itemv2));
    
    // if we have a cache, verify that the new item is now cached by writing a different value
    // to the underlying data - Get should still return the cached item
    if (cached) {
      MockItem item1v3 = new MockItem("flag", 3, false);
      core.forceSet(THINGS, item1v3);
    }
    
    assertThat(wrapper.get(THINGS, itemv1.key), equalTo(itemv2));
  }
  
  @Test
  public void cachedUpsertUnsuccessful() {
    if (!cached) {
      return;
    }
    
    // This is for an upsert where the data in the store has a higher version. In an uncached
    // store, this is just a no-op as far as the wrapper is concerned so there's nothing to
    // test here. In a cached store, we need to verify that the cache has been refreshed
    // using the data that was found in the store.
    MockItem itemv1 = new MockItem("flag", 1, false);
    MockItem itemv2 = new MockItem("flag", 2, false);
    
    wrapper.upsert(THINGS, itemv2);
    assertThat((MockItem)core.data.get(THINGS).get(itemv2.key), equalTo(itemv2));
    
    wrapper.upsert(THINGS, itemv1);
    assertThat((MockItem)core.data.get(THINGS).get(itemv1.key), equalTo(itemv2)); // value in store remains the same
    
    MockItem itemv3 = new MockItem("flag", 3, false);
    core.forceSet(THINGS, itemv3); // bypasses cache so we can verify that itemv2 is in the cache
    
    assertThat(wrapper.get(THINGS, itemv1.key), equalTo(itemv2));
  }
  
  @Test
  public void delete() {
    MockItem itemv1 = new MockItem("flag", 1, false);
    MockItem itemv2 = new MockItem("flag", 2, true);
    MockItem itemv3 = new MockItem("flag", 3, false);
    
    core.forceSet(THINGS, itemv1);
    MockItem item = wrapper.get(THINGS, itemv1.key);
    assertThat(item, equalTo(itemv1));
    
    wrapper.delete(THINGS, itemv1.key, 2);
    assertThat((MockItem)core.data.get(THINGS).get(itemv1.key), equalTo(itemv2));
    
    // make a change that bypasses the cache
    core.forceSet(THINGS, itemv3);
    
    MockItem result = wrapper.get(THINGS, itemv1.key);
    assertThat(result, cached ? nullValue(MockItem.class) : equalTo(itemv3));
  }
  
  @Test
  public void initializedCallsInternalMethodOnlyIfNotAlreadyInited() {
    assumeThat(cached, is(false));
    
    assertThat(wrapper.initialized(), is(false));
    assertThat(core.initedQueryCount, equalTo(1));
    
    core.inited = true;
    assertThat(wrapper.initialized(), is(true));
    assertThat(core.initedQueryCount, equalTo(2));
    
    core.inited = false;
    assertThat(wrapper.initialized(), is(true));
    assertThat(core.initedQueryCount, equalTo(2));
  }
  
  @Test
  public void initializedDoesNotCallInternalMethodAfterInitHasBeenCalled() {
    assumeThat(cached, is(false));
    
    assertThat(wrapper.initialized(), is(false));
    assertThat(core.initedQueryCount, equalTo(1));
    
    wrapper.init(makeData());
    
    assertThat(wrapper.initialized(), is(true));
    assertThat(core.initedQueryCount, equalTo(1));
  }
  
  @Test
  public void initializedCanCacheFalseResult() throws Exception {
    assumeThat(cached, is(true));
    
    // We need to create a different object for this test so we can set a short cache TTL
    try (CachingStoreWrapper wrapper1 = new CachingStoreWrapper(core, 500, TimeUnit.MILLISECONDS, false, false)) {
      assertThat(wrapper1.initialized(), is(false));
      assertThat(core.initedQueryCount, equalTo(1));
      
      core.inited = true;
      assertThat(core.initedQueryCount, equalTo(1));
      
      Thread.sleep(600);
      
      assertThat(wrapper1.initialized(), is(true));
      assertThat(core.initedQueryCount, equalTo(2));

      // From this point on it should remain true and the method should not be called
      assertThat(wrapper1.initialized(), is(true));
      assertThat(core.initedQueryCount, equalTo(2));
    }
  }
  
  private Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> makeData(MockItem... items) {
    Map<String, VersionedData> innerMap = new HashMap<>();
    for (MockItem item: items) {
      innerMap.put(item.getKey(), item);
    }
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> outerMap = new HashMap<>();
    outerMap.put(THINGS, innerMap);
    return outerMap;
  }
  
  static class MockCore implements FeatureStoreCore {
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> data = new HashMap<>();
    boolean inited;
    int initedQueryCount;
    
    @Override
    public void close() throws IOException {
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends VersionedData> T getInternal(VersionedDataKind<T> kind, String key) {
      if (data.containsKey(kind)) {
        return (T)data.get(kind).get(key);
      }
      return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends VersionedData> Map<String, T> getAllInternal(VersionedDataKind<T> kind) {
      return (Map<String, T>)data.get(kind);
    }

    @Override
    public void initInternal(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
      data = new HashMap<>();
      for (Map.Entry<VersionedDataKind<?>, Map<String, ? extends VersionedData>> e: allData.entrySet()) {
        data.put(e.getKey(), new HashMap<>(e.getValue()));
      }
      inited = true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends VersionedData> T upsertInternal(VersionedDataKind<T> kind, T item) {
      if (!data.containsKey(kind)) {
        data.put(kind, new HashMap<String, VersionedData>());
      }
      HashMap<String, VersionedData> items = (HashMap<String, VersionedData>)data.get(kind);
      T oldItem = (T)items.get(item.getKey());
      if (oldItem != null && oldItem.getVersion() >= item.getVersion()) {
        return oldItem;
      }
      items.put(item.getKey(), item);
      return item;
    }

    @Override
    public boolean initializedInternal() {
      initedQueryCount++;
      return inited;
    }
    
    public <T extends VersionedData> void forceSet(VersionedDataKind<?> kind, T item) {
      if (!data.containsKey(kind)) {
        data.put(kind, new HashMap<String, VersionedData>());
      }
      @SuppressWarnings("unchecked")
      HashMap<String, VersionedData> items = (HashMap<String, VersionedData>)data.get(kind);
      items.put(item.getKey(), item);
    }
    
    public void forceRemove(VersionedDataKind<?> kind, String key) {
      if (data.containsKey(kind)) {
        data.get(kind).remove(key);
      }
    }
  }
  
  static class MockItem implements VersionedData {
    private final String key;
    private final int version;
    private final boolean deleted;
    
    public MockItem(String key, int version, boolean deleted) {
      this.key = key;
      this.version = version;
      this.deleted = deleted;
    }
    
    public String getKey() {
      return key;
    }
    
    public int getVersion() {
      return version;
    }
    
    public boolean isDeleted() {
      return deleted;
    }
    
    @Override
    public String toString() {
      return "[" + key + ", " + version + ", " + deleted + "]";
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof MockItem) {
        MockItem o = (MockItem)other;
        return key.equals(o.key) && version == o.version && deleted == o.deleted;
      }
      return false;
    }
  }
  
  static VersionedDataKind<MockItem> THINGS = new VersionedDataKind<MockItem>() {
    public String getNamespace() {
      return "things";
    }
    
    public Class<MockItem> getItemClass() {
      return MockItem.class;
    }
    
    public String getStreamApiPath() {
      return "/things/";
    }
    
    public MockItem makeDeletedItem(String key, int version) {
      return new MockItem(key, version, true);
    }
  };
}
