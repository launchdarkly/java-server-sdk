package com.launchdarkly.client.integrations;

import com.google.common.annotations.VisibleForTesting;
import com.launchdarkly.client.interfaces.DataStoreCore;
import com.launchdarkly.client.interfaces.VersionedData;
import com.launchdarkly.client.interfaces.VersionedDataKind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.launchdarkly.client.utils.DataStoreHelpers.marshalJson;
import static com.launchdarkly.client.utils.DataStoreHelpers.unmarshalJson;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Transaction;
import redis.clients.util.JedisURIHelper;

class RedisDataStoreImpl implements DataStoreCore {
  private static final Logger logger = LoggerFactory.getLogger(RedisDataStoreImpl.class);

  private final JedisPool pool;
  private final String prefix;
  private UpdateListener updateListener;
  
  RedisDataStoreImpl(RedisDataStoreBuilder builder) {
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
        (int)builder.connectTimeout.toMillis(),
        (int)builder.socketTimeout.toMillis(),
        password,
        database,
        null, // clientName
        tls,
        null, // sslSocketFactory
        null, // sslParameters
        null  // hostnameVerifier
        );

    String prefix = (builder.prefix == null || builder.prefix.isEmpty()) ?
        RedisDataStoreBuilder.DEFAULT_PREFIX :
        builder.prefix;
    
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
  
  static interface UpdateListener {
    void aboutToUpdate(String baseKey, String itemKey);
  }
}
