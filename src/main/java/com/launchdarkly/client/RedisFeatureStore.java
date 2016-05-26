package com.launchdarkly.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.launchdarkly.client.flag.FeatureFlag;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Transaction;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A thread-safe, versioned store for {@link FeatureFlag} objects backed by Redis. Also
 * supports an optional in-memory cache configuration that can be used to improve performance.
 *
 */
public class RedisFeatureStore implements FeatureStore {
  private static final String DEFAULT_PREFIX = "launchdarkly";
  private static final String INIT_KEY = "$initialized$";
  private static final String CACHE_REFRESH_THREAD_POOL_NAME_FORMAT = "RedisFeatureStore-cache-refresher-pool-%d";
  private final JedisPool pool;
  private LoadingCache<String, FeatureFlag> cache;
  private LoadingCache<String, Boolean> initCache;
  private String prefix;
  private ListeningExecutorService executorService;

  /**
   * Creates a new store instance that connects to Redis with the provided host, port, prefix, and cache timeout. Uses a default
   * connection pool configuration.
   *
   * @param host the host for the Redis connection
   * @param port the port for the Redis connection
   * @param prefix a namespace prefix for all keys stored in Redis
   * @param cacheTimeSecs an optional timeout for the in-memory cache. If set to 0, no in-memory caching will be performed
   * @deprecated as of 1.1. Please use the {@link RedisFeatureStoreBuilder#build()} for a more flexible way of constructing a {@link RedisFeatureStore}.
   */
  @Deprecated
  public RedisFeatureStore(String host, int port, String prefix, long cacheTimeSecs) {
    this(host, port, prefix, cacheTimeSecs, getPoolConfig());
  }

  /**
   * Creates a new store instance that connects to Redis with the provided URI, prefix, and cache timeout. Uses a default
   * connection pool configuration.
   *
   * @param uri the URI for the Redis connection
   * @param prefix a namespace prefix for all keys stored in Redis
   * @param cacheTimeSecs an optional timeout for the in-memory cache. If set to 0, no in-memory caching will be performed
   * @deprecated as of 1.1. Please use the {@link RedisFeatureStoreBuilder#build()} for a more flexible way of constructing a {@link RedisFeatureStore}.
   */
  @Deprecated
  public RedisFeatureStore(URI uri, String prefix, long cacheTimeSecs) {
    this(uri, prefix, cacheTimeSecs, getPoolConfig());
  }

  /**
   * Creates a new store instance that connects to Redis with the provided host, port, prefix, cache timeout, and connection pool settings.
   *
   * @param host the host for the Redis connection
   * @param port the port for the Redis connection
   * @param prefix a namespace prefix for all keys stored in Redis
   * @param cacheTimeSecs an optional timeout for the in-memory cache. If set to 0, no in-memory caching will be performed
   * @param poolConfig an optional pool config for the Jedis connection pool
   * @deprecated as of 1.1. Please use the {@link RedisFeatureStoreBuilder#build()} for a more flexible way of constructing a {@link RedisFeatureStore}.
   */
  @Deprecated
  public RedisFeatureStore(String host, int port, String prefix, long cacheTimeSecs, JedisPoolConfig poolConfig) {
    pool = new JedisPool(poolConfig, host, port);
    setPrefix(prefix);
    createCache(cacheTimeSecs);
    createInitCache(cacheTimeSecs);
  }

  /**
   * Creates a new store instance that connects to Redis with the provided URI, prefix, cache timeout, and connection pool settings.
   *
   * @param uri the URI for the Redis connection
   * @param prefix a namespace prefix for all keys stored in Redis
   * @param cacheTimeSecs an optional timeout for the in-memory cache. If set to 0, no in-memory caching will be performed
   * @param poolConfig an optional pool config for the Jedis connection pool
   * @deprecated as of 1.1. Please use the {@link RedisFeatureStoreBuilder#build()} for a more flexible way of constructing a {@link RedisFeatureStore}.
   */
  @Deprecated
  public RedisFeatureStore(URI uri, String prefix, long cacheTimeSecs, JedisPoolConfig poolConfig) {
    pool = new JedisPool(poolConfig, uri);
    setPrefix(prefix);
    createCache(cacheTimeSecs);
    createInitCache(cacheTimeSecs);
  }

  /**
   * Creates a new store instance that connects to Redis based on the provided {@link RedisFeatureStoreBuilder}.
   *
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
    createInitCache(builder.cacheTimeSecs);
  }

  /**
   * Creates a new store instance that connects to Redis with a default connection (localhost port 6379) and no in-memory cache.
   *
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

  private CacheLoader<String, FeatureFlag> createDefaultCacheLoader() {
    return new CacheLoader<String, FeatureFlag>() {
      @Override
      public FeatureFlag load(String key) throws Exception {
        return getRedis(key);
      }
    };
  }

  /**
   * Configures the instance to use a "refresh after write" cache. This will not automatically evict stale values, allowing them to be returned if failures
   * occur when updating them. Optionally set the cache to refresh values asynchronously, which always returns the previously cached value immediately.
   * @param cacheTimeSecs the length of time in seconds, after a {@link FeatureFlag} value is created that it should be refreshed.
   * @param asyncRefresh makes the refresh asynchronous or not.
   */
  private void createRefreshCache(long cacheTimeSecs, boolean asyncRefresh) {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat(CACHE_REFRESH_THREAD_POOL_NAME_FORMAT).setDaemon(true).build();
    ExecutorService parentExecutor = Executors.newSingleThreadExecutor(threadFactory);
    executorService = MoreExecutors.listeningDecorator(parentExecutor);
    CacheLoader<String, FeatureFlag> cacheLoader = createDefaultCacheLoader();
    if (asyncRefresh) {
      cacheLoader = CacheLoader.asyncReloading(cacheLoader, executorService);
    }
    cache = CacheBuilder.newBuilder().refreshAfterWrite(cacheTimeSecs, TimeUnit.SECONDS).build(cacheLoader);
  }

