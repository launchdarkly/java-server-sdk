package com.launchdarkly.client.integrations;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.client.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.client.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.client.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.client.interfaces.DataStoreTypes.SerializedItemDescriptor;
import com.launchdarkly.client.interfaces.PersistentDataStore;

import java.io.IOException;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
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
class PersistentDataStoreWrapper implements DataStore {
  private static final String CACHE_REFRESH_THREAD_POOL_NAME_FORMAT = "CachingStoreWrapper-refresher-pool-%d";

  private final PersistentDataStore core;
  private final LoadingCache<CacheKey, Optional<ItemDescriptor>> itemCache;
  private final LoadingCache<DataKind, KeyedItems<ItemDescriptor>> allCache;
  private final LoadingCache<String, Boolean> initCache;
  private final boolean cacheIndefinitely;
  private final AtomicBoolean inited = new AtomicBoolean(false);
  private final ListeningExecutorService executorService;
  
  PersistentDataStoreWrapper(
      final PersistentDataStore core,
      Duration cacheTtl,
      PersistentDataStoreBuilder.StaleValuesPolicy staleValuesPolicy,
      CacheMonitor cacheMonitor
    ) {
    this.core = core;
    
    if (cacheTtl == null || cacheTtl.isZero()) {
      itemCache = null;
      allCache = null;
      initCache = null;
      executorService = null;
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
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat(CACHE_REFRESH_THREAD_POOL_NAME_FORMAT).setDaemon(true).build();
        ExecutorService parentExecutor = Executors.newSingleThreadExecutor(threadFactory);
        executorService = MoreExecutors.listeningDecorator(parentExecutor);
        
        // Note that the REFRESH_ASYNC mode is only used for itemCache, not allCache, since retrieving all flags is
        // less frequently needed and we don't want to incur the extra overhead.
        itemLoader = CacheLoader.asyncReloading(itemLoader, executorService);
      } else {
        executorService = null;
      }
      
      itemCache = newCacheBuilder(cacheTtl, staleValuesPolicy, cacheMonitor).build(itemLoader);
      allCache = newCacheBuilder(cacheTtl, staleValuesPolicy, cacheMonitor).build(allLoader);
      initCache = newCacheBuilder(cacheTtl, staleValuesPolicy, cacheMonitor).build(initLoader);
      
      if (cacheMonitor != null) {
        cacheMonitor.setSource(new CacheStatsSource());
      }
    }
  }
  
  private static CacheBuilder<Object, Object> newCacheBuilder(
      Duration cacheTtl,
      PersistentDataStoreBuilder.StaleValuesPolicy staleValuesPolicy,
      CacheMonitor cacheMonitor
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
    if (cacheMonitor != null) {
      builder = builder.recordStats();
    }
    return builder;
  }
  
  @Override
  public void close() throws IOException {
    if (executorService != null) {
      executorService.shutdownNow();
    }
    core.close();
  }

  @Override
  public boolean isInitialized() {
    if (inited.get()) {
      return true;
    }
    boolean result;
    if (initCache != null) {
      try {
        result = initCache.get("");
      } catch (ExecutionException e) {
        result = false;
      }
    } else {
      result = core.isInitialized();
    }
    if (result) {
      inited.set(true);
    }
    return result;
  }
  
  @Override
  public void init(FullDataSet<ItemDescriptor> allData) {
    ImmutableList.Builder<Map.Entry<DataKind, KeyedItems<SerializedItemDescriptor>>> allBuilder = ImmutableList.builder();
    for (Map.Entry<DataKind, KeyedItems<ItemDescriptor>> e0: allData.getData()) {
      DataKind kind = e0.getKey();
      ImmutableList.Builder<Map.Entry<String, SerializedItemDescriptor>> itemsBuilder = ImmutableList.builder();
      for (Map.Entry<String, ItemDescriptor> e1: e0.getValue().getItems()) {
        itemsBuilder.add(new AbstractMap.SimpleEntry<>(e1.getKey(), serialize(kind, e1.getValue())));
      }
      allBuilder.add(new AbstractMap.SimpleEntry<>(kind, new KeyedItems<>(itemsBuilder.build())));
    }
    RuntimeException failure = null;
    try {
      core.init(new FullDataSet<>(allBuilder.build()));
    } catch (RuntimeException e) {
      failure = e;
    }
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
  
  @Override
  public ItemDescriptor get(DataKind kind, String key) {
    if (itemCache != null) {
      try {
        return itemCache.get(CacheKey.forItem(kind, key)).orNull();
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
    return getAndDeserializeItem(kind, key);
  }

  @Override
  public KeyedItems<ItemDescriptor> getAll(DataKind kind) {
    if (allCache != null) {
      try {
        return allCache.get(kind);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
    return getAllAndDeserialize(kind);
  }

  @Override
  public boolean upsert(DataKind kind, String key, ItemDescriptor item) {
    SerializedItemDescriptor serializedItem = serialize(kind, item);
    boolean updated = false;
    RuntimeException failure = null;
    try {
      updated = core.upsert(kind, key, serializedItem);
    } catch (RuntimeException e) {
      // Normally, if the underlying store failed to do the update, we do not want to update the cache -
      // the idea being that it's better to stay in a consistent state of having old data than to act
      // like we have new data but then suddenly fall back to old data when the cache expires. However,
      // if the cache TTL is infinite, then it makes sense to update the cache always.
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
        try {
          Optional<ItemDescriptor> oldItem = itemCache.get(cacheKey);
          if (oldItem.isPresent() && oldItem.get().getVersion() < item.getVersion()) {
            itemCache.put(cacheKey, Optional.of(item));
          }
        } catch (ExecutionException e) {
          // An exception here means that the underlying database is down *and* there was no
          // cached item; in that case we just go ahead and update the cache.
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
        try {
          KeyedItems<ItemDescriptor> cachedAll = allCache.get(kind);
          allCache.put(kind, updateSingleItem(cachedAll, key, item));
        } catch (ExecutionException e) {
          // An exception here means that we did not have a cached value for All, so it tried to query
          // the underlying store, which failed (not surprisingly since it just failed a moment ago
          // when we tried to do an update). This should not happen in infinite-cache mode, but if it
          // does happen, there isn't really anything we can do.
        }
      } else {
        allCache.invalidate(kind);
      }
    }
    if (failure != null) {
      throw failure;
    }
    return updated;
  }
  
  /**
   * Return the underlying Guava cache stats object.
   *
   * @return the cache statistics object
   */
  public CacheStats getCacheStats() {
    if (itemCache != null) {
      return itemCache.stats();
    }
    return null;
  }

  /**
   * Return the underlying implementation object.
   * 
   * @return the underlying implementation object
   */
  public PersistentDataStore getCore() {
    return core;
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
    Object item = itemDesc.getItem();
    return new SerializedItemDescriptor(itemDesc.getVersion(), item == null ? null : kind.serialize(item));
  }
  
  private ItemDescriptor deserialize(DataKind kind, SerializedItemDescriptor serializedItemDesc) {
    String serializedItem = serializedItemDesc.getSerializedItem();
    if (serializedItem == null) {
      return ItemDescriptor.deletedItem(serializedItemDesc.getVersion());
    }
    ItemDescriptor deserializedItem = kind.deserialize(serializedItem);
    return (serializedItemDesc.getVersion() == 0 || serializedItemDesc.getVersion() == deserializedItem.getVersion())
        ? deserializedItem
        : new ItemDescriptor(serializedItemDesc.getVersion(), deserializedItem.getItem());
  }

  private KeyedItems<ItemDescriptor> updateSingleItem(KeyedItems<ItemDescriptor> items, String key, ItemDescriptor item) {
    // This is somewhat inefficient but it's preferable to use immutable data structures in the cache.
    return new KeyedItems<>(
        ImmutableList.copyOf(concat(
            filter(items.getItems(), e -> !e.getKey().equals(key)),
            ImmutableList.<Map.Entry<String, ItemDescriptor>>of(new AbstractMap.SimpleEntry<>(key, item))
            )
        ));
  }
  
  private final class CacheStatsSource implements Callable<CacheMonitor.CacheStats> {
    public CacheMonitor.CacheStats call() {
      if (itemCache == null || allCache == null) {
        return null;
      }
      CacheStats itemStats = itemCache.stats();
      CacheStats allStats = allCache.stats();
      return new CacheMonitor.CacheStats(
          itemStats.hitCount() + allStats.hitCount(),
          itemStats.missCount() + allStats.missCount(),
          itemStats.loadSuccessCount() + allStats.loadSuccessCount(),
          itemStats.loadExceptionCount() + allStats.loadExceptionCount(),
          itemStats.totalLoadTime() + allStats.totalLoadTime(),
          itemStats.evictionCount() + allStats.evictionCount());
    }
  }

  private static class CacheKey {
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
