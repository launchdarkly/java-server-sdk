package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.launchdarkly.client.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.client.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.client.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.client.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.client.interfaces.VersionedDataKind;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Iterables.transform;

/**
 * Implements a dependency graph ordering for data to be stored in a data store. We must use this
 * on every data set that will be passed to {@link com.launchdarkly.client.interfaces.DataStore#init(Map)}.
 *
 * @since 4.6.1
 */
abstract class DataStoreDataSetSorter {
  /**
   * Returns a copy of the input map that has the following guarantees: the iteration order of the outer
   * map will be in ascending order by {@link VersionedDataKind#getPriority()}; and for each data kind
   * that returns true for {@link VersionedDataKind#isDependencyOrdered()}, the inner map will have an
   * iteration order where B is before A if A has a dependency on B.
   * 
   * @param allData the unordered data set
   * @return a map with a defined ordering
   */
  public static FullDataSet<ItemDescriptor> sortAllCollections(FullDataSet<ItemDescriptor> allData) {
    ImmutableSortedMap.Builder<DataKind, KeyedItems<ItemDescriptor>> builder =
        ImmutableSortedMap.orderedBy(dataKindPriorityOrder);
    for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> entry: allData.getData()) {
      DataKind kind = entry.getKey();
      builder.put(kind, sortCollection(kind, entry.getValue()));
    }
    return new FullDataSet<>(builder.build().entrySet());
  }
  
  private static KeyedItems<ItemDescriptor> sortCollection(DataKind kind, KeyedItems<ItemDescriptor> input) {
    if (!isDependencyOrdered(kind) || isEmpty(input.getItems())) {
      return input;
    }
    
    Map<String, ItemDescriptor> remainingItems = new HashMap<>();
    for (Map.Entry<String, ItemDescriptor> e: input.getItems()) {
      remainingItems.put(e.getKey(), e.getValue());
    }
    ImmutableMap.Builder<String, ItemDescriptor> builder = ImmutableMap.builder();
    // Note, ImmutableMap guarantees that the iteration order will be the same as the builder insertion order
    
    while (!remainingItems.isEmpty()) {
      // pick a random item that hasn't been updated yet
      for (Map.Entry<String, ItemDescriptor> entry: remainingItems.entrySet()) {
        addWithDependenciesFirst(kind, entry.getKey(), entry.getValue(), remainingItems, builder);
        break;
      }
    }
    
    return new KeyedItems<>(builder.build().entrySet());
  }
  
  private static void addWithDependenciesFirst(DataKind kind,
      String key,
      ItemDescriptor item,
      Map<String, ItemDescriptor> remainingItems,
      ImmutableMap.Builder<String, ItemDescriptor> builder) {
    remainingItems.remove(key);  // we won't need to visit this item again
    for (String prereqKey: getDependencyKeys(kind, item.getItem())) {
      ItemDescriptor prereqItem = remainingItems.get(prereqKey);
      if (prereqItem != null) {
        addWithDependenciesFirst(kind, prereqKey, prereqItem, remainingItems, builder);
      }
    }
    builder.put(key, item);
  }

  private static boolean isDependencyOrdered(DataKind kind) {
    return kind == DataModel.DataKinds.FEATURES;
  }
  
  private static Iterable<String> getDependencyKeys(DataKind kind, Object item) {
    if (item == null) {
      return null;
    }
    if (kind == DataModel.DataKinds.FEATURES) {
      DataModel.FeatureFlag flag = (DataModel.FeatureFlag)item;
      if (flag.getPrerequisites() == null || flag.getPrerequisites().isEmpty()) {
        return ImmutableList.of();
      }
      return transform(flag.getPrerequisites(), p -> p.getKey());
    }
    return null;
  }
  
  private static int getPriority(DataKind kind) {
    if (kind == DataModel.DataKinds.FEATURES) {
      return 1;
    } else if (kind == DataModel.DataKinds.SEGMENTS) {
      return 0;
    } else {
      return kind.getName().length() + 2; 
    }
  }
  
  private static Comparator<DataKind> dataKindPriorityOrder = new Comparator<DataKind>() {
    @Override
    public int compare(DataKind o1, DataKind o2) {
      return getPriority(o1) - getPriority(o2);
    }
  };
}