  /**
   * Configures the instance to use an "expire after write" cache. This will evict stale values and block while loading the latest from Redis.
   * @param cacheTimeSecs the length of time in seconds, after a {@link FeatureFlag} value is created that it should be automatically removed.
   */
  private void createExpiringCache(long cacheTimeSecs) {
    cache = CacheBuilder.newBuilder().expireAfterWrite(cacheTimeSecs, TimeUnit.SECONDS).build(createDefaultCacheLoader());
  }

  private void createInitCache(long cacheTimeSecs) {
    if (cacheTimeSecs > 0) {
      initCache = CacheBuilder.newBuilder().expireAfterWrite(cacheTimeSecs, TimeUnit.SECONDS).build(new CacheLoader<String, Boolean>() {
        @Override
        public Boolean load(String key) throws Exception {
          return getInit();
        }
      });
    }
  }

  /**
   * Returns the {@link FeatureFlag} to which the specified key is mapped, or
   * null if the key is not associated or the associated {@link FeatureFlag} has
   * been deleted.
   *
   * @param key the key whose associated {@link FeatureFlag} is to be returned
   * @return the {@link FeatureFlag} to which the specified key is mapped, or
   * null if the key is not associated or the associated {@link FeatureFlag} has
   * been deleted.
   */
  @Override
  public FeatureFlag get(String key) {
    if (cache != null) {
      return cache.getUnchecked(key);
    } else {
      return getRedis(key);
    }
  }

  /**
   * Returns a {@link java.util.Map} of all associated features. This implementation does not take advantage
   * of the in-memory cache, so fetching all features will involve a fetch from Redis.
   *
   * @return a map of all associated features.
   */
  @Override
  public Map<String, FeatureFlag> all() {
    try (Jedis jedis = pool.getResource()) {
      Map<String,String> featuresJson = jedis.hgetAll(featuresKey());
      Map<String, FeatureFlag> result = new HashMap<>();
      Gson gson = new Gson();

      Type type = new TypeToken<FeatureFlag>() {}.getType();

      for (Map.Entry<String, String> entry : featuresJson.entrySet()) {
        FeatureFlag rep =  gson.fromJson(entry.getValue(), type);
        result.put(entry.getKey(), rep);
      }
      return result;
    }
  }

  /**
   * Initializes (or re-initializes) the store with the specified set of features. Any existing entries
   * will be removed.
   *
   * @param features the features to set the store
   */
  @Override
  public void init(Map<String, FeatureFlag> features) {
    try (Jedis jedis = pool.getResource()) {
      Gson gson = new Gson();
      Transaction t = jedis.multi();

      t.del(featuresKey());

      for (FeatureFlag f: features.values()) {
        t.hset(featuresKey(), f.getKey(), gson.toJson(f));
      }

      t.exec();
    }
  }

  /**
   * Deletes the feature associated with the specified key, if it exists and its version
   * is less than or equal to the specified version.
   *
   * @param key the key of the feature to be deleted
   * @param version the version for the delete operation
   */
  @Override
  public void delete(String key, int version) {
    try (Jedis jedis = pool.getResource()) {
      Gson gson = new Gson();
      jedis.watch(featuresKey());

      FeatureFlag feature = getRedis(key);

      if (feature != null && feature.getVersion() >= version) {
        return;
      }

      feature.setDeleted();
      feature.setVersion(version);

      jedis.hset(featuresKey(), key, gson.toJson(feature));

      if (cache != null) {
        cache.invalidate(key);
      }
    }
  }

  /**
   * Update or insert the feature associated with the specified key, if its version
   * is less than or equal to the version specified in the argument feature.
   *
   * @param key
   * @param feature
   */
  @Override
  public void upsert(String key, FeatureFlag feature) {
    try (Jedis jedis = pool.getResource()) {
      Gson gson = new Gson();
      jedis.watch(featuresKey());

      FeatureFlag f = getRedis(key);

      if (f != null && f.getVersion() >= feature.getVersion()) {
        return;
      }

      jedis.hset(featuresKey(), key, gson.toJson(feature));

      if (cache != null) {
        cache.invalidate(key);
      }
    }
  }

  /**
   * Returns true if this store has been initialized
   *
   * @return true if this store has been initialized
   */
  @Override
  public boolean initialized() {
    if (initCache != null) {
      Boolean initialized = initCache.getUnchecked(INIT_KEY);

      if (initialized != null && initialized) {
        return true;
      }
    }

    return getInit();
  }

  /**
   * Releases all resources associated with the store. The store must no longer be used once closed.
   * @throws IOException
   */
  public void close() throws IOException
  {
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

  private String featuresKey() {
    return prefix + ":features";
  }

  private Boolean getInit() {
    try (Jedis jedis = pool.getResource()) {
      return jedis.exists(featuresKey());
    }
  }

  private FeatureFlag getRedis(String key) {
    try (Jedis jedis = pool.getResource()){
      Gson gson = new Gson();
      String featureJson = jedis.hget(featuresKey(), key);

      if (featureJson == null) {
        return null;
      }

      Type type = new TypeToken<FeatureFlag>() {}.getType();
      FeatureFlag f = gson.fromJson(featureJson, type);

      return f.isDeleted() ? null : f;
    }
  }

  private static final JedisPoolConfig getPoolConfig() {
    JedisPoolConfig config = new JedisPoolConfig();
    return config;
  }

}
