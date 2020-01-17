package com.launchdarkly.client.utils;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FeatureStoreCacheConfig;
import com.launchdarkly.client.VersionedData;
import com.launchdarkly.client.VersionedDataKind;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CachingStoreWrapper is a partial implementation of {@link FeatureStore} that delegates the basic
 * functionality to an instance of {@link FeatureStoreCore}. It provides optional caching behavior and
 * other logic that would otherwise be repeated in every feature store implementation. This makes it
 * easier to create new database integrations by implementing only the database-specific logic. 
 * <p>
 * Construct instances of this class with {@link CachingStoreWrapper#builder(FeatureStoreCore)}.
 * 
 * @since 4.6.0
 */
public class CachingStoreWrapper implements FeatureStore {
  private static final String CACHE_REFRESH_THREAD_POOL_NAME_FORMAT = "CachingStoreWrapper-refresher-pool-%d";

  private final FeatureStoreCore core;
  private final FeatureStoreCacheConfig caching;
  private final LoadingCache<CacheKey, Optional<VersionedData>> itemCache;
  private final LoadingCache<VersionedDataKind<?>, ImmutableMap<String, VersionedData>> allCache;
  private final LoadingCache<String, Boolean> initCache;
  private final AtomicBoolean inited = new AtomicBoolean(false);
  private final ListeningExecutorService executorService;
  
  /**
   * Creates a new builder.
   * @param core the {@link FeatureStoreCore} instance
   * @return the builder
   */
  public static CachingStoreWrapper.Builder builder(FeatureStoreCore core) {
    return new Builder(core);
  }
  
  protected CachingStoreWrapper(final FeatureStoreCore core, FeatureStoreCacheConfig caching) {
    this.core = core;
    this.caching = caching;
    
    if (!caching.isEnabled()) {
      itemCache = null;
      allCache = null;
      initCache = null;
      executorService = null;
    } else {
      CacheLoader<CacheKey, Optional<VersionedData>> itemLoader = new CacheLoader<CacheKey, Optional<VersionedData>>() {
        @Override
        public Optional<VersionedData> load(CacheKey key) throws Exception {
          return Optional.<VersionedData>fromNullable(core.getInternal(key.kind, key.key));
        }
      };
      CacheLoader<VersionedDataKind<?>, ImmutableMap<String, VersionedData>> allLoader = new CacheLoader<VersionedDataKind<?>, ImmutableMap<String, VersionedData>>() {
        @Override
        public ImmutableMap<String, VersionedData> load(VersionedDataKind<?> kind) throws Exception {
          return itemsOnlyIfNotDeleted(core.getAllInternal(kind));
        }
      };
      CacheLoader<String, Boolean> initLoader = new CacheLoader<String, Boolean>() {
        @Override
        public Boolean load(String key) throws Exception {
          return core.initializedInternal();
        }
      };
      
      if (caching.isInfiniteTtl()) {
        itemCache = CacheBuilder.newBuilder().build(itemLoader);
        allCache = CacheBuilder.newBuilder().build(allLoader);
        executorService = null; 
      } else if (caching.getStaleValuesPolicy() == FeatureStoreCacheConfig.StaleValuesPolicy.EVICT) {
        // We are using an "expire after write" cache. This will evict stale values and block while loading the latest
        // from the underlying data store.

        itemCache = CacheBuilder.newBuilder().expireAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(itemLoader);
        allCache = CacheBuilder.newBuilder().expireAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(allLoader);
        executorService = null;
      } else {
        // We are using a "refresh after write" cache. This will not automatically evict stale values, allowing them
        // to be returned if failures occur when updating them. Optionally set the cache to refresh values asynchronously,
        // which always returns the previously cached value immediately (this is only done for itemCache, not allCache,
        // since retrieving all flags is less frequently needed and we don't want to incur the extra overhead).

        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat(CACHE_REFRESH_THREAD_POOL_NAME_FORMAT).setDaemon(true).build();
        ExecutorService parentExecutor = Executors.newSingleThreadExecutor(threadFactory);
        executorService = MoreExecutors.listeningDecorator(parentExecutor);

        if (caching.getStaleValuesPolicy() == FeatureStoreCacheConfig.StaleValuesPolicy.REFRESH_ASYNC) {
          itemLoader = CacheLoader.asyncReloading(itemLoader, executorService);
        }
        itemCache = CacheBuilder.newBuilder().refreshAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(itemLoader);
        allCache = CacheBuilder.newBuilder().refreshAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(allLoader);        
      }

      if (caching.isInfiniteTtl()) {
        initCache = CacheBuilder.newBuilder().build(initLoader);
      } else {
        initCache = CacheBuilder.newBuilder().expireAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(initLoader);
      }
    }
  }
  
  @Override
  public void close() throws IOException {
    if (executorService != null) {
      executorService.shutdownNow();
    }
    core.close();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends VersionedData> T get(VersionedDataKind<T> kind, String key) {
    if (itemCache != null) {
      Optional<VersionedData> cachedItem = itemCache.getUnchecked(CacheKey.forItem(kind, key));
      if (cachedItem != null) {
        return (T)itemOnlyIfNotDeleted(cachedItem.orNull());
      }
    }
    return (T)itemOnlyIfNotDeleted(core.getInternal(kind, key));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends VersionedData> Map<String, T> all(VersionedDataKind<T> kind) {
    if (allCache != null) {
      Map<String, T> items = (Map<String, T>)allCache.getUnchecked(kind);
      if (items != null) {
        return items;
      }
    }
    return itemsOnlyIfNotDeleted(core.getAllInternal(kind));
  }

  @SuppressWarnings("unchecked")
  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    Map<VersionedDataKind<?>, Map<String, VersionedData>> castMap = // silly generic wildcard problem
        (Map<VersionedDataKind<?>, Map<String, VersionedData>>)((Map<?, ?>)allData);
    try {
      core.initInternal(castMap);
    } catch (RuntimeException e) {
      // Normally, if the underlying store failed to do the update, we do not want to update the cache -
      // the idea being that it's better to stay in a consistent state of having old data than to act
      // like we have new data but then suddenly fall back to old data when the cache expires. However,
      // if the cache TTL is infinite, then it makes sense to update the cache always.
      if (allCache != null && itemCache != null && caching.isInfiniteTtl()) {
        updateAllCache(castMap);
        inited.set(true);
      }
      throw e;
    }

    if (allCache != null && itemCache != null) {
      allCache.invalidateAll();
      itemCache.invalidateAll();
      updateAllCache(castMap);
    }
    inited.set(true);
  }
  
  private void updateAllCache(Map<VersionedDataKind<?>, Map<String, VersionedData>> allData) {
    for (Map.Entry<VersionedDataKind<?>, Map<String, VersionedData>> e0: allData.entrySet()) {
      VersionedDataKind<?> kind = e0.getKey();
      allCache.put(kind, itemsOnlyIfNotDeleted(e0.getValue()));
      for (Map.Entry<String, VersionedData> e1: e0.getValue().entrySet()) {
        itemCache.put(CacheKey.forItem(kind, e1.getKey()), Optional.of(e1.getValue()));
      }
    }
  }
  
  @Override
  public <T extends VersionedData> void delete(VersionedDataKind<T> kind, String key, int version) {
    upsert(kind, kind.makeDeletedItem(key, version));
  }

  @Override
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    VersionedData newState = item;
    RuntimeException failure = null;
    try {
      newState = core.upsertInternal(kind, item);
    } catch (RuntimeException e) {
      failure = e;
    }
    // Normally, if the underlying store failed to do the update, we do not want to update the cache -
    // the idea being that it's better to stay in a consistent state of having old data than to act
    // like we have new data but then suddenly fall back to old data when the cache expires. However,
    // if the cache TTL is infinite, then it makes sense to update the cache always.
    if (failure == null || caching.isInfiniteTtl()) {
      if (itemCache != null) {
        itemCache.put(CacheKey.forItem(kind, item.getKey()), Optional.fromNullable(newState));
      }
      if (allCache != null) {
        // If the cache has a finite TTL, then we should remove the "all items" cache entry to force
        // a reread the next time All is called. However, if it's an infinite TTL, we need to just
        // update the item within the existing "all items" entry (since we want things to still work
        // even if the underlying store is unavailable).
        if (caching.isInfiniteTtl()) {
          try {
            ImmutableMap<String, VersionedData> cachedAll = allCache.get(kind);
            Map<String, VersionedData> newValues = new HashMap<>();
            newValues.putAll(cachedAll);
            newValues.put(item.getKey(), newState);
            allCache.put(kind, ImmutableMap.copyOf(newValues));
          } catch (Exception e) {
            // An exception here means that we did not have a cached value for All, so it tried to query
            // the underlying store, which failed (not surprisingly since it just failed a moment ago
            // when we tried to do an update). This should not happen in infinite-cache mode, but if it
            // does happen, there isn't really anything we can do.
          }
        } else {
          allCache.invalidate(kind);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
  
  @Override
  public boolean initialized() {
    if (inited.get()) {
      return true;
    }
    boolean result;
    if (initCache != null) {
      result = initCache.getUnchecked("arbitrary-key");
    } else {
      result = core.initializedInternal();
    }
    if (result) {
      inited.set(true);
    }
    return result;
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
  public FeatureStoreCore getCore() {
    return core;
  }
  
  private VersionedData itemOnlyIfNotDeleted(VersionedData item) {
    return (item != null && item.isDeleted()) ? null : item;
  }
  
  @SuppressWarnings("unchecked")
  private <T extends VersionedData> ImmutableMap<String, T> itemsOnlyIfNotDeleted(Map<String, ? extends VersionedData> items) {
    ImmutableMap.Builder<String, T> builder = ImmutableMap.builder();
    if (items != null) {
      for (Map.Entry<String, ? extends VersionedData> item: items.entrySet()) {
        if (!item.getValue().isDeleted()) {
          builder.put(item.getKey(), (T) item.getValue());
        }
      }
    }
    return builder.build();
  }
  
  private static class CacheKey {
    final VersionedDataKind<?> kind;
    final String key;
    
    public static CacheKey forItem(VersionedDataKind<?> kind, String key) {
      return new CacheKey(kind, key);
    }
    
    private CacheKey(VersionedDataKind<?> kind, String key) {
      this.kind = kind;
      this.key = key;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof CacheKey) {
        CacheKey o = (CacheKey) other;
        return o.kind.getNamespace().equals(this.kind.getNamespace()) &&
            o.key.equals(this.key);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return kind.getNamespace().hashCode() * 31 + key.hashCode();
    }
  }

  /**
   * Builder for instances of {@link CachingStoreWrapper}.
   */
  public static class Builder {
    private final FeatureStoreCore core;
    private FeatureStoreCacheConfig caching = FeatureStoreCacheConfig.DEFAULT;
    
    Builder(FeatureStoreCore core) {
      this.core = core;
    }

    /**
     * Sets the local caching properties.
     * @param caching a {@link FeatureStoreCacheConfig} object specifying cache parameters
     * @return the builder
     */
    public Builder caching(FeatureStoreCacheConfig caching) {
      this.caching = caching;
      return this;
    }
    
    /**
     * Creates and configures the wrapper object.
     * @return a {@link CachingStoreWrapper} instance
     */
    public CachingStoreWrapper build() {
      return new CachingStoreWrapper(core, caching);
    }
  }
}
