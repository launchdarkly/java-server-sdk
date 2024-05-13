package com.launchdarkly.sdk.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogValues;
import com.launchdarkly.sdk.server.DataModelDependencies.KindAndKey;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.StatusListener;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;
import static com.launchdarkly.sdk.server.DataModel.ALL_DATA_KINDS;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static java.util.Collections.emptyMap;

/**
 * The data source will push updates into this component. We then apply any necessary
 * transformations before putting them into the data store; currently that just means sorting
 * the data set for init(). We also generate flag change events for any updates or deletions.
 * <p>
 * This component is also responsible for receiving updates to the data source status, broadcasting
 * them to any status listeners, and tracking the length of any period of sustained failure.
 * 
 * @since 4.11.0
 */
final class DataSourceUpdatesImpl implements DataSourceUpdateSink {
  private final DataStore store;
  private final EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeEventNotifier;
  private final EventBroadcasterImpl<StatusListener, Status> dataSourceStatusNotifier;
  private final DataModelDependencies.DependencyTracker dependencyTracker = new DataModelDependencies.DependencyTracker();
  private final DataStoreStatusProvider dataStoreStatusProvider;
  private final OutageTracker outageTracker;
  private final Object stateLock = new Object();
  private final LDLogger logger;
  
  private volatile Status currentStatus;
  private volatile boolean lastStoreUpdateFailed = false;
  volatile Consumer<String> onOutageErrorLog = null; // test instrumentation
  
  DataSourceUpdatesImpl(
      DataStore store,
      DataStoreStatusProvider dataStoreStatusProvider,
      EventBroadcasterImpl<FlagChangeListener, FlagChangeEvent> flagChangeEventNotifier,
      EventBroadcasterImpl<StatusListener, Status> dataSourceStatusNotifier,
      ScheduledExecutorService sharedExecutor,
      Duration outageLoggingTimeout,
      LDLogger baseLogger
      ) {
    this.store = store;
    this.flagChangeEventNotifier = flagChangeEventNotifier;
    this.dataSourceStatusNotifier = dataSourceStatusNotifier;
    this.dataStoreStatusProvider = dataStoreStatusProvider;
    this.outageTracker = new OutageTracker(sharedExecutor, outageLoggingTimeout);
    this.logger = baseLogger.subLogger(Loggers.DATA_SOURCE_LOGGER_NAME);
    
    currentStatus = new Status(State.INITIALIZING, Instant.now(), null);
  }
  
  @Override
  public boolean init(FullDataSet<ItemDescriptor> allData) {
    Map<DataKind, Map<String, ItemDescriptor>> oldData = null;

    try {
      if (hasFlagChangeEventListeners()) {
        // Query the existing data if any, so that after the update we can send events for whatever was changed
        oldData = new HashMap<>();
        for (DataKind kind: ALL_DATA_KINDS) {
          KeyedItems<ItemDescriptor> items = store.getAll(kind);
          oldData.put(kind, ImmutableMap.copyOf(items.getItems()));
        }
      }
      store.init(DataModelDependencies.sortAllCollections(allData));
      lastStoreUpdateFailed = false;
    } catch (RuntimeException e) {
      reportStoreFailure(e);
      return false;
    }
    
    // We must always update the dependency graph even if we don't currently have any event listeners, because if
    // listeners are added later, we don't want to have to reread the whole data store to compute the graph
    updateDependencyTrackerFromFullDataSet(allData);
    
    // Now, if we previously queried the old data because someone is listening for flag change events, compare
    // the versions of all items and generate events for those (and any other items that depend on them)
    if (oldData != null) {
      sendChangeEvents(computeChangedItemsForFullDataSet(oldData, fullDataSetToMap(allData)));
    }
    
    return true;
  }

  @Override
  public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
    boolean successfullyUpdated;
    try {
      successfullyUpdated = store.upsert(kind, key, item);
      lastStoreUpdateFailed = false;
    } catch (RuntimeException e) {
      reportStoreFailure(e);
      return false;
    }
    
    if (successfullyUpdated) {
      dependencyTracker.updateDependenciesFrom(kind, key, item);
      if (hasFlagChangeEventListeners()) {
        Set<KindAndKey> affectedItems = new HashSet<>();
        dependencyTracker.addAffectedItems(affectedItems, new KindAndKey(kind, key));
        sendChangeEvents(affectedItems);
      }
    }
    
