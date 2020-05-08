package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.server.DataModelDependencies.KindAndKey;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Iterables.concat;
import static com.launchdarkly.sdk.server.DataModel.ALL_DATA_KINDS;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static java.util.Collections.emptyMap;

/**
 * The data source will push updates into this component. We then apply any necessary
 * transformations before putting them into the data store; currently that just means sorting
 * the data set for init(). We also generate flag change events for any updates or deletions.
 * 
 * @since 4.11.0
 */
final class DataSourceUpdatesImpl implements DataSourceUpdates {
  private final DataStore store;
  private final EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeEventNotifier;
  private final EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> dataSourceStatusNotifier;
  private final DataModelDependencies.DependencyTracker dependencyTracker = new DataModelDependencies.DependencyTracker();
  private final DataStoreStatusProvider dataStoreStatusProvider;
  
  private volatile DataSourceStatusProvider.Status currentStatus;
  
  DataSourceUpdatesImpl(
      DataStore store,
      DataStoreStatusProvider dataStoreStatusProvider,
      EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeEventNotifier,
      EventBroadcasterImpl<DataSourceStatusProvider.StatusListener, DataSourceStatusProvider.Status> dataSourceStatusNotifier
      ) {
    this.store = store;
    this.flagChangeEventNotifier = flagChangeEventNotifier;
    this.dataSourceStatusNotifier = dataSourceStatusNotifier;
    this.dataStoreStatusProvider = dataStoreStatusProvider;
    
    currentStatus = new DataSourceStatusProvider.Status(
        DataSourceStatusProvider.State.INITIALIZING,
        Instant.now(),
        null
        );
  }
  
  @Override
  public void init(FullDataSet<ItemDescriptor> allData) {
    Map<DataKind, Map<String, ItemDescriptor>> oldData = null;
    
    if (hasFlagChangeEventListeners()) {
      // Query the existing data if any, so that after the update we can send events for whatever was changed
      oldData = new HashMap<>();
      for (DataKind kind: ALL_DATA_KINDS) {
        KeyedItems<ItemDescriptor> items = store.getAll(kind);
        oldData.put(kind, ImmutableMap.copyOf(items.getItems()));
      }
    }
    
    store.init(DataModelDependencies.sortAllCollections(allData));
    
    // We must always update the dependency graph even if we don't currently have any event listeners, because if
    // listeners are added later, we don't want to have to reread the whole data store to compute the graph
    updateDependencyTrackerFromFullDataSet(allData);
    
    // Now, if we previously queried the old data because someone is listening for flag change events, compare
    // the versions of all items and generate events for those (and any other items that depend on them)
    if (oldData != null) {
      sendChangeEvents(computeChangedItemsForFullDataSet(oldData, fullDataSetToMap(allData)));
    }
  }

  @Override
  public void upsert(DataKind kind, String key, ItemDescriptor item) {
    boolean successfullyUpdated = store.upsert(kind, key, item); 
    
    if (successfullyUpdated) {
      dependencyTracker.updateDependenciesFrom(kind, key, item);
      if (hasFlagChangeEventListeners()) {
        Set<KindAndKey> affectedItems = new HashSet<>();
        dependencyTracker.addAffectedItems(affectedItems, new KindAndKey(kind, key));
        sendChangeEvents(affectedItems);
      }
    }
  }

  @Override
  public DataStoreStatusProvider getDataStoreStatusProvider() {
    return dataStoreStatusProvider;
  }

  @Override
  public void updateStatus(DataSourceStatusProvider.State newState, DataSourceStatusProvider.ErrorInfo newError) {
    if (newState == null) {
      return;
    }
    DataSourceStatusProvider.Status newStatus;
    synchronized (this) {
      if (newState == DataSourceStatusProvider.State.INTERRUPTED && currentStatus.getState() == DataSourceStatusProvider.State.INITIALIZING) {
        newState = DataSourceStatusProvider.State.INITIALIZING; // see comment on updateStatus in the DataSourceUpdates interface
      }
      if (newState == currentStatus.getState() && newError == null) {
        return;
      }
      currentStatus = new DataSourceStatusProvider.Status(
          newState,
          newState == currentStatus.getState() ? currentStatus.getStateSince() : Instant.now(),
          newError == null ? currentStatus.getLastError() : newError
          );
      newStatus = currentStatus;
    }
    dataSourceStatusNotifier.broadcast(newStatus);
  }

  Status getLastStatus() {
    synchronized (this) {
      return currentStatus;
    }
  }
  
  private boolean hasFlagChangeEventListeners() {
    return flagChangeEventNotifier.hasListeners();
  }
  
  private void sendChangeEvents(Iterable<KindAndKey> affectedItems) {
    for (KindAndKey item: affectedItems) {
      if (item.kind == FEATURES) {
        flagChangeEventNotifier.broadcast(new FlagChangeEvent(item.key));
      }
    }
  }
  
  private void updateDependencyTrackerFromFullDataSet(FullDataSet<ItemDescriptor> allData) {
    dependencyTracker.reset();
    for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> e0: allData.getData()) {
      DataKind kind = e0.getKey();
      for (Map.Entry<String, ItemDescriptor> e1: e0.getValue().getItems()) {
        String key = e1.getKey();
        dependencyTracker.updateDependenciesFrom(kind, key, e1.getValue());
      }
    }
  }
  
  private Map<DataKind, Map<String, ItemDescriptor>> fullDataSetToMap(FullDataSet<ItemDescriptor> allData) {
    Map<DataKind, Map<String, ItemDescriptor>> ret = new HashMap<>();
    for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> e: allData.getData()) {
      ret.put(e.getKey(), ImmutableMap.copyOf(e.getValue().getItems()));
    }
    return ret;
  }
  
  private Set<KindAndKey> computeChangedItemsForFullDataSet(Map<DataKind, Map<String, ItemDescriptor>> oldDataMap,
      Map<DataKind, Map<String, ItemDescriptor>> newDataMap) {
    Set<KindAndKey> affectedItems = new HashSet<>();
    for (DataKind kind: ALL_DATA_KINDS) {
      Map<String, ItemDescriptor> oldItems = oldDataMap.get(kind);
      Map<String, ItemDescriptor> newItems = newDataMap.get(kind);
      if (oldItems == null) {
        oldItems = emptyMap();
      }
      if (newItems == null) {
        newItems = emptyMap();
      }
      Set<String> allKeys = ImmutableSet.copyOf(concat(oldItems.keySet(), newItems.keySet()));
      for (String key: allKeys) {
        ItemDescriptor oldItem = oldItems.get(key);
        ItemDescriptor newItem = newItems.get(key);
        if (oldItem == null && newItem == null) { // shouldn't be possible due to how we computed allKeys
          continue;
        }
        if (oldItem == null || newItem == null || oldItem.getVersion() < newItem.getVersion()) {
          dependencyTracker.addAffectedItems(affectedItems, new KindAndKey(kind, key));
        }
        // Note that comparing the version numbers is sufficient; we don't have to compare every detail of the
        // flag or segment configuration, because it's a basic underlying assumption of the entire LD data model
        // that if an entity's version number hasn't changed, then the entity hasn't changed (and that if two
        // version numbers are different, the higher one is the more recent version).
      }
    }
    return affectedItems;  
  }
}
