package com.launchdarkly.client;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.launchdarkly.client.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.client.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.client.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.client.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.client.interfaces.DataStoreTypes.SerializedItemDescriptor;
import com.launchdarkly.client.interfaces.VersionedData;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.collect.Iterables.transform;

@SuppressWarnings("javadoc")
public class DataStoreTestTypes {
  public static <T> Map<DataKind, Map<String, T>> toDataMap(FullDataSet<T> data) {
    return ImmutableMap.copyOf(transform(data.getData(), e -> new AbstractMap.SimpleEntry<>(e.getKey(), toItemsMap(e.getValue()))));
  }
  
  public static <T> Map<String, T> toItemsMap(KeyedItems<T> data) {
    return ImmutableMap.copyOf(data.getItems());
  }
  
  public static SerializedItemDescriptor toSerialized(DataKind kind, ItemDescriptor item) {
    return item.getItem() == null ? SerializedItemDescriptor.deletedItem(item.getVersion()) :
      new SerializedItemDescriptor(item.getVersion(), kind.serialize(item.getItem()));
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
    
    public SerializedItemDescriptor toSerializedItemDescriptor() {
      return new SerializedItemDescriptor(version, TEST_ITEMS.serialize(this));
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof TestItem) {
        TestItem o = (TestItem)other;
        return Objects.equal(name, o.name) &&
            Objects.equal(key, o.key) &&
            version == o.version;
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hashCode(name, key, version);
    }
    
    @Override
    public String toString() {
      return "TestItem(" + name + "," + key + "," + version + ")";
    }
  }
  
  public static final DataKind TEST_ITEMS = new DataKind("test-items",
      DataStoreTestTypes::serializeTestItem,
      DataStoreTestTypes::deserializeTestItem,
      DataStoreTestTypes::serializeDeletedItemPlaceholder);

  public static final DataKind OTHER_TEST_ITEMS = new DataKind("other-test-items",
      DataStoreTestTypes::serializeTestItem,
      DataStoreTestTypes::deserializeTestItem,
      DataStoreTestTypes::serializeDeletedItemPlaceholder);

  private static String serializeTestItem(Object o) {
    return JsonHelpers.gsonInstance().toJson(o);
  }
  
  private static ItemDescriptor deserializeTestItem(String s) {
    if (s.startsWith("DELETED:")) {
      return ItemDescriptor.deletedItem(Integer.parseInt(s.substring(8)));
    }
    TestItem ti = JsonHelpers.gsonInstance().fromJson(s, TestItem.class);
    return new ItemDescriptor(ti.version, ti);
  }
  
  private static String serializeDeletedItemPlaceholder(int version) {
    return "DELETED:" + version;
  }

  public static class DataBuilder {
    private Map<DataKind, Map<String, ItemDescriptor>> data = new HashMap<>();
    
    public DataBuilder add(DataKind kind, VersionedData... items) {
      Map<String, ItemDescriptor> itemsMap = data.get(kind);
      if (itemsMap == null) {
        itemsMap = new HashMap<>();
        data.put(kind, itemsMap);
      }
      for (VersionedData item: items) {
        itemsMap.put(item.getKey(), new ItemDescriptor(item.getVersion(), item));
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
  }
}
