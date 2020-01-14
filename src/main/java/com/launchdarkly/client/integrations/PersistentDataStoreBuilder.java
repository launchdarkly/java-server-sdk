package com.launchdarkly.client.integrations;

import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FeatureStoreFactory;
import com.launchdarkly.client.interfaces.PersistentDataStoreFactory;

import java.util.concurrent.TimeUnit;

/**
 * A configurable factory for a persistent data store.
 * <p>
 * Several database integrations exist for the LaunchDarkly SDK, each with its own behavior and options
 * specific to that database; this is described via some implementation of {@link PersistentDataStoreFactory}.
 * There is also universal behavior that the SDK provides for all persistent data stores, such as caching;
 * the {@link PersistentDataStoreBuilder} adds this.
 * <p>
 * After configuring this object, pass it to {@link com.launchdarkly.client.LDConfig.Builder#dataStore(FeatureStoreFactory)}
 * to use it in the SDK configuration. For example, using the Redis integration:
 * 
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .dataStore(
 *             Components.persistentDataStore(
 *                 Redis.dataStore().url("redis://my-redis-host")
 *             ).cacheSeconds(15)
 *         )
 *         .build();
 * </code></pre>
 * 
 * In this example, {@code .url()} is an option specifically for the Redis integration, whereas
 * {@code ttlSeconds()} is an option that can be used for any persistent data store. 
 * 
 * @since 4.11.0
 */
public final class PersistentDataStoreBuilder implements FeatureStoreFactory {
  /**
   * The default value for the cache TTL.
   */
  public static final int DEFAULT_CACHE_TTL_SECONDS = 15;

  private final PersistentDataStoreFactory persistentDataStoreFactory;
  
  /**
   * Possible values for {@link #staleValuesPolicy(StaleValuesPolicy)}.
   */
  public enum StaleValuesPolicy {
    /**
     * Indicates that when the cache TTL expires for an item, it is evicted from the cache. The next
     * attempt to read that item causes a synchronous read from the underlying data store; if that
     * fails, no value is available. This is the default behavior.
     * 
     * @see com.google.common.cache.CacheBuilder#expireAfterWrite(long, TimeUnit)
     */
    EVICT,
    /**
     * Indicates that the cache should refresh stale values instead of evicting them.
     * <p>
     * In this mode, an attempt to read an expired item causes a synchronous read from the underlying
     * data store, like {@link #EVICT}--but if an error occurs during this refresh, the cache will
     * continue to return the previously cached values (if any). This is useful if you prefer the most
     * recently cached feature rule set to be returned for evaluation over the default value when
     * updates go wrong.
     * <p>
     * See: <a href="https://github.com/google/guava/wiki/CachesExplained#timed-eviction">CacheBuilder</a>
     * for more specific information on cache semantics. This mode is equivalent to {@code expireAfterWrite}.
     */
    REFRESH,
    /**
     * Indicates that the cache should refresh stale values asynchronously instead of evicting them.
     * <p>
     * This is the same as {@link #REFRESH}, except that the attempt to refresh the value is done
     * on another thread (using a {@link java.util.concurrent.Executor}). In the meantime, the cache
     * will continue to return the previously cached value (if any) in a non-blocking fashion to threads
     * requesting the stale key. Any exception encountered during the asynchronous reload will cause
     * the previously cached value to be retained.
     * <p>
     * This setting is ideal to enable when you desire high performance reads and can accept returning
     * stale values for the period of the async refresh. For example, configuring this feature store
     * with a very low cache time and enabling this feature would see great performance benefit by
     * decoupling calls from network I/O.
     * <p>
     * See: <a href="https://github.com/google/guava/wiki/CachesExplained#refresh">CacheBuilder</a> for
     * more specific information on cache semantics.
     */
    REFRESH_ASYNC
  };
  
  /**
   * Creates a new builder.
   * 
   * @param persistentDataStoreFactory the factory implementation for the specific data store type
   */
  public PersistentDataStoreBuilder(PersistentDataStoreFactory persistentDataStoreFactory) {
    this.persistentDataStoreFactory = persistentDataStoreFactory;
  }

  @Override
  public FeatureStore createFeatureStore() {
    return persistentDataStoreFactory.createFeatureStore();
  }
  
