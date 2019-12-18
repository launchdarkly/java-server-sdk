package com.launchdarkly.client.utils;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.launchdarkly.client.DataStoreCacheConfig;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.interfaces.DataStoreCore;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CachingStoreWrapper is a partial implementation of {@link DataStore} that delegates the basic
 * functionality to an instance of {@link DataStoreCore}. It provides optional caching behavior and
 * other logic that would otherwise be repeated in every data store implementation. This makes it
 * easier to create new database integrations by implementing only the database-specific logic. 
 * <p>
 * Construct instances of this class with {@link CachingStoreWrapper#builder(DataStoreCore)}.
 * 
 * @since 4.6.0
 */
public class CachingStoreWrapper implements DataStore {
  private static final String CACHE_REFRESH_THREAD_POOL_NAME_FORMAT = "CachingStoreWrapper-refresher-pool-%d";

  private final DataStoreCore core;
  private final LoadingCache<CacheKey, Optional<VersionedData>> itemCache;
  private final LoadingCache<VersionedDataKind<?>, Map<String, VersionedData>> allCache;
  private final LoadingCache<String, Boolean> initCache;
  private final AtomicBoolean inited = new AtomicBoolean(false);
  private final ListeningExecutorService executorService;
  
  /**
   * Creates a new builder.
   * @param core the {@link DataStoreCore} instance
   * @return the builder
   */
  public static CachingStoreWrapper.Builder builder(DataStoreCore core) {
    return new Builder(core);
  }
  
  protected CachingStoreWrapper(final DataStoreCore core, DataStoreCacheConfig caching) {
    this.core = core;
    
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
      CacheLoader<VersionedDataKind<?>, Map<String, VersionedData>> allLoader = new CacheLoader<VersionedDataKind<?>, Map<String, VersionedData>>() {
        @Override
        public Map<String, VersionedData> load(VersionedDataKind<?> kind) throws Exception {
          return itemsOnlyIfNotDeleted(core.getAllInternal(kind));
        }
      };
      CacheLoader<String, Boolean> initLoader = new CacheLoader<String, Boolean>() {
        @Override
        public Boolean load(String key) throws Exception {
          return core.initializedInternal();
        }
      };
      
      switch (caching.getStaleValuesPolicy()) {
      case EVICT:
        // We are using an "expire after write" cache. This will evict stale values and block while loading the latest
        // from the underlying data store.

        itemCache = CacheBuilder.newBuilder().expireAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(itemLoader);
        allCache = CacheBuilder.newBuilder().expireAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(allLoader);
        executorService = null;
        break;
        
      default:
        // We are using a "refresh after write" cache. This will not automatically evict stale values, allowing them
        // to be returned if failures occur when updating them. Optionally set the cache to refresh values asynchronously,
        // which always returns the previously cached value immediately (this is only done for itemCache, not allCache,
        // since retrieving all flags is less frequently needed and we don't want to incur the extra overhead).

        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat(CACHE_REFRESH_THREAD_POOL_NAME_FORMAT).setDaemon(true).build();
        ExecutorService parentExecutor = Executors.newSingleThreadExecutor(threadFactory);
        executorService = MoreExecutors.listeningDecorator(parentExecutor);

        if (caching.getStaleValuesPolicy() == DataStoreCacheConfig.StaleValuesPolicy.REFRESH_ASYNC) {
          itemLoader = CacheLoader.asyncReloading(itemLoader, executorService);
        }
        itemCache = CacheBuilder.newBuilder().refreshAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(itemLoader);
        allCache = CacheBuilder.newBuilder().refreshAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(allLoader);        
      }

      initCache = CacheBuilder.newBuilder().expireAfterWrite(caching.getCacheTime(), caching.getCacheTimeUnit()).build(initLoader);
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
    core.initInternal(castMap);

    inited.set(true);
    
    if (allCache != null && itemCache != null) {
      allCache.invalidateAll();
      itemCache.invalidateAll();
      for (Map.Entry<VersionedDataKind<?>, Map<String, VersionedData>> e0: castMap.entrySet()) {
        VersionedDataKind<?> kind = e0.getKey();
        allCache.put(kind, itemsOnlyIfNotDeleted(e0.getValue()));
        for (Map.Entry<String, VersionedData> e1: e0.getValue().entrySet()) {
          itemCache.put(CacheKey.forItem(kind, e1.getKey()), Optional.of(e1.getValue()));
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
  public DataStoreCore getCore() {
    return core;
  }
  
  private VersionedData itemOnlyIfNotDeleted(VersionedData item) {
    return (item != null && item.isDeleted()) ? null : item;
  }
  
  @SuppressWarnings("unchecked")
  private <T extends VersionedData> Map<String, T> itemsOnlyIfNotDeleted(Map<String, ? extends VersionedData> items) {
    Map<String, T> ret = new HashMap<>();
    if (items != null) {
      for (Map.Entry<String, ? extends VersionedData> item: items.entrySet()) {
        if (!item.getValue().isDeleted()) {
          ret.put(item.getKey(), (T) item.getValue());
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
    private final DataStoreCore core;
    private DataStoreCacheConfig caching = DataStoreCacheConfig.DEFAULT;
    
    Builder(DataStoreCore core) {
      this.core = core;
    }

    /**
     * Sets the local caching properties.
     * @param caching a {@link DataStoreCacheConfig} object specifying cache parameters
     * @return the builder
     */
    public Builder caching(DataStoreCacheConfig caching) {
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
