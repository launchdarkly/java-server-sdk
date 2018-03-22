package com.launchdarkly.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Transaction;

/**
 * An implementation of {@link FeatureStore} backed by Redis. Also
 * supports an optional in-memory cache configuration that can be used to improve performance.
 */
public class RedisFeatureStore implements FeatureStore {
  private static final Logger logger = LoggerFactory.getLogger(RedisFeatureStore.class);
  private static final String DEFAULT_PREFIX = "launchdarkly";
  private static final String INIT_KEY = "$initialized$";
  private static final String CACHE_REFRESH_THREAD_POOL_NAME_FORMAT = "RedisFeatureStore-cache-refresher-pool-%d";
  private static final Gson gson = new Gson();
  
  private final JedisPool pool;
  private LoadingCache<CacheKey, Optional<VersionedData>> cache;
  private final LoadingCache<String, Boolean> initCache = createInitCache();
  private String prefix;
  private ListeningExecutorService executorService;
  private UpdateListener updateListener;
  
  private static class CacheKey {
    final VersionedDataKind<?> kind;
    final String key;
    
    public CacheKey(VersionedDataKind<?> kind, String key) {
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
   * Creates a new store instance that connects to Redis based on the provided {@link RedisFeatureStoreBuilder}.
   * <p>
   * See the {@link RedisFeatureStoreBuilder} for information on available configuration options and what they do.
   *
   * @param builder the configured builder to construct the store with.
   */
  protected RedisFeatureStore(RedisFeatureStoreBuilder builder) {
    if (builder.poolConfig == null) {
      this.pool = new JedisPool(getPoolConfig(), builder.uri, builder.connectTimeout, builder.socketTimeout);
    } else {
      this.pool = new JedisPool(builder.poolConfig, builder.uri, builder.connectTimeout, builder.socketTimeout);
    }
    setPrefix(builder.prefix);
    createCache(builder.cacheTimeSecs, builder.refreshStaleValues, builder.asyncRefresh);
  }

  /**
   * Creates a new store instance that connects to Redis with a default connection (localhost port 6379) and no in-memory cache.
   */
  public RedisFeatureStore() {
    pool = new JedisPool(getPoolConfig(), "localhost");
    this.prefix = DEFAULT_PREFIX;
  }

  private void setPrefix(String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      this.prefix = DEFAULT_PREFIX;
    } else {
      this.prefix = prefix;
    }
  }

  private void createCache(long cacheTimeSecs, boolean refreshStaleValues, boolean asyncRefresh) {
    if (cacheTimeSecs > 0) {
      if (refreshStaleValues) {
        createRefreshCache(cacheTimeSecs, asyncRefresh);
      } else {
        createExpiringCache(cacheTimeSecs);
      }
    }
  }

  private CacheLoader<CacheKey, Optional<VersionedData>> createDefaultCacheLoader() {
    return new CacheLoader<CacheKey, Optional<VersionedData>>() {
      @Override
      public Optional<VersionedData> load(CacheKey key) throws Exception {
        try (Jedis jedis = pool.getResource()) {
          return Optional.<VersionedData>fromNullable(getRedisEvenIfDeleted(key.kind, key.key, jedis));
        }
      }
    };
  }

  /**
   * Configures the instance to use a "refresh after write" cache. This will not automatically evict stale values, allowing them to be returned if failures
   * occur when updating them. Optionally set the cache to refresh values asynchronously, which always returns the previously cached value immediately.
   *
   * @param cacheTimeSecs the length of time in seconds, after a {@link FeatureFlag} value is created that it should be refreshed.
   * @param asyncRefresh  makes the refresh asynchronous or not.
   */
  private void createRefreshCache(long cacheTimeSecs, boolean asyncRefresh) {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat(CACHE_REFRESH_THREAD_POOL_NAME_FORMAT).setDaemon(true).build();
    ExecutorService parentExecutor = Executors.newSingleThreadExecutor(threadFactory);
    executorService = MoreExecutors.listeningDecorator(parentExecutor);
    CacheLoader<CacheKey, Optional<VersionedData>> cacheLoader = createDefaultCacheLoader();
    if (asyncRefresh) {
      cacheLoader = CacheLoader.asyncReloading(cacheLoader, executorService);
    }
    cache = CacheBuilder.newBuilder().refreshAfterWrite(cacheTimeSecs, TimeUnit.SECONDS).build(cacheLoader);
  }

  /**
   * Configures the instance to use an "expire after write" cache. This will evict stale values and block while loading the latest from Redis.
   *
   * @param cacheTimeSecs the length of time in seconds, after a {@link FeatureFlag} value is created that it should be automatically removed.
   */
  private void createExpiringCache(long cacheTimeSecs) {
    cache = CacheBuilder.newBuilder().expireAfterWrite(cacheTimeSecs, TimeUnit.SECONDS).build(createDefaultCacheLoader());
  }

