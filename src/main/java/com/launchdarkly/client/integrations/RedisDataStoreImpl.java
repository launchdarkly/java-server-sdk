package com.launchdarkly.client.integrations;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.launchdarkly.client.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.client.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.client.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.client.interfaces.DataStoreTypes.KeyedItems;
import com.launchdarkly.client.interfaces.DataStoreTypes.SerializedItemDescriptor;
import com.launchdarkly.client.interfaces.PersistentDataStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Transaction;
import redis.clients.util.JedisURIHelper;

final class RedisDataStoreImpl implements PersistentDataStore {
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
  public SerializedItemDescriptor get(DataKind kind, String key) {
    try (Jedis jedis = pool.getResource()) {
      String item = getRedis(kind, key, jedis);
      return item == null ? null : new SerializedItemDescriptor(0, false, item);
    }
  }

  @Override
  public KeyedItems<SerializedItemDescriptor> getAll(DataKind kind) {
    try (Jedis jedis = pool.getResource()) {
      Map<String, String> allJson = jedis.hgetAll(itemsKey(kind));
      return new KeyedItems<>(
          Maps.transformValues(allJson, itemJson -> new SerializedItemDescriptor(0, false, itemJson)).entrySet()
          );
    }
  }
  
  @Override
  public void init(FullDataSet<SerializedItemDescriptor> allData) {
    try (Jedis jedis = pool.getResource()) {
      Transaction t = jedis.multi();

      for (Map.Entry<DataKind, KeyedItems<SerializedItemDescriptor>> e0: allData.getData()) {
        DataKind kind = e0.getKey();
        String baseKey = itemsKey(kind); 
        t.del(baseKey);
        for (Map.Entry<String, SerializedItemDescriptor> e1: e0.getValue().getItems()) {
          t.hset(baseKey, e1.getKey(), jsonOrPlaceholder(kind, e1.getValue()));
        }
      }

      t.set(initedKey(), "");
      t.exec();
    }
  }
  
  @Override
  public boolean upsert(DataKind kind, String key, SerializedItemDescriptor newItem) {
    while (true) {
      Jedis jedis = null;
      try {
        jedis = pool.getResource();
        String baseKey = itemsKey(kind);
        jedis.watch(baseKey);
  
        if (updateListener != null) {
          updateListener.aboutToUpdate(baseKey, key);
        }
        
        String oldItemJson = getRedis(kind, key, jedis);
        // In this implementation, we have to parse the existing item in order to determine its version.
        int oldVersion = oldItemJson == null ? -1 : kind.deserialize(oldItemJson).getVersion();
  
        if (oldVersion >= newItem.getVersion()) {
          logger.debug("Attempted to {} key: {} version: {}" +
              " with a version that is the same or older: {} in \"{}\"",
              newItem.getSerializedItem() == null ? "delete" : "update",
              key, oldVersion, newItem.getVersion(), kind.getName());
          return false;
        }
  
        Transaction tx = jedis.multi();
        tx.hset(baseKey, key, jsonOrPlaceholder(kind, newItem));
        List<Object> result = tx.exec();
        if (result.isEmpty()) {
          // if exec failed, it means the watch was triggered and we should retry
          logger.debug("Concurrent modification detected, retrying");
          continue;
        }
  
        return true;
      } finally {
        if (jedis != null) {
          jedis.unwatch();
          jedis.close();
        }
      }
    }
  }
  
  @Override
  public boolean isInitialized() {
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
  
  private String itemsKey(DataKind kind) {
    return prefix + ":" + kind.getName();
  }
  
  private String initedKey() {
    return prefix + ":$inited";
  }
  
  private String getRedis(DataKind kind, String key, Jedis jedis) {
    String json = jedis.hget(itemsKey(kind), key);

    if (json == null) {
      logger.debug("[get] Key: {} not found in \"{}\". Returning null", key, kind.getName());
    }
    
    return json;
  }
  
  private static String jsonOrPlaceholder(DataKind kind, SerializedItemDescriptor serializedItem) {
    String s = serializedItem.getSerializedItem();
    if (s != null) {
      return s;
    }
    // For backward compatibility with previous implementations of the Redis integration, we must store a
    // special placeholder string for deleted items. DataKind.serializeItem() will give us this string if
    // we pass a deleted ItemDescriptor.
    return kind.serialize(ItemDescriptor.deletedItem(serializedItem.getVersion()));
  }
  
  static interface UpdateListener {
    void aboutToUpdate(String baseKey, String itemKey);
  }
}
