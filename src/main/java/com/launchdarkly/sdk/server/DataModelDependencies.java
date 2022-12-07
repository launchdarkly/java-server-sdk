package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Iterables.transform;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * Implements a dependency graph ordering for data to be stored in a data store.
 * <p>
 * We use this to order the data that we pass to {@link com.launchdarkly.sdk.server.subsystems.DataStore#init(FullDataSet)},
 * and also to determine which flags are affected by a change if the application is listening for flag change events.
 * <p>
 * Dependencies are defined as follows: there is a dependency from flag F to flag G if F is a prerequisite flag for
 * G, or transitively for any of G's prerequisites; there is a dependency from flag F to segment S if F contains a
 * rule with a segmentMatch clause that uses S. Therefore, if G or S is modified or deleted then F may be affected,
 * and if we must populate the store non-atomically then G and S should be added before F. 
 *
 * @since 4.6.1
 */
abstract class DataModelDependencies {
  private DataModelDependencies() {}
  
  static class KindAndKey {
    final DataKind kind;
    final String key;
    
    public KindAndKey(DataKind kind, String key) {
      this.kind = kind;
      this.key = key;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof KindAndKey) {
        KindAndKey o = (KindAndKey)other;
        return kind == o.kind && key.equals(o.key); 
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return kind.hashCode() * 31 + key.hashCode();
    }
  }
  
  /**
   * Returns the immediate dependencies from the given item.
   * 
   * @param fromKind the item's kind
   * @param fromItem the item descriptor
   * @return the flags and/or segments that this item depends on
   */
  public static Set<KindAndKey> computeDependenciesFrom(DataKind fromKind, ItemDescriptor fromItem) {
    if (fromItem == null || fromItem.getItem() == null) {
      return emptySet();
    }
    if (fromKind == FEATURES) {
      DataModel.FeatureFlag flag = (DataModel.FeatureFlag)fromItem.getItem();
      
      Iterable<String> prereqFlagKeys = transform(flag.getPrerequisites(), p -> p.getKey());
      
      Iterable<String> segmentKeys = concat(
            transform(
                flag.getRules(),
                rule -> segmentKeysFromClauses(rule.getClauses()))
          );
      
      return ImmutableSet.copyOf(
          concat(kindAndKeys(FEATURES, prereqFlagKeys), kindAndKeys(SEGMENTS, segmentKeys))
          );
    } else if (fromKind == SEGMENTS) {
      DataModel.Segment segment = (DataModel.Segment)fromItem.getItem();
      
      Iterable<String> nestedSegmentKeys = concat(
            transform(
                segment.getRules(),
                rule -> segmentKeysFromClauses(rule.getClauses())));
      return ImmutableSet.copyOf(kindAndKeys(SEGMENTS, nestedSegmentKeys));
    }
    return emptySet();
  }
  
  private static Iterable<KindAndKey> kindAndKeys(DataKind kind, Iterable<String> keys) {
    return transform(keys, key -> new KindAndKey(kind, key));
  }
  
  private static Iterable<String> segmentKeysFromClauses(Iterable<DataModel.Clause> clauses) {
    return concat(Iterables.<DataModel.Clause, Iterable<String>>transform(
        clauses,
        clause -> clause.getOp() == Operator.segmentMatch ?
            transform(clause.getValues(), LDValue::stringValue) :
            emptyList()
        ));
  }
  
  /**
   * Returns a copy of the input data set that guarantees that if you iterate through it the outer list and
   * the inner list in the order provided, any object that depends on another object will be updated after it.
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
    for (KindAndKey dependency: computeDependenciesFrom(kind, item)) {
      if (dependency.kind == kind) {
        ItemDescriptor prereqItem = remainingItems.get(dependency.key);
        if (prereqItem != null) {
          addWithDependenciesFirst(kind, dependency.key, prereqItem, remainingItems, builder);
        }
      }
    }
    builder.put(key, item);
  }

  private static boolean isDependencyOrdered(DataKind kind) {
    return kind == FEATURES;
  }
  
  private static int getPriority(DataKind kind) {
    if (kind == FEATURES) {
      return 1;
    } else if (kind == SEGMENTS) {
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
  
  /**
   * Maintains a bidirectional dependency graph that can be updated whenever an item has changed.
   */
  static final class DependencyTracker {
    private final Map<KindAndKey, Set<KindAndKey>> dependenciesFrom = new HashMap<>();
    private final Map<KindAndKey, Set<KindAndKey>> dependenciesTo = new HashMap<>();
    
    /**
     * Updates the dependency graph when an item has changed. 
     * 
     * @param fromKind the changed item's kind
     * @param fromKey the changed item's key
     * @param fromItem the changed item
     */
    public void updateDependenciesFrom(DataKind fromKind, String fromKey, ItemDescriptor fromItem) {
      KindAndKey fromWhat = new KindAndKey(fromKind, fromKey);
      Set<KindAndKey> updatedDependencies = computeDependenciesFrom(fromKind, fromItem); // never null
      
      Set<KindAndKey> oldDependencySet = dependenciesFrom.get(fromWhat);
      if (oldDependencySet != null) {
        for (KindAndKey oldDep: oldDependencySet) {
          Set<KindAndKey> depsToThisOldDep = dependenciesTo.get(oldDep);
          if (depsToThisOldDep != null) {
            // COVERAGE: cannot cause this condition in unit tests, it should never be null 
            depsToThisOldDep.remove(fromWhat);
          }
        }
      }
      dependenciesFrom.put(fromWhat, updatedDependencies);
      for (KindAndKey newDep: updatedDependencies) {
        Set<KindAndKey> depsToThisNewDep = dependenciesTo.get(newDep);
        if (depsToThisNewDep == null) {
          depsToThisNewDep = new HashSet<>();
          dependenciesTo.put(newDep, depsToThisNewDep);
        }
        depsToThisNewDep.add(fromWhat);
      }
    }
    
    public void reset() {
      dependenciesFrom.clear();
      dependenciesTo.clear();
    }
    
    /**
     * Populates the given set with the union of the initial item and all items that directly or indirectly
     * depend on it (based on the current state of the dependency graph).
     * 
     * @param itemsOut an existing set to be updated
     * @param initialModifiedItem an item that has been modified
     */
    public void addAffectedItems(Set<KindAndKey> itemsOut, KindAndKey initialModifiedItem) {
      if (!itemsOut.contains(initialModifiedItem)) {
        itemsOut.add(initialModifiedItem);
        Set<KindAndKey> affectedItems = dependenciesTo.get(initialModifiedItem);
        if (affectedItems != null) {
          for (KindAndKey affectedItem: affectedItems) {
            addAffectedItems(itemsOut, affectedItem);
          }
        }
      }
    }
  }
}
