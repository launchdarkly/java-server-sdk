package com.launchdarkly.client;

import com.google.common.cache.CacheBuilder;
import com.launchdarkly.client.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.client.interfaces.DataStore;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Parameters that can be used for {@link DataStore} implementations that support local caching.
 * If a store implementation uses this class, then it is using the standard caching mechanism that
 * is built into the SDK, and is guaranteed to support all the properties defined in this class. 
 * <p>
 * This is an immutable class that uses a fluent interface. Obtain an instance by calling the static
 * methods {@link #disabled()} or {@link #enabled()}; then, if desired, you can use chained methods
 * to set other properties:
 * 
 * <pre><code>
 *     Components.redisDataStore()
 *         .caching(
 *             DataStoreCacheConfig.enabled()
 *                 .ttlSeconds(30)
 *                 .staleValuesPolicy(DataStoreCacheConfig.StaleValuesPolicy.REFRESH)
 *         )
 * </code></pre>
 * 
 * @since 4.6.0
 */
public final class DataStoreCacheConfig {
  /**
   * The default TTL used by {@link #DEFAULT}.
   */
  public static final Duration DEFAULT_TIME = Duration.ofSeconds(15);

  /**
   * The caching parameters that the data store should use by default. Caching is enabled, with a
   * TTL of {@link #DEFAULT_TIME} and the {@link StaleValuesPolicy#EVICT} policy. 
   */
  public static final DataStoreCacheConfig DEFAULT =
      new DataStoreCacheConfig(DEFAULT_TIME, StaleValuesPolicy.EVICT);
  
  private static final DataStoreCacheConfig DISABLED =
      new DataStoreCacheConfig(Duration.ZERO, StaleValuesPolicy.EVICT);
  
  private final Duration cacheTime;
  private final StaleValuesPolicy staleValuesPolicy;
  
  /**
   * Possible values for {@link DataStoreCacheConfig#staleValuesPolicy(StaleValuesPolicy)}.
   */
  public enum StaleValuesPolicy {
    /**
     * Indicates that when the cache TTL expires for an item, it is evicted from the cache. The next
     * attempt to read that item causes a synchronous read from the underlying data store; if that
     * fails, no value is available. This is the default behavior.
     * 
     * @see CacheBuilder#expireAfterWrite(long, TimeUnit)
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
     * stale values for the period of the async refresh. For example, configuring this data store
     * with a very low cache time and enabling this feature would see great performance benefit by
     * decoupling calls from network I/O.
     * <p>
     * See: <a href="https://github.com/google/guava/wiki/CachesExplained#refresh">CacheBuilder</a> for
     * more specific information on cache semantics.
     */
    REFRESH_ASYNC;
    
    /**
     * Used internally for backward compatibility.
     * @return the equivalent enum value
     * @since 4.11.0
     */
    public PersistentDataStoreBuilder.StaleValuesPolicy toNewEnum() {
      switch (this) {
      case REFRESH:
        return PersistentDataStoreBuilder.StaleValuesPolicy.REFRESH;
      case REFRESH_ASYNC:
        return PersistentDataStoreBuilder.StaleValuesPolicy.REFRESH_ASYNC;
      default:
        return PersistentDataStoreBuilder.StaleValuesPolicy.EVICT;
      }
    }
    
    /**
     * Used internally for backward compatibility.
     * @param policy the enum value in the new API
     * @return the equivalent enum value
     * @since 4.11.0
     */
    public static StaleValuesPolicy fromNewEnum(PersistentDataStoreBuilder.StaleValuesPolicy policy) {
      switch (policy) {
      case REFRESH:
        return StaleValuesPolicy.REFRESH;
      case REFRESH_ASYNC:
        return StaleValuesPolicy.REFRESH_ASYNC;
      default:
        return StaleValuesPolicy.EVICT;
      }
    }
  };
  
  /**
   * Returns a parameter object indicating that caching should be disabled. Specifying any additional
   * properties on this object will have no effect.
   * @return a {@link DataStoreCacheConfig} instance
   */
  public static DataStoreCacheConfig disabled() {
    return DISABLED;
  }
  
  /**
   * Returns a parameter object indicating that caching should be enabled, using the default TTL of
   * {@link #DEFAULT_TIME}. You can further modify the cache properties using the other
   * methods of this class.
   * @return a {@link DataStoreCacheConfig} instance
   */
  public static DataStoreCacheConfig enabled() {
    return DEFAULT;
  }

  private DataStoreCacheConfig(Duration cacheTime, StaleValuesPolicy staleValuesPolicy) {
    this.cacheTime = cacheTime == null ? DEFAULT_TIME : cacheTime;
    this.staleValuesPolicy = staleValuesPolicy;
  }

  /**
   * Returns true if caching will be enabled.
   * @return true if the cache TTL is nonzero
   */
  public boolean isEnabled() {
    return !cacheTime.isZero();
  }
    
  /**
   * Returns true if caching is enabled and does not have a finite TTL.
   * @return true if the cache TTL is negative
   */
  public boolean isInfiniteTtl() {
    return cacheTime.isNegative();
  }
  
  /**
   * Returns the cache TTL.
   * <p>
   * If the value is zero, caching is disabled.
   * <p>
   * If the value is negative, data is cached forever (i.e. it will only be read again from the database
   * if the SDK is restarted). Use the "cached forever" mode with caution: it means that in a scenario
   * where multiple processes are sharing the database, and the current process loses connectivity to
   * LaunchDarkly while other processes are still receiving updates and writing them to the database,
   * the current process will have stale data.
   * 
   * @return the cache TTL
   */
  public Duration getCacheTime() {
    return cacheTime;
  }
  
  /**
   * Returns the {@link StaleValuesPolicy} setting.
   * @return the expiration policy
   */
  public StaleValuesPolicy getStaleValuesPolicy() {
    return staleValuesPolicy;
  }
  
  /**
   * Specifies the cache TTL. Items will be evicted or refreshed (depending on {@link #staleValuesPolicy(StaleValuesPolicy)})
   * after this amount of time from the time when they were originally cached. If the time is less
   * than or equal to zero, caching is disabled.
   * after this amount of time from the time when they were originally cached.
   * <p>
   * If the value is zero, caching is disabled.
   * <p>
   * If the value is negative, data is cached forever (i.e. it will only be read again from the database
   * if the SDK is restarted). Use the "cached forever" mode with caution: it means that in a scenario
   * where multiple processes are sharing the database, and the current process loses connectivity to
   * LaunchDarkly while other processes are still receiving updates and writing them to the database,
   * the current process will have stale data.
   * 
   * @param cacheTime the cache TTL
   * @return an updated parameters object
   */
  public DataStoreCacheConfig ttl(Duration cacheTime) {
    return new DataStoreCacheConfig(cacheTime, staleValuesPolicy);
  }

  /**
   * Shortcut for calling {@link #ttl(Duration)} with a duration in milliseconds.
   * 
   * @param millis the cache TTL in milliseconds
   * @return an updated parameters object
   */
  public DataStoreCacheConfig ttlMillis(long millis) {
    return ttl(Duration.ofMillis(millis));
  }

  /**
   * Shortcut for calling {@link #ttl(Duration)} with a duration in seconds.
   * 
   * @param seconds the cache TTL in seconds
   * @return an updated parameters object
   */
  public DataStoreCacheConfig ttlSeconds(long seconds) {
    return ttl(Duration.ofSeconds(seconds));
  }
  
  /**
   * Specifies how the cache (if any) should deal with old values when the cache TTL expires. The default
   * is {@link StaleValuesPolicy#EVICT}. This property has no effect if caching is disabled.
   * 
   * @param policy a {@link StaleValuesPolicy} constant
   * @return an updated parameters object
   */
  public DataStoreCacheConfig staleValuesPolicy(StaleValuesPolicy policy) {
    return new DataStoreCacheConfig(cacheTime, policy);
  }
  
  @Override
  public boolean equals(Object other) {
    if (other instanceof DataStoreCacheConfig) {
      DataStoreCacheConfig o = (DataStoreCacheConfig) other;
      return o.cacheTime.equals(this.cacheTime) && o.staleValuesPolicy == this.staleValuesPolicy;
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(cacheTime, staleValuesPolicy);
  }
}
