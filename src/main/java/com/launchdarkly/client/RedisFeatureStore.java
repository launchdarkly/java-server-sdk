package com.launchdarkly.client;

import com.google.common.cache.CacheStats;
import com.launchdarkly.client.utils.CachingStoreWrapper;

import java.io.IOException;
import java.util.Map;

/**
 * Deprecated implementation class for the Redis-based persistent data store.
 * <p>
 * Instead of referencing this class directly, use {@link com.launchdarkly.client.integrations.Redis#dataStore()} to obtain a builder object.
 * 
 * @deprecated Use {@link com.launchdarkly.client.integrations.Redis#dataStore()}
 */
@Deprecated
public class RedisFeatureStore implements FeatureStore {
  // The actual implementation is now in the com.launchdarkly.integrations package. This class remains
  // visible for backward compatibility, but simply delegates to an instance of the underlying store.
  
  private final FeatureStore wrappedStore;
  
  @Override
  public void init(Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData) {
    wrappedStore.init(allData);
  }
  
  @Override
  public <T extends VersionedData> T get(VersionedDataKind<T> kind, String key) {
    return wrappedStore.get(kind, key);
  }
  
  @Override
  public <T extends VersionedData> Map<String, T> all(VersionedDataKind<T> kind) {
    return wrappedStore.all(kind);
  }

  @Override
  public <T extends VersionedData> void upsert(VersionedDataKind<T> kind, T item) {
    wrappedStore.upsert(kind, item);
  }

  @Override
  public <T extends VersionedData> void delete(VersionedDataKind<T> kind, String key, int version) {
    wrappedStore.delete(kind, key, version);
  }
  
  @Override
  public boolean initialized() {
    return wrappedStore.initialized();
  }
  
  @Override
  public void close() throws IOException {
    wrappedStore.close();
  }
  
  /**
   * Return the underlying Guava cache stats object.
   * <p>
   * In the newer data store API, there is a different way to do this. See
   * {@link com.launchdarkly.client.integrations.PersistentDataStoreBuilder#cacheMonitor(com.launchdarkly.client.integrations.CacheMonitor)}. 
   *
   * @return the cache statistics object.
   */
  public CacheStats getCacheStats() {
    return ((CachingStoreWrapper)wrappedStore).getCacheStats();
  }

  /**
   * Creates a new store instance that connects to Redis based on the provided {@link RedisFeatureStoreBuilder}.
   * <p>
   * See the {@link RedisFeatureStoreBuilder} for information on available configuration options and what they do.
   *
   * @param builder the configured builder to construct the store with.
   */
  protected RedisFeatureStore(RedisFeatureStoreBuilder builder) {
    wrappedStore = builder.wrappedBuilder.createFeatureStore();
  }

  /**
   * Creates a new store instance that connects to Redis with a default connection (localhost port 6379) and no in-memory cache.
   * @deprecated Please use {@link Components#redisFeatureStore()} instead.
   */
  @Deprecated
  public RedisFeatureStore() {
    this(new RedisFeatureStoreBuilder().caching(FeatureStoreCacheConfig.disabled()));
  }
}
