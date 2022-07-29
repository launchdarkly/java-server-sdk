package com.launchdarkly.sdk.server;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogValues;
import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.CacheStats;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.SerializedItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreUpdateSink;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;

import java.io.IOException;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.isEmpty;

/**
 * Package-private implementation of {@link DataStore} that delegates the basic functionality to an
 * instance of {@link PersistentDataStore}. It provides optional caching behavior and other logic that
 * would otherwise be repeated in every data store implementation. This makes it easier to create new
 * database integrations by implementing only the database-specific logic. 
 * <p>
 * This class is only constructed by {@link PersistentDataStoreBuilder}.
 */
final class PersistentDataStoreWrapper implements DataStore {
  private final PersistentDataStore core;
  private final LoadingCache<CacheKey, Optional<ItemDescriptor>> itemCache;
  private final LoadingCache<DataKind, KeyedItems<ItemDescriptor>> allCache;
  private final LoadingCache<String, Boolean> initCache;
  private final PersistentDataStoreStatusManager statusManager;
  private final boolean cacheIndefinitely;
  private final Set<DataKind> cachedDataKinds = new HashSet<>(); // this map is used in pollForAvailability()
  private final AtomicBoolean inited = new AtomicBoolean(false);
  private final ListeningExecutorService cacheExecutor;
  private final LDLogger logger;
  
  PersistentDataStoreWrapper(
      final PersistentDataStore core,
      Duration cacheTtl,
      PersistentDataStoreBuilder.StaleValuesPolicy staleValuesPolicy,
      boolean recordCacheStats,
      DataStoreUpdateSink dataStoreUpdates,
      ScheduledExecutorService sharedExecutor,
      LDLogger logger
    ) {
    this.core = core;
    this.logger = logger;
    
    if (cacheTtl.isZero()) {
      itemCache = null;
      allCache = null;
      initCache = null;
      cacheExecutor = null;
      cacheIndefinitely = false;
    } else {
      cacheIndefinitely = cacheTtl.isNegative();
      CacheLoader<CacheKey, Optional<ItemDescriptor>> itemLoader = new CacheLoader<CacheKey, Optional<ItemDescriptor>>() {
        @Override
        public Optional<ItemDescriptor> load(CacheKey key) throws Exception { 
          return Optional.fromNullable(getAndDeserializeItem(key.kind, key.key));
        }
      };
      CacheLoader<DataKind, KeyedItems<ItemDescriptor>> allLoader = new CacheLoader<DataKind, KeyedItems<ItemDescriptor>>() {
        @Override
        public KeyedItems<ItemDescriptor> load(DataKind kind) throws Exception {
          return getAllAndDeserialize(kind);
        }
      };
      CacheLoader<String, Boolean> initLoader = new CacheLoader<String, Boolean>() {
        @Override
        public Boolean load(String key) throws Exception {
          return core.isInitialized();
        }
      };
      
      if (staleValuesPolicy == PersistentDataStoreBuilder.StaleValuesPolicy.REFRESH_ASYNC) {
        cacheExecutor = MoreExecutors.listeningDecorator(sharedExecutor);
        
        // Note that the REFRESH_ASYNC mode is only used for itemCache, not allCache, since retrieving all flags is
        // less frequently needed and we don't want to incur the extra overhead.
        itemLoader = CacheLoader.asyncReloading(itemLoader, cacheExecutor);
      } else {
        cacheExecutor = null;
      }
      
      itemCache = newCacheBuilder(cacheTtl, staleValuesPolicy, recordCacheStats).build(itemLoader);
      allCache = newCacheBuilder(cacheTtl, staleValuesPolicy, recordCacheStats).build(allLoader);
      initCache = newCacheBuilder(cacheTtl, staleValuesPolicy, recordCacheStats).build(initLoader);
    }
    statusManager = new PersistentDataStoreStatusManager(
        !cacheIndefinitely,
        true,
        this::pollAvailabilityAfterOutage,
        dataStoreUpdates::updateStatus,
        sharedExecutor,
        logger
        );
  }
  