  /**
   * Specifies that the SDK should <i>not</i> use an in-memory cache for the persistent data store.
   * This means that every feature flag evaluation will trigger a data store query.
   * 
   * @return the builder
   */
  public PersistentDataStoreBuilder noCaching() {
    return cacheTime(0, TimeUnit.MILLISECONDS);
  }
  
  /**
   * Specifies the cache TTL. Items will be evicted or refreshed (depending on the StaleValuesPolicy)
   * after this amount of time from the time when they were originally cached.
   * <p>
   * If the value is zero, caching is disabled (equivalent to {@link #noCaching()}).
   * <p>
   * If the value is negative, data is cached forever (equivalent to {@link #cacheForever()}).
   * 
   * @param cacheTime the cache TTL in whatever units you wish
   * @param cacheTimeUnit the time unit
   * @return the builder
   */
  @SuppressWarnings("deprecation")
  public PersistentDataStoreBuilder cacheTime(long cacheTime, TimeUnit cacheTimeUnit) {
    persistentDataStoreFactory.cacheTime(cacheTime, cacheTimeUnit);
    return this;
  }

  /**
   * Shortcut for calling {@link #cacheTime(long, TimeUnit)} with {@link TimeUnit#MILLISECONDS}.
   * 
   * @param millis the cache TTL in milliseconds
   * @return the builder
   */
  public PersistentDataStoreBuilder cacheMillis(long millis) {
    return cacheTime(millis, TimeUnit.MILLISECONDS);
  }

  /**
   * Shortcut for calling {@link #cacheTime(long, TimeUnit)} with {@link TimeUnit#SECONDS}.
   * 
   * @param seconds the cache TTL in seconds
   * @return the builder
   */
  public PersistentDataStoreBuilder cacheSeconds(long seconds) {
    return cacheTime(seconds, TimeUnit.SECONDS);
  }
  
  /**
   * Specifies that the in-memory cache should never expire. In this mode, data will be written
   * to both the underlying persistent store and the cache, but will only ever be read <i>from</i>
   * persistent store if the SDK is restarted.
   * <p>
   * Use this mode with caution: it means that in a scenario where multiple processes are sharing
   * the database, and the current process loses connectivity to LaunchDarkly while other processes
   * are still receiving updates and writing them to the database, the current process will have
   * stale data.
   * 
   * @return the builder
   */
  public PersistentDataStoreBuilder cacheForever() {
    return cacheTime(-1, TimeUnit.MILLISECONDS);
  }
  
  /**
   * Specifies how the cache (if any) should deal with old values when the cache TTL expires. The default
   * is {@link StaleValuesPolicy#EVICT}. This property has no effect if caching is disabled.
   * 
   * @param staleValuesPolicy a {@link StaleValuesPolicy} constant
   * @return the builder
   */
  @SuppressWarnings("deprecation")
  public PersistentDataStoreBuilder staleValuesPolicy(StaleValuesPolicy staleValuesPolicy) {
    persistentDataStoreFactory.staleValuesPolicy(staleValuesPolicy);
    return this;
  }
  
  /**
   * Provides a conduit for an application to monitor the effectiveness of the in-memory cache.
   * <p>
   * Create an instance of {@link CacheMonitor}; retain a reference to it, and also pass it to this
   * method when you are configuring the persistent data store. The store will use
   * {@link CacheMonitor#setSource(java.util.concurrent.Callable)} to make the caching
   * statistics available through that {@link CacheMonitor} instance.
   * <p>
   * Note that turning on cache monitoring may slightly decrease performance, due to the need to
   * record statistics for each cache operation.
   * <p>
   * Example usage:
   * 
   * <pre><code>
   *     CacheMonitor cacheMonitor = new CacheMonitor();
   *     LDConfig config = new LDConfig.Builder()
   *         .dataStore(Components.persistentDataStore(Redis.dataStore()).cacheMonitor(cacheMonitor))
   *         .build();
   *     // later...
   *     CacheMonitor.CacheStats stats = cacheMonitor.getCacheStats();
   * </code></pre>
   * 
   * @param cacheMonitor an instance of {@link CacheMonitor}
   * @return the builder
   */
  @SuppressWarnings("deprecation")
  public PersistentDataStoreBuilder cacheMonitor(CacheMonitor cacheMonitor) {
    persistentDataStoreFactory.cacheMonitor(cacheMonitor);
    return this;
  }
}
