package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.SerializedItemDescriptor;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.google.common.collect.Iterables.transform;
import static com.launchdarkly.sdk.server.TestUtil.TEST_GSON_INSTANCE;

@SuppressWarnings("javadoc")
public class DataStoreTestTypes {
  public static <T> Map<DataKind, Map<String, T>> toDataMap(FullDataSet<T> data) {
    return ImmutableMap.copyOf(transform(data.getData(), e -> new AbstractMap.SimpleEntry<>(e.getKey(), toItemsMap(e.getValue()))));
  }
  
  public static <T> Map<String, T> toItemsMap(KeyedItems<T> data) {
    return ImmutableMap.copyOf(data.getItems());
  }
  
  public static SerializedItemDescriptor toSerialized(DataKind kind, ItemDescriptor item) {
    boolean isDeleted = item.getItem() == null;
    return new SerializedItemDescriptor(item.getVersion(), isDeleted, kind.serialize(item));
  }
  
  public static class TestItem implements VersionedData {
    public final String key;
    public final String name;
    public final int version;
    public final boolean deleted;

    public TestItem(String key, String name, int version, boolean deleted) {
      this.key = key;
      this.name = name;
      this.version = version;
      this.deleted = deleted;
    }
    
    public TestItem(String key, String name, int version) {
      this(key, name, version, false);
    }

    public TestItem(String key, int version) {
      this(key, "", version);
    }
    
    @Override
    public String getKey() {
      return key;
    }

    @Override
    public int getVersion() {
      return version;
    }
    
    public boolean isDeleted() {
      return deleted;
    }

    public TestItem withName(String newName) {
      return new TestItem(key, newName, version);
    }
    
    public TestItem withVersion(int newVersion) {
      return new TestItem(key, name, newVersion);
    }
    
    public ItemDescriptor toItemDescriptor() {
      return new ItemDescriptor(version, this);
    }

    public Map.Entry<String, ItemDescriptor> toKeyedItemDescriptor() {
      return new AbstractMap.SimpleEntry<>(key, toItemDescriptor());
    }
    
    public SerializedItemDescriptor toSerializedItemDescriptor() {
      return toSerialized(TEST_ITEMS, toItemDescriptor());
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof TestItem) {
        TestItem o = (TestItem)other;
        return Objects.equals(name, o.name) &&
            Objects.equals(key, o.key) &&
            version == o.version;
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(name, key, version);
    }
    
    @Override
    public String toString() {
      return "TestItem(" + name + "," + key + "," + version + ")";
    }
  }
  
  public static final DataKind TEST_ITEMS = new DataKind("test-items",
      DataStoreTestTypes::serializeTestItem,
      DataStoreTestTypes::deserializeTestItem);

  public static final DataKind OTHER_TEST_ITEMS = new DataKind("other-test-items",
      DataStoreTestTypes::serializeTestItem,
      DataStoreTestTypes::deserializeTestItem);

  private static String serializeTestItem(ItemDescriptor item) {
    if (item.getItem() == null) {
      return "DELETED:" + item.getVersion();
    }
    return TEST_GSON_INSTANCE.toJson(item.getItem());
  }
  
  private static ItemDescriptor deserializeTestItem(String s) {
    if (s.startsWith("DELETED:")) {
      return ItemDescriptor.deletedItem(Integer.parseInt(s.substring(8)));
    }
    TestItem ti = TEST_GSON_INSTANCE.fromJson(s, TestItem.class);
    return new ItemDescriptor(ti.version, ti);
  }
  
  public static class DataBuilder {
    private Map<DataKind, Map<String, ItemDescriptor>> data = new HashMap<>();
    
    public static DataBuilder forStandardTypes() {
      // This just ensures that we use realistic-looking data sets in our tests when simulating
      // an LD service response, which will always include "flags" and "segments" even if empty.
      DataBuilder ret = new DataBuilder();
      ret.add(DataModel.FEATURES);
      ret.add(DataModel.SEGMENTS);
      return ret;
    }
    
    public DataBuilder add(DataKind kind, TestItem... items) {
      return addAny(kind, items);
    }

    // This is defined separately because test code that's outside of this package can't see DataModel.VersionedData
    public DataBuilder addAny(DataKind kind, VersionedData... items) {
      Map<String, ItemDescriptor> itemsMap = data.get(kind);
      if (itemsMap == null) {
        itemsMap = new LinkedHashMap<>(); // use LinkedHashMap to preserve insertion order
        data.put(kind, itemsMap);
      }
      for (VersionedData item: items) {
        itemsMap.put(item.getKey(), new ItemDescriptor(item.getVersion(), item));
      }
      return this;
    }
    
    public DataBuilder remove(DataKind kind, String key) {
      if (data.get(kind) != null) {
        data.get(kind).remove(key);
      }
      return this;
    }
    
    public FullDataSet<ItemDescriptor> build() {
      return new FullDataSet<>(
          ImmutableMap.copyOf(
              Maps.transformValues(data, itemsMap ->
                new KeyedItems<>(ImmutableList.copyOf(itemsMap.entrySet()))
              )).entrySet()
          );
    }
    
    public FullDataSet<SerializedItemDescriptor> buildSerialized() {
      return new FullDataSet<>(
          ImmutableMap.copyOf(
              Maps.transformEntries(data, (kind, itemsMap) ->
                new KeyedItems<>(
                    ImmutableMap.copyOf(
                        Maps.transformValues(itemsMap, item -> DataStoreTestTypes.toSerialized(kind, item))
                    ).entrySet()
                    )
                )
          ).entrySet());
    }
    
    public LDValue buildJson() {
      FullDataSet<SerializedItemDescriptor> allData = buildSerialized();
      ObjectBuilder allBuilder = LDValue.buildObject();
      for (Map.Entry<DataKind, KeyedItems<SerializedItemDescriptor>> coll: allData.getData()) {
        String namespace = coll.getKey().getName().equals("features") ? "flags" : coll.getKey().getName();
        ObjectBuilder itemsBuilder = LDValue.buildObject();
        for (Map.Entry<String, SerializedItemDescriptor> item: coll.getValue().getItems()) {
          itemsBuilder.put(item.getKey(), LDValue.parse(item.getValue().getSerializedItem()));
        }
        allBuilder.put(namespace, itemsBuilder.build());
      }
      return allBuilder.build();
    }
  }
}
