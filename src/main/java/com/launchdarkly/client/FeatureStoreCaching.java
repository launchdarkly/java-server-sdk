package com.launchdarkly.client;

import com.google.common.cache.CacheBuilder;
import com.launchdarkly.client.utils.CachingStoreWrapper;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Parameters that can be used for {@link FeatureStore} implementations that support local caching.
 * <p>
 * This is an immutable class that uses a fluent interface. Obtain an instance by calling the static
 * methods {@link #disabled()} or {@link #enabled()}; then, if desired, you can use chained methods
 * to set other properties:
 * 
 * <pre>
 *     new RedisFeatureStoreBuilder()
 *         .caching(
 *             FeatureStoreCaching.enabled()
 *                 .ttlSeconds(30)
 *                 .staleValuesPolicy(FeatureStoreCaching.StaleValuesPolicy.REFRESH)
 *         )
 * </pre>
 * 
 * @see RedisFeatureStoreBuilder#caching(FeatureStoreCaching)
 * @see CachingStoreWrapper.Builder#caching(FeatureStoreCaching)
 * @since 4.6.0
 */
public final class FeatureStoreCaching {
  /**
   * The default TTL, in seconds, used by {@link #DEFAULT}.
   */
  public static final long DEFAULT_TIME_SECONDS = 15;

  /**
   * The caching parameters that feature store should use by default. Caching is enabled, with a
   * TTL of {@link #DEFAULT_TIME_SECONDS} and the {@link StaleValuesPolicy#EVICT} policy. 
   */
  public static final FeatureStoreCaching DEFAULT =
      new FeatureStoreCaching(DEFAULT_TIME_SECONDS, TimeUnit.SECONDS, StaleValuesPolicy.EVICT);
  
  private static final FeatureStoreCaching DISABLED =
      new FeatureStoreCaching(0, TimeUnit.MILLISECONDS, StaleValuesPolicy.EVICT);
  
  private final long cacheTime;
  private final TimeUnit cacheTimeUnit;
  private final StaleValuesPolicy staleValuesPolicy;
  
  /**
   * Possible values for {@link FeatureStoreCaching#staleValuesPolicy(StaleValuesPolicy)}.
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
   * Returns a parameter object indicating that caching should be disabled. Specifying any additional
   * properties on this object will have no effect.
   * @return a {@link FeatureStoreCaching} instance
   */
  public static FeatureStoreCaching disabled() {
    return DISABLED;
  }
  
  /**
   * Returns a parameter object indicating that caching should be enabled, using the default TTL of
   * {@link #DEFAULT_TIME_SECONDS}. You can further modify the cache properties using the other
   * methods of this class.
   * @return a {@link FeatureStoreCaching} instance
   */
  public static FeatureStoreCaching enabled() {
    return DEFAULT;
  }

  private FeatureStoreCaching(long cacheTime, TimeUnit cacheTimeUnit, StaleValuesPolicy staleValuesPolicy) {
    this.cacheTime = cacheTime;
    this.cacheTimeUnit = cacheTimeUnit;
    this.staleValuesPolicy = staleValuesPolicy;
  }

  /**
   * Returns true if caching will be enabled.
   * @return true if the cache TTL is great then 0
   */
  public boolean isEnabled() {
    return getCacheTime() > 0;
  }
  
  /**
   * Returns the cache TTL. Caching is enabled if this is greater than zero.
   * @return the cache TTL in whatever units were specified
   * @see #getCacheTimeUnit()
   */
  public long getCacheTime() {
    return cacheTime;
  }
  
  /**
   * Returns the time unit for the cache TTL.
   * @return the time unit
   */
  public TimeUnit getCacheTimeUnit() {
    return cacheTimeUnit;
  }
  
  /**
   * Returns the cache TTL converted to milliseconds.
   * @return the TTL in milliseconds
   */
  public long getCacheTimeMillis() {
    return cacheTimeUnit.toMillis(cacheTime);
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
   * 
   * @param cacheTime the cache TTL in whatever units you wish
   * @param timeUnit the time unit
   * @return an updated parameters object
   */
  public FeatureStoreCaching ttl(long cacheTime, TimeUnit timeUnit) {
    return new FeatureStoreCaching(cacheTime, timeUnit, staleValuesPolicy);
  }

  /**
   * Shortcut for calling {@link #ttl(long, TimeUnit)} with {@link TimeUnit#MILLISECONDS}.
   * 
   * @param millis the cache TTL in milliseconds
   * @return an updated parameters object
   */
  public FeatureStoreCaching ttlMillis(long millis) {
    return ttl(millis, TimeUnit.MILLISECONDS);
  }

  /**
   * Shortcut for calling {@link #ttl(long, TimeUnit)} with {@link TimeUnit#SECONDS}.
   * 
   * @param seconds the cache TTL in seconds
   * @return an updated parameters object
   */
  public FeatureStoreCaching ttlSeconds(long seconds) {
    return ttl(seconds, TimeUnit.SECONDS);
  }
  
  /**
   * Specifies how the cache (if any) should deal with old values when the cache TTL expires. The default
   * is {@link StaleValuesPolicy#EVICT}. This property has no effect if caching is disabled.
   * 
   * @param policy a {@link StaleValuesPolicy} constant
   * @return an updated parameters object
   */
  public FeatureStoreCaching staleValuesPolicy(StaleValuesPolicy policy) {
    return new FeatureStoreCaching(cacheTime, cacheTimeUnit, policy);
  }
  
  @Override
  public boolean equals(Object other) {
    if (other instanceof FeatureStoreCaching) {
      FeatureStoreCaching o = (FeatureStoreCaching) other;
      return o.cacheTime == this.cacheTime && o.cacheTimeUnit == this.cacheTimeUnit &&
          o.staleValuesPolicy == this.staleValuesPolicy;
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(cacheTime, cacheTimeUnit, staleValuesPolicy);
  }
}
