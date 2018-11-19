package com.launchdarkly.client.utils;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.VersionedData;
import com.launchdarkly.client.VersionedDataKind;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CachingStoreWrapper is a partial implementation of {@link FeatureStore} that delegates the basic
 * functionality to an instance of {@link FeatureStoreCore}. It provides optional caching behavior and
 * other logic that would otherwise be repeated in every feature store implementation.
 * 
 * Construct instances of this class with {@link CachingStoreWrapper.Builder}.
 * 
 * @since 4.6.0
 */
public class CachingStoreWrapper implements FeatureStore {
  private static final String CACHE_REFRESH_THREAD_POOL_NAME_FORMAT = "CachingStoreWrapper-refresher-pool-%d";

  private final FeatureStoreCore core;
  private final LoadingCache<CacheKey, Optional<VersionedData>> itemCache;
  private final LoadingCache<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allCache;
  private final LoadingCache<String, Boolean> initCache;
  private final AtomicBoolean inited = new AtomicBoolean(false);
  private final ListeningExecutorService executorService;
  
  protected CachingStoreWrapper(final FeatureStoreCore core, long cacheTime, TimeUnit cacheTimeUnit, boolean refreshStaleValues, boolean asyncRefresh) {
    this.core = core;
    
    if (cacheTime <= 0) {
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
      CacheLoader<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allLoader = new CacheLoader<VersionedDataKind<?>, Map<String, ? extends VersionedData>>() {
        @Override
        public Map<String, ? extends VersionedData> load(VersionedDataKind<?> kind) throws Exception {
          return itemsOnlyIfNotDeleted(core.getAllInternal(kind));
        }
      };
      CacheLoader<String, Boolean> initLoader = new CacheLoader<String, Boolean>() {
        @Override
        public Boolean load(String key) throws Exception {
          return core.initializedInternal();
        }
      };
      
      if (refreshStaleValues) {
        // We are using a "refresh after write" cache. This will not automatically evict stale values, allowing them
        // to be returned if failures occur when updating them. Optionally set the cache to refresh values asynchronously,
        // which always returns the previously cached value immediately (this is only done for itemCache, not allCache,
        // since retrieving all flags is less frequently needed and we don't want to incur the extra overhead).

        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat(CACHE_REFRESH_THREAD_POOL_NAME_FORMAT).setDaemon(true).build();
        ExecutorService parentExecutor = Executors.newSingleThreadExecutor(threadFactory);
        executorService = MoreExecutors.listeningDecorator(parentExecutor);

        if (asyncRefresh) {
          itemLoader = CacheLoader.asyncReloading(itemLoader, executorService);
        }
        itemCache = CacheBuilder.newBuilder().refreshAfterWrite(cacheTime, cacheTimeUnit).build(itemLoader);
        allCache = CacheBuilder.newBuilder().refreshAfterWrite(cacheTime, cacheTimeUnit).build(allLoader);
      } else {
        // We are using an "expire after write" cache. This will evict stale values and block while loading the latest
        // from Redis.

        itemCache = CacheBuilder.newBuilder().expireAfterWrite(cacheTime, cacheTimeUnit).build(itemLoader);
        allCache = CacheBuilder.newBuilder().expireAfterWrite(cacheTime, cacheTimeUnit).build(allLoader);
        executorService = null;
      }

      initCache = CacheBuilder.newBuilder().expireAfterWrite(cacheTime, cacheTimeUnit).build(initLoader);
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
        T item = (T)cachedItem.orNull();
        return itemOnlyIfNotDeleted(item);
      }
    }
    return itemOnlyIfNotDeleted(core.getInternal(kind, key));
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
    return core.getAllInternal(kind);
  }

  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    core.initInternal(allData);
    inited.set(true);
    if (allCache != null && itemCache != null) {
      allCache.invalidateAll();
      itemCache.invalidateAll();
      for (Map.Entry<VersionedDataKind<?>, Map<String, ? extends VersionedData>> e0: allData.entrySet()) {
        VersionedDataKind<?> kind = e0.getKey();
        allCache.put(kind, e0.getValue());
        for (Map.Entry<String, ? extends VersionedData> e1: e0.getValue().entrySet()) {
          itemCache.put(CacheKey.forItem(kind, e1.getKey()), Optional.of((VersionedData)e1.getValue()));
        }
      }
    }
  }
  
  @Override
  public <T extends VersionedData> void delete(VersionedDataKind<T> kind, String key, int version) {
    upsert(kind, kind.makeDeletedItem(key, version));
  }

  @Override
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    VersionedData newState = core.upsertInternal(kind, item);
    if (itemCache != null) {
      itemCache.put(CacheKey.forItem(kind, item.getKey()), Optional.fromNullable(newState));
    }
    if (allCache != null) {
      allCache.invalidate(kind);
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
   * @return the cache statistics object.
   */
  public CacheStats getCacheStats() {
    if (itemCache != null) {
      return itemCache.stats();
    }
    return null;
  }

  private <T extends VersionedData> T itemOnlyIfNotDeleted(T item) {
    return (item != null && item.isDeleted()) ? null : item;
  }
  
  private Map<String, ? extends VersionedData> itemsOnlyIfNotDeleted(Map<String, ? extends VersionedData> items) {
    Map<String, VersionedData> ret = new HashMap<>();
    if (items != null) {
      for (Map.Entry<String, ? extends VersionedData> item: items.entrySet()) {
        if (!item.getValue().isDeleted()) {
          ret.put(item.getKey(), item.getValue());
        }
      }
    }
    return ret;
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
    private long cacheTime;
    private TimeUnit cacheTimeUnit;
    private boolean refreshStaleValues;
    private boolean asyncRefresh;
    
    public Builder(FeatureStoreCore core) {
      this.core = core;
    }
    
    /**
     * Specifies the cache TTL. If {@code cacheTime} is zero or negative, there will be no local caching.
     * Caching is off by default.
     * @param cacheTime the cache TTL, in whatever unit is specified by {@code cacheTimeUnit}
     * @param cacheTimeUnit the time unit
     * @return the same builder
     */
    public Builder cacheTime(long cacheTime, TimeUnit cacheTimeUnit) {
      this.cacheTime = cacheTime;
      this.cacheTimeUnit = cacheTimeUnit;
      return this;
    }
    
    /**
     * Specifies whether the cache (if any) should attempt to refresh stale values instead of evicting them.
     * In this mode, if the refresh fails, the last good value will still be available from the cache.
     * @param refreshStaleValues true if values should be lazily refreshed
     * @return the same builder
     */
    public Builder refreshStaleValues(boolean refreshStaleValues) {
      this.refreshStaleValues = refreshStaleValues;
      return this;
    }
    
    /**
     * Specifies whether cache refreshing should be asynchronous (assuming {@code refreshStaleValues} is true).
     * In this mode, if a cached value has expired, retrieving it will still get the old value but will
     * trigger an attempt to refresh on another thread, rather than blocking until a new value is available.
     * @param asyncRefresh true if values should be asynchronously refreshed
     * @return the same builder
     */
    public Builder asyncRefresh(boolean asyncRefresh) {
      this.asyncRefresh = asyncRefresh;
      return this;
    }
    
    public CachingStoreWrapper build() {
      return new CachingStoreWrapper(core, cacheTime, cacheTimeUnit, refreshStaleValues, asyncRefresh);
    }
  }
}