  private LoadingCache<String, Boolean> createInitCache() {
    // Note that this cache does not expire - it's being used only for memoization.
    return CacheBuilder.newBuilder().build(new CacheLoader<String, Boolean>() {
      @Override
      public Boolean load(String key) throws Exception {
        return getInit();
      }
    });
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends VersionedData> T get(VersionedDataKind<T> kind, String key) {
    T item;
    if (cache != null) {
      item = (T) cache.getUnchecked(new CacheKey(kind, key)).orNull();
    } else {
      try (Jedis jedis = pool.getResource()) {
        item = getRedisEvenIfDeleted(kind, key, jedis);
      }
    }
    if (item != null && item.isDeleted()) {
      logger.debug("[get] Key: {} has been deleted in \"{}\". Returning null", key, kind.getNamespace());
      return null;
    }
    if (item != null) {
      logger.debug("[get] Key: {} with version: {} found in \"{}\".", key, item.getVersion(), kind.getNamespace());
    }
    return item;
  }

  @Override
  public <T extends VersionedData> Map<String, T> all(VersionedDataKind<T> kind) {
    try (Jedis jedis = pool.getResource()) {
      Map<String, String> allJson = jedis.hgetAll(itemsKey(kind));
      Map<String, T> result = new HashMap<>();

      for (Map.Entry<String, String> entry : allJson.entrySet()) {
        T item = gson.fromJson(entry.getValue(), kind.getItemClass());
        if (!item.isDeleted()) {
          result.put(entry.getKey(), item);
        }
      }
      return result;
    }
  }

  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    try (Jedis jedis = pool.getResource()) {
      Transaction t = jedis.multi();

      for (Map.Entry<VersionedDataKind<?>, Map<String, ? extends VersionedData>> entry: allData.entrySet()) {
        String baseKey = itemsKey(entry.getKey()); 
        t.del(baseKey);
        for (VersionedData item: entry.getValue().values()) {
          t.hset(baseKey, item.getKey(), gson.toJson(item));
        }
      }

      t.exec();
    }
    cache.invalidateAll();
    initCache.put(INIT_KEY, true);
  }

  @Override
  public <T extends VersionedData> void delete(VersionedDataKind<T> kind, String key, int version) {
    T deletedItem = kind.makeDeletedItem(key, version);
    updateItemWithVersioning(kind, deletedItem);
  }
  
  @Override
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    updateItemWithVersioning(kind, item);
  }

  private <T extends VersionedData> void updateItemWithVersioning(VersionedDataKind<T> kind, T newItem) {
    while (true) {
      Jedis jedis = null;
      try {
        jedis = pool.getResource();
        String baseKey = itemsKey(kind);
        jedis.watch(baseKey);
  
        if (updateListener != null) {
          updateListener.aboutToUpdate(baseKey, newItem.getKey());
        }
        
        VersionedData oldItem = getRedisEvenIfDeleted(kind, newItem.getKey(), jedis);
  
        if (oldItem != null && oldItem.getVersion() >= newItem.getVersion()) {
          logger.warn("Attempted to {} key: {} version: {}" +
              " with a version that is the same or older: {} in \"{}\"",
              newItem.isDeleted() ? "delete" : "update",
              newItem.getKey(), oldItem.getVersion(), newItem.getVersion(), kind.getNamespace());
          return;
        }
  
        Transaction tx = jedis.multi();
        tx.hset(baseKey, newItem.getKey(), gson.toJson(newItem));
        List<Object> result = tx.exec();
        if (result.isEmpty()) {
          // if exec failed, it means the watch was triggered and we should retry
          logger.debug("Concurrent modification detected, retrying");
          continue;
        }
  
        if (cache != null) {
          cache.invalidate(new CacheKey(kind, newItem.getKey()));
        }
      } finally {
        if (jedis != null) {
          jedis.unwatch();
          jedis.close();
        }
      }
    }
  }

  @Override
  public boolean initialized() {
    // The LoadingCache takes care of both coalescing multiple simultaneous requests and memoizing
    // the result, so we'll only ever query Redis once for this (if at all - the Redis query will
    // be skipped if the cache was explicitly set by init()).
    return initCache.getUnchecked(INIT_KEY);
  }

  /**
   * Releases all resources associated with the store. The store must no longer be used once closed.
   *
   * @throws IOException if an underlying service threw an exception
   */
  public void close() throws IOException {
    logger.info("Closing LaunchDarkly RedisFeatureStore");
    try {
      if (executorService != null) {
        executorService.shutdownNow();
      }
    } finally {
      pool.destroy();
    }
  }

  /**
   * Return the underlying Guava cache stats object.
   *
   * @return the cache statistics object.
   */
  public CacheStats getCacheStats() {
    if (cache != null) {
      return cache.stats();
    }
    return null;
  }

  private String itemsKey(VersionedDataKind<?> kind) {
    return prefix + ":" + kind.getNamespace();
  }

  private Boolean getInit() {
    try (Jedis jedis = pool.getResource()) {
      return jedis.exists(itemsKey(FEATURES));
    }
  }

  private <T extends VersionedData> T getRedisEvenIfDeleted(VersionedDataKind<T> kind, String key, Jedis jedis) {
    String json = jedis.hget(itemsKey(kind), key);

    if (json == null) {
      logger.debug("[get] Key: {} not found in \"{}\". Returning null", key, kind.getNamespace());
      return null;
    }

    return gson.fromJson(json, kind.getItemClass());
  }

  private static JedisPoolConfig getPoolConfig() {
    return new JedisPoolConfig();
  }

  static interface UpdateListener {
    void aboutToUpdate(String baseKey, String itemKey);
  }
  
  @VisibleForTesting
  void setUpdateListener(UpdateListener updateListener) {
    this.updateListener = updateListener;
  }
}