    return true;
  }

  @Override
  public DataStoreStatusProvider getDataStoreStatusProvider() {
    return dataStoreStatusProvider;
  }

  @Override
  public void updateStatus(State newState, ErrorInfo newError) {
    if (newState == null) {
      return;
    }
    
    Status statusToBroadcast = null;
    
    synchronized (stateLock) {
      Status oldStatus = currentStatus;
      
      if (newState == State.INTERRUPTED && oldStatus.getState() == State.INITIALIZING) {
        newState = State.INITIALIZING; // see comment on updateStatus in the DataSourceUpdates interface
      }
      
      if (newState != oldStatus.getState() || newError != null) {
        currentStatus = new Status(
            newState,
            newState == currentStatus.getState() ? currentStatus.getStateSince() : Instant.now(),
            newError == null ? currentStatus.getLastError() : newError
            );
        statusToBroadcast = currentStatus;
        stateLock.notifyAll();
      }
      
      outageTracker.trackDataSourceState(newState, newError);
    }
    
    if (statusToBroadcast != null) {
      dataSourceStatusNotifier.broadcast(statusToBroadcast);
    }
  }

  // package-private - called from DataSourceStatusProviderImpl
  Status getLastStatus() {
    synchronized (stateLock) {
      return currentStatus;
    }
  }
  
  // package-private - called from DataSourceStatusProviderImpl
  boolean waitFor(State desiredState, Duration timeout) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    synchronized (stateLock) {
      while (true) {
        if (currentStatus.getState() == desiredState) {
          return true;
        }
        if (currentStatus.getState() == State.OFF) {
          return false;
        }
        if (timeout.isZero()) {
          stateLock.wait();
        } else {
          long now = System.currentTimeMillis();
          if (now >= deadline) {
            return false;
          }
          stateLock.wait(deadline - now);
        }
      }
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
        // COVERAGE: there is no way to simulate this condition in unit tests
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
          // COVERAGE: there is no way to simulate this condition in unit tests
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
  
  private void reportStoreFailure(RuntimeException e) {
    if (!lastStoreUpdateFailed) {
      logger.warn("Unexpected data store error when trying to store an update received from the data source: {}",
          LogValues.exceptionSummary(e));
      lastStoreUpdateFailed = true;
    }
    logger.debug(LogValues.exceptionTrace(e));
    updateStatus(State.INTERRUPTED, ErrorInfo.fromException(ErrorKind.STORE_ERROR, e));
  }
  
  // Encapsulates our logic for keeping track of the length and cause of data source outages.
  private final class OutageTracker {
    private final boolean enabled;
    private final ScheduledExecutorService sharedExecutor;
    private final Duration loggingTimeout;
    private final HashMap<ErrorInfo, Integer> errorCounts = new HashMap<>();
    
    private volatile boolean inOutage;
    private volatile ScheduledFuture<?> timeoutFuture;
    
    OutageTracker(ScheduledExecutorService sharedExecutor, Duration loggingTimeout) {
      this.sharedExecutor = sharedExecutor;
      this.loggingTimeout = loggingTimeout;
      this.enabled = loggingTimeout != null;
    }
    
    void trackDataSourceState(State newState, ErrorInfo newError) {
      if (!enabled) {
        return;
      }
      
      synchronized (this) {
        if (newState == State.INTERRUPTED || newError != null || (newState == State.INITIALIZING && inOutage)) {
          // We are in a potentially recoverable outage. If that wasn't the case already, and if we've been configured
          // with a timeout for logging the outage at a higher level, schedule that timeout.
          if (inOutage) {
            // We were already in one - just record this latest error for logging later.
            recordError(newError);
          } else { 
            // We weren't already in one, so set the timeout and start recording errors.
            inOutage = true;
            errorCounts.clear();
            recordError(newError);
            timeoutFuture = sharedExecutor.schedule(this::onTimeout, loggingTimeout.toMillis(), TimeUnit.MILLISECONDS);
          }
        } else {
          if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
          }
          inOutage = false;
        }
      }
    }
  
    private void recordError(ErrorInfo newError) {
      // Accumulate how many times each kind of error has occurred during the outage - use just the basic
      // properties as the key so the map won't expand indefinitely
      ErrorInfo basicErrorInfo = new ErrorInfo(newError.getKind(), newError.getStatusCode(), null, null);
      errorCounts.compute(basicErrorInfo, (key, oldValue) -> oldValue == null ? 1 : oldValue.intValue() + 1);
    }
    
    private void onTimeout() {
      String errorsDesc;
      synchronized (this) {
        if (timeoutFuture == null || !inOutage) {
          // COVERAGE: there is no way to simulate this condition in unit tests
          return;
        }
        timeoutFuture = null;
        errorsDesc = Joiner.on(", ").join(transform(errorCounts.entrySet(), DataSourceUpdatesImpl::describeErrorCount));
      }
      if (onOutageErrorLog != null) {
        onOutageErrorLog.accept(errorsDesc);
      }
      logger.error(
          "A streaming connection to LaunchDarkly has not been established within {} after the connection was interrupted. " +
              "The following errors were encountered: {}",
          Util.describeDuration(loggingTimeout),
          errorsDesc
      );
    }
  }
  
  private static String describeErrorCount(Map.Entry<ErrorInfo, Integer> entry) {
    return entry.getKey() + " (" + entry.getValue() + (entry.getValue() == 1 ? " time" : " times") + ")";
  }
}