  private static CacheBuilder<Object, Object> newCacheBuilder(
      Duration cacheTtl,
      PersistentDataStoreBuilder.StaleValuesPolicy staleValuesPolicy,
      boolean recordCacheStats
    ) {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    boolean isInfiniteTtl = cacheTtl.isNegative();
    if (!isInfiniteTtl) {
      if (staleValuesPolicy == PersistentDataStoreBuilder.StaleValuesPolicy.EVICT) {
        // We are using an "expire after write" cache. This will evict stale values and block while loading the latest
        // from the underlying data store.
        builder = builder.expireAfterWrite(cacheTtl);
      } else {
        // We are using a "refresh after write" cache. This will not automatically evict stale values, allowing them
        // to be returned if failures occur when updating them.
        builder = builder.refreshAfterWrite(cacheTtl);
      }
    }
    if (recordCacheStats) {
      builder = builder.recordStats();
    }
    return builder;
  }
  
  @Override
  public void close() throws IOException {
    statusManager.close();
    core.close();
  }

  @Override
  public boolean isInitialized() {
    if (inited.get()) {
      return true;
    }
    boolean result;
    try {
      if (initCache != null) {
        result = initCache.get("");
      } else {
        result = core.isInitialized();
      }
    } catch (Exception e) {
      result = false;
    }
    if (result) {
      inited.set(true);
    }
    return result;
  }
  
