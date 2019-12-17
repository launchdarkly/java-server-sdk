package com.launchdarkly.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements a dependency graph ordering for data to be stored in a feature store. We must use this
 * on every data set that will be passed to {@link com.launchdarkly.client.interfaces.FeatureStore#init(Map)}.
 *
 * @since 4.6.1
 */
abstract class FeatureStoreDataSetSorter {
  /**
   * Returns a copy of the input map that has the following guarantees: the iteration order of the outer
   * map will be in ascending order by {@link VersionedDataKind#getPriority()}; and for each data kind
   * that returns true for {@link VersionedDataKind#isDependencyOrdered()}, the inner map will have an
   * iteration order where B is before A if A has a dependency on B.
   * 
   * @param allData the unordered data set
   * @return a map with a defined ordering
   */
  public static Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> sortAllCollections(
      Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    ImmutableSortedMap.Builder<VersionedDataKind<?>, Map<String, ? extends VersionedData>> builder =
        ImmutableSortedMap.orderedBy(dataKindPriorityOrder);
    for (Map.Entry<VersionedDataKind<?>, Map<String, ? extends VersionedData>> entry: allData.entrySet()) {
      VersionedDataKind<?> kind = entry.getKey();
      builder.put(kind, sortCollection(kind, entry.getValue()));
    }
    return builder.build();
  }
  
  private static Map<String, ? extends VersionedData> sortCollection(VersionedDataKind<?> kind, Map<String, ? extends VersionedData> input) {
    if (!kind.isDependencyOrdered() || input.isEmpty()) {
      return input;
    }
    
    Map<String, VersionedData> remainingItems = new HashMap<>(input);
    ImmutableMap.Builder<String, VersionedData> builder = ImmutableMap.builder();
    // Note, ImmutableMap guarantees that the iteration order will be the same as the builder insertion order
    
    while (!remainingItems.isEmpty()) {
      // pick a random item that hasn't been updated yet
      for (Map.Entry<String, VersionedData> entry: remainingItems.entrySet()) {
        addWithDependenciesFirst(kind, entry.getValue(), remainingItems, builder);
        break;
      }
    }
    
    return builder.build();
  }
  
  private static void addWithDependenciesFirst(VersionedDataKind<?> kind,
      VersionedData item,
      Map<String, VersionedData> remainingItems,
      ImmutableMap.Builder<String, VersionedData> builder) {
    remainingItems.remove(item.getKey());  // we won't need to visit this item again
    for (String prereqKey: kind.getDependencyKeys(item)) {
      VersionedData prereqItem = remainingItems.get(prereqKey);
      if (prereqItem != null) {
        addWithDependenciesFirst(kind, prereqItem, remainingItems, builder);
      }
    }
    builder.put(item.getKey(), item);
  }
  
  private static Comparator<VersionedDataKind<?>> dataKindPriorityOrder = new Comparator<VersionedDataKind<?>>() {
    @Override
    public int compare(VersionedDataKind<?> o1, VersionedDataKind<?> o2) {
      return o1.getPriority() - o2.getPriority();
    }
  };
}
