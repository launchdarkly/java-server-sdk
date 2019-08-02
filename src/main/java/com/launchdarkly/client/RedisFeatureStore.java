package com.launchdarkly.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheStats;
import com.launchdarkly.client.utils.CachingStoreWrapper;
import com.launchdarkly.client.utils.FeatureStoreCore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.launchdarkly.client.utils.FeatureStoreHelpers.marshalJson;
import static com.launchdarkly.client.utils.FeatureStoreHelpers.unmarshalJson;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Transaction;
import redis.clients.util.JedisURIHelper;

/**
 * An implementation of {@link FeatureStore} backed by Redis. Also
 * supports an optional in-memory cache configuration that can be used to improve performance.
 */
public class RedisFeatureStore implements FeatureStore {
  private static final Logger logger = LoggerFactory.getLogger(RedisFeatureStore.class);

  // Note that we could avoid the indirection of delegating everything to CachingStoreWrapper if we
  // simply returned the wrapper itself as the FeatureStore; however, for historical reasons we can't,
  // because we have already exposed the RedisFeatureStore type.
  private final CachingStoreWrapper wrapper;
  private final Core core;
  
  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    wrapper.init(allData);
  }
  
  @Override
  public <T extends VersionedData> T get(VersionedDataKind<T> kind, String key) {
    return wrapper.get(kind, key);
  }
  
  @Override
  public <T extends VersionedData> Map<String, T> all(VersionedDataKind<T> kind) {
    return wrapper.all(kind);
  }

  @Override
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    wrapper.upsert(kind, item);
  }

  @Override
  public <T extends VersionedData> void delete(VersionedDataKind<T> kind, String key, int version) {
    wrapper.delete(kind, key, version);
  }
  
  @Override
  public boolean initialized() {
    return wrapper.initialized();
  }
  
  @Override
  public void close() throws IOException {
    wrapper.close();
  }
  
  /**
   * Return the underlying Guava cache stats object.
   *
   * @return the cache statistics object.
   */
  public CacheStats getCacheStats() {
    return wrapper.getCacheStats();
  }

  /**
   * Creates a new store instance that connects to Redis based on the provided {@link RedisFeatureStoreBuilder}.
   * <p>
   * See the {@link RedisFeatureStoreBuilder} for information on available configuration options and what they do.
   *
   * @param builder the configured builder to construct the store with.
   */
  protected RedisFeatureStore(RedisFeatureStoreBuilder builder) {
    // There is no builder for JedisPool, just a large number of constructor overloads. Unfortunately,
    // the overloads that accept a URI do not accept the other parameters we need to set, so we need
    // to decompose the URI.
    String host = builder.uri.getHost();
    int port = builder.uri.getPort();
    String password = builder.password == null ? JedisURIHelper.getPassword(builder.uri) : builder.password;
    int database = builder.database == null ? JedisURIHelper.getDBIndex(builder.uri): builder.database.intValue();
    boolean tls = builder.tls || builder.uri.getScheme().equals("rediss");
    
    String extra = tls ? " with TLS" : "";
    if (password != null) {
      extra = extra + (extra.isEmpty() ? " with" : " and") + " password";
    }
    logger.info(String.format("Connecting to Redis feature store at %s:%d/%d%s", host, port, database, extra));

    JedisPoolConfig poolConfig = (builder.poolConfig != null) ? builder.poolConfig : new JedisPoolConfig();    
    JedisPool pool = new JedisPool(poolConfig,
        host,
        port,
        builder.connectTimeout,
        builder.socketTimeout,
        password,
        database,
        null, // clientName
        tls,
        null, // sslSocketFactory
        null, // sslParameters
        null  // hostnameVerifier
        );

    String prefix = (builder.prefix == null || builder.prefix.isEmpty()) ?
        RedisFeatureStoreBuilder.DEFAULT_PREFIX :
        builder.prefix;
    
    this.core = new Core(pool, prefix);
    this.wrapper = CachingStoreWrapper.builder(this.core).caching(builder.caching)
        .build();
  }

  /**
   * Creates a new store instance that connects to Redis with a default connection (localhost port 6379) and no in-memory cache.
   * @deprecated Please use {@link Components#redisFeatureStore()} instead.
   */
  public RedisFeatureStore() {
    this(new RedisFeatureStoreBuilder().caching(FeatureStoreCacheConfig.disabled()));
  }

  static class Core implements FeatureStoreCore {    
    private final JedisPool pool;
    private final String prefix;
    private UpdateListener updateListener;
    
    Core(JedisPool pool, String prefix) {
      this.pool = pool;
      this.prefix = prefix;
    }
    
    @Override
    public VersionedData getInternal(VersionedDataKind<?> kind, String key) {
      try (Jedis jedis = pool.getResource()) {
        VersionedData item = getRedis(kind, key, jedis);
        if (item != null) {
          logger.debug("[get] Key: {} with version: {} found in \"{}\".", key, item.getVersion(), kind.getNamespace());
        }
        return item;
      }
    }

    @Override
    public Map<String, VersionedData> getAllInternal(VersionedDataKind<?> kind) {
      try (Jedis jedis = pool.getResource()) {
        Map<String, String> allJson = jedis.hgetAll(itemsKey(kind));
        Map<String, VersionedData> result = new HashMap<>();

        for (Map.Entry<String, String> entry : allJson.entrySet()) {
          VersionedData item = unmarshalJson(kind, entry.getValue());
          result.put(entry.getKey(), item);
        }
        return result;
      }
    }
    
    @Override
    public void initInternal(Map<VersionedDataKind<?>, Map<String, VersionedData>> allData) {
      try (Jedis jedis = pool.getResource()) {
        Transaction t = jedis.multi();

        for (Map.Entry<VersionedDataKind<?>, Map<String, VersionedData>> entry: allData.entrySet()) {
          String baseKey = itemsKey(entry.getKey()); 
          t.del(baseKey);
          for (VersionedData item: entry.getValue().values()) {
            t.hset(baseKey, item.getKey(), marshalJson(item));
          }
        }

        t.set(initedKey(), "");
        t.exec();
      }
    }
    
    @Override
    public VersionedData upsertInternal(VersionedDataKind<?> kind, VersionedData newItem) {
      while (true) {
        Jedis jedis = null;
        try {
          jedis = pool.getResource();
          String baseKey = itemsKey(kind);
          jedis.watch(baseKey);
    
          if (updateListener != null) {
            updateListener.aboutToUpdate(baseKey, newItem.getKey());
          }
          
          VersionedData oldItem = getRedis(kind, newItem.getKey(), jedis);
    
          if (oldItem != null && oldItem.getVersion() >= newItem.getVersion()) {
            logger.debug("Attempted to {} key: {} version: {}" +
                " with a version that is the same or older: {} in \"{}\"",
                newItem.isDeleted() ? "delete" : "update",
                newItem.getKey(), oldItem.getVersion(), newItem.getVersion(), kind.getNamespace());
            return oldItem;
          }
    
          Transaction tx = jedis.multi();
          tx.hset(baseKey, newItem.getKey(), marshalJson(newItem));
          List<Object> result = tx.exec();
          if (result.isEmpty()) {
            // if exec failed, it means the watch was triggered and we should retry
            logger.debug("Concurrent modification detected, retrying");
            continue;
          }
    
          return newItem;
        } finally {
          if (jedis != null) {
            jedis.unwatch();
            jedis.close();
          }
        }
      }
    }
    
    @Override
    public boolean initializedInternal() {
      try (Jedis jedis = pool.getResource()) {
        return jedis.exists(initedKey());
      }
    }
    
    @Override
    public void close() throws IOException {
      logger.info("Closing LaunchDarkly RedisFeatureStore");
      pool.destroy();
    }

    @VisibleForTesting
    void setUpdateListener(UpdateListener updateListener) {
      this.updateListener = updateListener;
    }
    
    private String itemsKey(VersionedDataKind<?> kind) {
      return prefix + ":" + kind.getNamespace();
    }
    
    private String initedKey() {
      return prefix + ":$inited";
    }
    
    private <T extends VersionedData> T getRedis(VersionedDataKind<T> kind, String key, Jedis jedis) {
      String json = jedis.hget(itemsKey(kind), key);

      if (json == null) {
        logger.debug("[get] Key: {} not found in \"{}\". Returning null", key, kind.getNamespace());
        return null;
      }

      return unmarshalJson(kind, json);
    }
  }

  static interface UpdateListener {
    void aboutToUpdate(String baseKey, String itemKey);
  }
  
  @VisibleForTesting
  void setUpdateListener(UpdateListener updateListener) {
    core.setUpdateListener(updateListener);
  }
}