  @Override
  public void init(FullDataSet<ItemDescriptor> allData) {
    synchronized (cachedDataKinds) {
      cachedDataKinds.clear();
      for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> e: allData.getData()) {
        cachedDataKinds.add(e.getKey());
      }
    }
    ImmutableList.Builder<Map.Entry<DataKind, KeyedItems<SerializedItemDescriptor>>> allBuilder = ImmutableList.builder();
    for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> e0: allData.getData()) {
      DataKind kind = e0.getKey();
      KeyedItems<SerializedItemDescriptor> items = serializeAll(kind, e0.getValue());
      allBuilder.add(new AbstractMap.SimpleEntry<>(kind, items));
    }
    RuntimeException failure = initCore(new FullDataSet<>(allBuilder.build()));
    if (itemCache != null && allCache != null) {
      itemCache.invalidateAll();
      allCache.invalidateAll();
      if (failure != null && !cacheIndefinitely) {
        // Normally, if the underlying store failed to do the update, we do not want to update the cache -
        // the idea being that it's better to stay in a consistent state of having old data than to act
        // like we have new data but then suddenly fall back to old data when the cache expires. However,
        // if the cache TTL is infinite, then it makes sense to update the cache always.
        throw failure;
      }
      for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> e0: allData.getData()) {
        DataKind kind = e0.getKey();
        KeyedItems<ItemDescriptor> immutableItems = new KeyedItems<>(ImmutableList.copyOf(e0.getValue().getItems()));
        allCache.put(kind, immutableItems);
        for (Map.Entry<String, ItemDescriptor> e1: e0.getValue().getItems()) {
          itemCache.put(CacheKey.forItem(kind, e1.getKey()), Optional.of(e1.getValue()));
        }
      }
    }
    if (failure == null || cacheIndefinitely) {
      inited.set(true);
    }
    if (failure != null) {
      throw failure;
    }
  }
  
  private RuntimeException initCore(FullDataSet<SerializedItemDescriptor> allData) {
    try {
      core.init(allData);
      processError(null);
      return null;
    } catch (RuntimeException e) {
      processError(e);
      return e;
    }
  }
  
  @Override
  public ItemDescriptor get(DataKind kind, String key) {
    try {
      ItemDescriptor ret = itemCache != null ? itemCache.get(CacheKey.forItem(kind, key)).orNull() :
        getAndDeserializeItem(kind, key);
      processError(null);
      return ret;
    } catch (Exception e) {
      processError(e);
      throw getAsRuntimeException(e);
    }
  }

  @Override
  public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
    try {
      KeyedItems<ItemDescriptor> ret;
      ret = allCache != null ? allCache.get(kind) : getAllAndDeserialize(kind);
      processError(null);
      return ret;
    } catch (Exception e) {
      processError(e);
      throw getAsRuntimeException(e);
    }
  }

  private static RuntimeException getAsRuntimeException(Exception e) {
    Throwable t = (e instanceof ExecutionException || e instanceof UncheckedExecutionException)
        ? e.getCause() // this is a wrapped exception thrown by a cache
        : e;
    return t instanceof RuntimeException ? (RuntimeException)t : new RuntimeException(t);
  }
  
  @Override
  public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
    synchronized (cachedDataKinds) {
      cachedDataKinds.add(kind);
    }
    SerializedItemDescriptor serializedItem = serialize(kind, item);
    boolean updated = false;
    RuntimeException failure = null;
    try {
      updated = core.upsert(kind, key, serializedItem);
      processError(null);
    } catch (RuntimeException e) {
      // Normally, if the underlying store failed to do the update, we do not want to update the cache -
      // the idea being that it's better to stay in a consistent state of having old data than to act
      // like we have new data but then suddenly fall back to old data when the cache expires. However,
      // if the cache TTL is infinite, then it makes sense to update the cache always.
      processError(e);
      if (!cacheIndefinitely)
      {
          throw e;
      }
      failure = e;
    }
    if (itemCache != null) {
      CacheKey cacheKey = CacheKey.forItem(kind, key);
      if (failure == null) {
        if (updated) {
          itemCache.put(cacheKey, Optional.of(item));
        } else {
          // there was a concurrent modification elsewhere - update the cache to get the new state
          itemCache.refresh(cacheKey);
        }
      } else {
        Optional<ItemDescriptor> oldItem = itemCache.getIfPresent(cacheKey);
        if (oldItem == null || !oldItem.isPresent() || oldItem.get().getVersion() < item.getVersion()) {
          itemCache.put(cacheKey, Optional.of(item));
        }
      }
    }
    if (allCache != null) {
      // If the cache has a finite TTL, then we should remove the "all items" cache entry to force
      // a reread the next time All is called. However, if it's an infinite TTL, we need to just
      // update the item within the existing "all items" entry (since we want things to still work
      // even if the underlying store is unavailable).
      if (cacheIndefinitely) {
        KeyedItems<ItemDescriptor> cachedAll = allCache.getIfPresent(kind);
        allCache.put(kind, updateSingleItem(cachedAll, key, item));
      } else {
        allCache.invalidate(kind);
      }
    }
    if (failure != null) {
      throw failure;
    }
    return updated;
  }
  
  @Override
  public boolean isStatusMonitoringEnabled() {
    return true;
  }

  @Override
  public CacheStats getCacheStats() {
    if (itemCache == null || allCache == null) {
      return null;
    }
    com.google.common.cache.CacheStats itemStats = itemCache.stats();
    com.google.common.cache.CacheStats allStats = allCache.stats();
    return new CacheStats(
        itemStats.hitCount() + allStats.hitCount(),
        itemStats.missCount() + allStats.missCount(),
        itemStats.loadSuccessCount() + allStats.loadSuccessCount(),
        itemStats.loadExceptionCount() + allStats.loadExceptionCount(),
        itemStats.totalLoadTime() + allStats.totalLoadTime(),
        itemStats.evictionCount() + allStats.evictionCount());
  }

  private ItemDescriptor getAndDeserializeItem(DataKind kind, String key) {
    SerializedItemDescriptor maybeSerializedItem = core.get(kind, key);
    return maybeSerializedItem == null ? null : deserialize(kind, maybeSerializedItem);
  }
  
  private KeyedItems<ItemDescriptor> getAllAndDeserialize(DataKind kind) {
    KeyedItems<SerializedItemDescriptor> allItems = core.getAll(kind);
    if (isEmpty(allItems.getItems())) {
      return new KeyedItems<ItemDescriptor>(null);
    }
    ImmutableList.Builder<Map.Entry<String, ItemDescriptor>> b = ImmutableList.builder();
    for (Map.Entry<String, SerializedItemDescriptor> e: allItems.getItems()) {
      b.add(new AbstractMap.SimpleEntry<>(e.getKey(), deserialize(kind, e.getValue())));
    }
    return new KeyedItems<>(b.build());
  }
  
  private SerializedItemDescriptor serialize(DataKind kind, ItemDescriptor itemDesc) {
    boolean isDeleted = itemDesc.getItem() == null;
    return new SerializedItemDescriptor(itemDesc.getVersion(), isDeleted, kind.serialize(itemDesc));
  }
  
  private KeyedItems<SerializedItemDescriptor> serializeAll(DataKind kind, KeyedItems<ItemDescriptor> items) {
    ImmutableList.Builder<Map.Entry<String, SerializedItemDescriptor>> itemsBuilder = ImmutableList.builder();
    for (Map.Entry<String, ItemDescriptor> e: items.getItems()) {
      itemsBuilder.add(new AbstractMap.SimpleEntry<>(e.getKey(), serialize(kind, e.getValue())));
    }
    return new KeyedItems<>(itemsBuilder.build());
  }
  
  private ItemDescriptor deserialize(DataKind kind, SerializedItemDescriptor serializedItemDesc) {
    if (serializedItemDesc.isDeleted() || serializedItemDesc.getSerializedItem() == null) {
      return ItemDescriptor.deletedItem(serializedItemDesc.getVersion());
    }
    ItemDescriptor deserializedItem = kind.deserialize(serializedItemDesc.getSerializedItem());
    if (serializedItemDesc.getVersion() == 0 || serializedItemDesc.getVersion() == deserializedItem.getVersion()
        || deserializedItem.getItem() == null) {
      return deserializedItem;
    }
    // If the store gave us a version number that isn't what was encoded in the object, trust it
    return new ItemDescriptor(serializedItemDesc.getVersion(), deserializedItem.getItem());
  }

  private KeyedItems<ItemDescriptor> updateSingleItem(KeyedItems<ItemDescriptor> items, String key, ItemDescriptor item) {
    // This is somewhat inefficient but it's preferable to use immutable data structures in the cache.
    return new KeyedItems<>(
        ImmutableList.copyOf(concat(
            items == null ? ImmutableList.of() : filter(items.getItems(), e -> !e.getKey().equals(key)),
            ImmutableList.<Map.Entry<String, ItemDescriptor>>of(new AbstractMap.SimpleEntry<>(key, item))
            )
        ));
  }
  
  private void processError(Throwable error) {
    if (error == null) {
      // If we're waiting to recover after a failure, we'll let the polling routine take care
      // of signaling success. Even if we could signal success a little earlier based on the
      // success of whatever operation we just did, we'd rather avoid the overhead of acquiring
      // w.statusLock every time we do anything. So we'll just do nothing here.
      return;
    }
    statusManager.updateAvailability(false);
  }
  
  private boolean pollAvailabilityAfterOutage() {
    if (!core.isStoreAvailable()) {
      return false;
    }
    
    if (cacheIndefinitely && allCache != null) {
      // If we're in infinite cache mode, then we can assume the cache has a full set of current
      // flag data (since presumably the data source has still been running) and we can just
      // write the contents of the cache to the underlying data store.
      DataKind[] allKinds;
      synchronized (cachedDataKinds) {
        allKinds = cachedDataKinds.toArray(new DataKind[cachedDataKinds.size()]);        
      }
      ImmutableList.Builder<Map.Entry<DataKind, KeyedItems<SerializedItemDescriptor>>> builder = ImmutableList.builder();
      for (DataKind kind: allKinds) {
        KeyedItems<ItemDescriptor> items = allCache.getIfPresent(kind);
        if (items != null) {
          builder.add(new AbstractMap.SimpleEntry<>(kind, serializeAll(kind, items)));
        }
      }
      RuntimeException e = initCore(new FullDataSet<>(builder.build()));
      if (e == null) {
        logger.warn("Successfully updated persistent store from cached data");
      } else {
        // We failed to write the cached data to the underlying store. In this case, we should not
        // return to a recovered state, but just try this all again next time the poll task runs.
        logger.error("Tried to write cached data to persistent store after a store outage, but failed: {}",
            LogValues.exceptionSummary(e));
        logger.debug(LogValues.exceptionTrace(e));
        return false;
      }
    }
    
    return true;
  }
  
  static final class CacheKey {
    final DataKind kind;
    final String key;
    
    public static CacheKey forItem(DataKind kind, String key) {
      return new CacheKey(kind, key);
    }
    
    private CacheKey(DataKind kind, String key) {
      this.kind = kind;
      this.key = key;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof CacheKey) {
        CacheKey o = (CacheKey) other;
        return o.kind.getName().equals(this.kind.getName()) && o.key.equals(this.key);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return kind.getName().hashCode() * 31 + key.hashCode();
    }
  }
}
