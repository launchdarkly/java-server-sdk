package com.launchdarkly.sdk.server.interfaces;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.SerializedItemDescriptor;
import com.launchdarkly.testhelpers.TypeBehavior;

import org.junit.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

@SuppressWarnings("javadoc")
public class DataStoreTypesTest {
  @Test
  public void dataKindProperties() {
    Function<ItemDescriptor, String> serializer = item -> "version=" + item.getVersion();
    Function<String, ItemDescriptor> deserializer = s -> new ItemDescriptor(0, s);
    
    DataKind k = new DataKind("foo", serializer, deserializer);
    
    assertThat(k.getName(), equalTo("foo"));
    assertThat(k.serialize(new ItemDescriptor(9, null)), equalTo("version=9"));
    assertThat(k.deserialize("x"), equalTo(new ItemDescriptor(0, "x")));
    
    assertThat(k.toString(), equalTo("DataKind(foo)"));
  }
  
  @Test
  public void itemDescriptorProperties() {
    Object o = new Object();
    ItemDescriptor i1 = new ItemDescriptor(1, o);
    assertThat(i1.getVersion(), equalTo(1));
    assertThat(i1.getItem(), sameInstance(o));
    
    ItemDescriptor i2 = ItemDescriptor.deletedItem(2);
    assertThat(i2.getVersion(), equalTo(2));
    assertThat(i2.getItem(), nullValue());
  }
  
  @Test
  public void itemDescriptorEquality() {
    List<TypeBehavior.ValueFactory<ItemDescriptor>> allPermutations = new ArrayList<>();
    for (int version: new int[] { 1, 2 }) {
      for (Object item: new Object[] { null, "a", "b" }) {
        allPermutations.add(() -> new ItemDescriptor(version, item));
      }
    }
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
  
  @Test
  public void itemDescriptorStringRepresentation() {
    assertThat(new ItemDescriptor(1, "a").toString(), equalTo("ItemDescriptor(1,a)"));
    assertThat(new ItemDescriptor(2, null).toString(), equalTo("ItemDescriptor(2,null)"));
  }
  
  @Test
  public void serializedItemDescriptorProperties() {
    SerializedItemDescriptor si1 = new SerializedItemDescriptor(1, false, "x");
    assertThat(si1.getVersion(), equalTo(1));
    assertThat(si1.isDeleted(), equalTo(false));
    assertThat(si1.getSerializedItem(), equalTo("x"));
    
    SerializedItemDescriptor si2 = new SerializedItemDescriptor(2, true, null);
    assertThat(si2.getVersion(), equalTo(2));
    assertThat(si2.isDeleted(), equalTo(true));
    assertThat(si2.getSerializedItem(), nullValue());
  }
  
  @Test
  public void serializedItemDescriptorEquality() {
    List<TypeBehavior.ValueFactory<SerializedItemDescriptor>> allPermutations = new ArrayList<>();
    for (int version: new int[] { 1, 2 }) {
      for (boolean deleted: new boolean[] { true, false }) {
        for (String item: new String[] { null, "a", "b" }) {
          allPermutations.add(() -> new SerializedItemDescriptor(version, deleted, item));
        }
      }
    }
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
  
  @Test
  public void serializedItemDescriptorStringRepresentation() {
    assertThat(new SerializedItemDescriptor(1, false, "a").toString(), equalTo("SerializedItemDescriptor(1,false,a)"));
    assertThat(new SerializedItemDescriptor(2, true, null).toString(), equalTo("SerializedItemDescriptor(2,true,null)"));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void keyedItemsProperties() {
    ItemDescriptor item1 = new ItemDescriptor(1, "a");
    ItemDescriptor item2 = new ItemDescriptor(2, "b");
    
    KeyedItems<ItemDescriptor> items = new KeyedItems<>(ImmutableSortedMap.of("key1", item1, "key2", item2).entrySet());
    
    assertThat(items.getItems(), contains(
        new AbstractMap.SimpleEntry<>("key1", item1),
        new AbstractMap.SimpleEntry<>("key2", item2)
        ));
    
    KeyedItems<ItemDescriptor> emptyItems = new KeyedItems<>(null);
    
    assertThat(emptyItems.getItems(), emptyIterable());
  }
  
  @Test
  public void keyedItemsEquality() {
    List<TypeBehavior.ValueFactory<KeyedItems<ItemDescriptor>>> allPermutations = new ArrayList<>();
    for (String key: new String[] { "key1", "key2"}) {
      for (int version: new int[] { 1, 2 }) {
        for (String data: new String[] { null, "a", "b" }) {
          allPermutations.add(() -> new KeyedItems<>(ImmutableMap.of(key, new ItemDescriptor(version, data)).entrySet()));
        }
      }
    }
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void fullDataSetProperties() {
    ItemDescriptor item1 = new ItemDescriptor(1, "a");
    KeyedItems<ItemDescriptor> items = new KeyedItems<>(ImmutableMap.of("key1", item1).entrySet());
    FullDataSet<ItemDescriptor> data = new FullDataSet<>(ImmutableMap.of(DataModel.FEATURES, items).entrySet());
    
    assertThat(data.getData(), contains(
        new AbstractMap.SimpleEntry<>(DataModel.FEATURES, items)
        ));
    
    FullDataSet<ItemDescriptor> emptyData = new FullDataSet<>(null);
    
    assertThat(emptyData.getData(), emptyIterable());
  }
  
  @Test
  public void fullDataSetEquality() {
    List<TypeBehavior.ValueFactory<FullDataSet<ItemDescriptor>>> allPermutations = new ArrayList<>();
    for (DataKind kind: new DataKind[] { DataModel.FEATURES, DataModel.SEGMENTS }) {
      for (int version: new int[] { 1, 2 }) {
        allPermutations.add(() -> new FullDataSet<>(
            ImmutableMap.of(kind,
                new KeyedItems<>(ImmutableMap.of("key", new ItemDescriptor(version, "a")).entrySet())
            ).entrySet()));
      }
    }
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
}
