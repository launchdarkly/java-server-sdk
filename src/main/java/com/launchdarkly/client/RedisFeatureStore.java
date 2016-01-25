package com.launchdarkly.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.util.EntityUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisFeatureStore implements FeatureStore {
  private static final String DEFAULT_PREFIX = "launchdarkly";
  private final Jedis jedis;
  private LoadingCache<String, FeatureRep<?>> cache;
  private String prefix;

  public RedisFeatureStore(String host, int port, String prefix, long cacheTimeSecs) {
    jedis = new Jedis(host, port);
    setPrefix(prefix);
    createCache(cacheTimeSecs);
  }

  public RedisFeatureStore(URI uri, String prefix, long cacheTimeSecs) {
    jedis = new Jedis(uri);
    setPrefix(prefix);
    createCache(cacheTimeSecs);
  }

  public RedisFeatureStore() {
    jedis = new Jedis("localhost");
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


  @Override
  public FeatureRep<?> get(String key) {
    if (cache != null) {
      return cache.getUnchecked(key);
    } else {
      return getRedis(key);
    }
  }

  @Override
  public Map<String, FeatureRep<?>> all() {
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

  @Override
  public void init(Map<String, FeatureRep<?>> features) {
    Gson gson = new Gson();
    Transaction t = jedis.multi();

    t.del(featuresKey());

    for (FeatureRep<?> f: features.values()) {
      t.hset(featuresKey(), f.key, gson.toJson(f));
    }

    t.exec();
  }

  @Override
  public void delete(String key, int version) {
    try {
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
    finally {
      jedis.unwatch();
    }

  }

  @Override
  public void upsert(String key, FeatureRep<?> feature) {
    try {
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
    finally {
      jedis.unwatch();
    }
  }

  @Override
  public boolean initialized() {
    return jedis.exists(featuresKey());
  }


  private String featuresKey() {
    return prefix + ":features";
  }

  private FeatureRep<?> getRedis(String key) {
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
