package com.launchdarkly.client;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;

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
  private final JedisPool pool;
  private LoadingCache<CacheKey, Optional<VersionedData>> cache;
  private final LoadingCache<String, Boolean> initCache = createInitCache();
  private String prefix;
  private ListeningExecutorService executorService;

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

  private void createCache(long cacheTimeSecs) {
    createCache(cacheTimeSecs, false, false);
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
        return Optional.<VersionedData>fromNullable(getRedis(key.kind, key.key));
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
      item = getRedis(kind, key);
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
      Gson gson = new Gson();

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
      Gson gson = new Gson();
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
    Jedis jedis = null;
    try {
      Gson gson = new Gson();
      jedis = pool.getResource();
      String baseKey = itemsKey(kind);
      jedis.watch(baseKey);

      VersionedData item = getRedis(kind, key, jedis);

      if (item != null && item.getVersion() >= version) {
        logger.warn("Attempted to delete key: {} version: {}" +
            " with a version that is the same or older: {} in \"{}\"",
            key, item.getVersion(), version, kind.getNamespace());
        return;
      }

      VersionedData deletedItem = kind.makeDeletedItem(key, version);
      jedis.hset(baseKey, key, gson.toJson(deletedItem));

      if (cache != null) {
        cache.invalidate(new CacheKey(kind, key));
      }
    } finally {
      if (jedis != null) {
        jedis.unwatch();
        jedis.close();
      }
    }
  }

  @Override
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    Jedis jedis = null;
    try {
      jedis = pool.getResource();
      Gson gson = new Gson();
      String baseKey = itemsKey(kind);
      jedis.watch(baseKey);

      VersionedData old = getRedisEvenIfDeleted(kind, item.getKey(), jedis);

      if (old != null && old.getVersion() >= item.getVersion()) {
        logger.warn("Attempted to update key: {} version: {}" +
            " with a version that is the same or older: {} in \"{}\"",
            item.getKey(), old.getVersion(), item.getVersion(), kind.getNamespace());
        return;
      }

      jedis.hset(baseKey, item.getKey(), gson.toJson(item));

      if (cache != null) {
        cache.invalidate(new CacheKey(kind, item.getKey()));
      }
    } finally {
      if (jedis != null) {
        jedis.unwatch();
        jedis.close();
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

  private <T extends VersionedData> T getRedis(VersionedDataKind<T> kind, String key) {
    try (Jedis jedis = pool.getResource()) {
      return getRedis(kind, key, jedis);
    }
  }

  private <T extends VersionedData> T getRedis(VersionedDataKind<T> kind, String key, Jedis jedis) {
    T item = getRedisEvenIfDeleted(kind, key, jedis);
    if (item != null && item.isDeleted()) {
      logger.debug("[get] Key: {} has been deleted in \"{}\". Returning null", key, kind.getNamespace());
      return null;
    }
    return item;
  }

  private <T extends VersionedData> T getRedisEvenIfDeleted(VersionedDataKind<T> kind, String key, Jedis jedis) {
    Gson gson = new Gson();
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

}
