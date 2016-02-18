package com.launchdarkly.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import java.util.concurrent.TimeUnit;

/**
 * A thread-safe, versioned store for {@link com.launchdarkly.client.FeatureRep} objects backed by Redis. Also
 * supports an optional in-memory cache configuration that can be used to improve performance.
 *
 */
public class RedisFeatureStore implements FeatureStore {
  private static final String DEFAULT_PREFIX = "launchdarkly";
  private final JedisPool pool;
  private LoadingCache<String, FeatureRep<?>> cache;
  private LoadingCache<String, Boolean> initCache;
  private String prefix;
  private static final String INIT_KEY = "$initialized$";

  /**
   *
   * @param host the host for the Redis connection
   * @param port the port for the Redis connection
   * @param prefix a namespace prefix for all keys stored in Redis
   * @param cacheTimeSecs an optional timeout for the in-memory cache. If set to 0, no in-memory caching will be performed
   */
  public RedisFeatureStore(String host, int port, String prefix, long cacheTimeSecs) {
    pool = new JedisPool(getPoolConfig(), host, port);
    setPrefix(prefix);
    createCache(cacheTimeSecs);
    createInitCache(cacheTimeSecs);
  }

  /**
   * Creates a new store instance that connects to Redis with the provided URI, prefix, and cache timeout.
   *
   * @param uri the URI for the Redis connection
   * @param prefix a namespace prefix for all keys stored in Redis
   * @param cacheTimeSecs an optional timeout for the in-memory cache. If set to 0, no in-memory caching will be performed
   */
  public RedisFeatureStore(URI uri, String prefix, long cacheTimeSecs) {
    pool = new JedisPool(getPoolConfig(), uri);
    setPrefix(prefix);
    createCache(cacheTimeSecs);
    createInitCache(cacheTimeSecs);
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
    if (cacheTimeSecs > 0) {
      cache = CacheBuilder.newBuilder().expireAfterWrite(cacheTimeSecs, TimeUnit.SECONDS).build(new CacheLoader<String, FeatureRep<?>>() {

        @Override
        public FeatureRep<?> load(String key) throws Exception {
          return getRedis(key);
        }
      });
    }
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
   *
   * Returns the {@link com.launchdarkly.client.FeatureRep} to which the specified key is mapped, or
   * null if the key is not associated or the associated {@link com.launchdarkly.client.FeatureRep} has
   * been deleted.
   *
   * @param key the key whose associated {@link com.launchdarkly.client.FeatureRep} is to be returned
   * @return the {@link com.launchdarkly.client.FeatureRep} to which the specified key is mapped, or
   * null if the key is not associated or the associated {@link com.launchdarkly.client.FeatureRep} has
   * been deleted.
   */
  @Override
  public FeatureRep<?> get(String key) {
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
   *
   * @return a map of all associated features.
   */
  @Override
  public Map<String, FeatureRep<?>> all() {
    try (Jedis jedis = pool.getResource()) {
      Map<String,String> featuresJson = jedis.hgetAll(featuresKey());
      Map<String, FeatureRep<?>> result = new HashMap<String, FeatureRep<?>>();
      Gson gson = new Gson();

      Type type = new TypeToken<FeatureRep<?>>() {}.getType();

      for (Map.Entry<String, String> entry : featuresJson.entrySet()) {
        FeatureRep<?> rep =  gson.fromJson(entry.getValue(), type);
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
  public void init(Map<String, FeatureRep<?>> features) {
    try (Jedis jedis = pool.getResource()) {
      Gson gson = new Gson();
      Transaction t = jedis.multi();

      t.del(featuresKey());

      for (FeatureRep<?> f: features.values()) {
        t.hset(featuresKey(), f.key, gson.toJson(f));
      }

      t.exec();
    }
  }


  /**
   *
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

      FeatureRep<?> feature = getRedis(key);

      if (feature != null && feature.version >= version) {
        return;
      }

      feature.deleted = true;
      feature.version = version;

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
  public void upsert(String key, FeatureRep<?> feature) {
    try (Jedis jedis = pool.getResource()) {
      Gson gson = new Gson();
      jedis.watch(featuresKey());

      FeatureRep<?> f = getRedis(key);

      if (f != null && f.version >= feature.version) {
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

      if (initialized != null && initialized.booleanValue()) {
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
    pool.destroy();
  }



  private String featuresKey() {
    return prefix + ":features";
  }

  private Boolean getInit() {
    try (Jedis jedis = pool.getResource()) {
      return jedis.exists(featuresKey());
    }
  }

  private FeatureRep<?> getRedis(String key) {
    try (Jedis jedis = pool.getResource()){
      Gson gson = new Gson();
      String featureJson = jedis.hget(featuresKey(), key);

      if (featureJson == null) {
        return null;
      }

      Type type = new TypeToken<FeatureRep<?>>() {}.getType();
      FeatureRep<?> f = gson.fromJson(featureJson, type);

      return f.deleted ? null : f;
    }
  }

  private final JedisPoolConfig getPoolConfig() {
    JedisPoolConfig config = new JedisPoolConfig();
    return config;
  }

}
