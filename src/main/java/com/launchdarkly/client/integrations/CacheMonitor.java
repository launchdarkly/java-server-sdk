package com.launchdarkly.client.integrations;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * A conduit that an application can use to monitor caching behavior of a persistent data store.
 *
 * @see PersistentDataStoreBuilder#cacheMonitor(CacheMonitor)
 * @since 4.12.0
 */
public final class CacheMonitor {
  private Callable<CacheStats> source;
  
  /**
   * Constructs a new instance.
   */
  public CacheMonitor() {}
  
  /**
   * Called internally by the SDK to establish a source for the statistics.
   * @param source provided by an internal SDK component
   * @deprecated Referencing this method directly is deprecated. In a future version, it will
   * only be visible to SDK implementation code.
   */
  @Deprecated
  public void setSource(Callable<CacheStats> source) {
    this.source = source;
  }
  
  /**
   * Queries the current cache statistics.
   * 
   * @return a {@link CacheStats} instance, or null if not available
   */
  public CacheStats getCacheStats() {
    try {
      return source == null ? null : source.call();
    } catch (Exception e) {
      return null;
    }
  }
  
  /**
   * A snapshot of cache statistics. The statistics are cumulative across the lifetime of the data store.
   * <p>
   * This is based on the data provided by Guava's caching framework. The SDK currently uses Guava
   * internally, but is not guaranteed to always do so, and to avoid embedding Guava API details in
   * the SDK API this is provided as a separate class.
   * 
   * @since 4.12.0
   */
  public static final class CacheStats {
    private final long hitCount;
    private final long missCount;
    private final long loadSuccessCount;
    private final long loadExceptionCount;
    private final long totalLoadTime;
    private final long evictionCount;
    
    /**
     * Constructs a new instance.
     * 
     * @param hitCount number of queries that produced a cache hit
     * @param missCount number of queries that produced a cache miss
     * @param loadSuccessCount number of cache misses that loaded a value without an exception
     * @param loadExceptionCount number of cache misses that tried to load a value but got an exception
     * @param totalLoadTime number of nanoseconds spent loading new values
     * @param evictionCount number of cache entries that have been evicted
     */
    public CacheStats(long hitCount, long missCount, long loadSuccessCount, long loadExceptionCount,
        long totalLoadTime, long evictionCount) {
      this.hitCount = hitCount;
      this.missCount = missCount;
      this.loadSuccessCount = loadSuccessCount;
      this.loadExceptionCount = loadExceptionCount;
      this.totalLoadTime = totalLoadTime;
      this.evictionCount = evictionCount;
    }
    
    /**
     * The number of data queries that received cached data instead of going to the underlying data store.
     * @return the number of cache hits
     */
    public long getHitCount() {
      return hitCount;
    }

    /**
     * The number of data queries that did not find cached data and went to the underlying data store. 
     * @return the number of cache misses
     */
    public long getMissCount() {
      return missCount;
    }

    /**
     * The number of times a cache miss resulted in successfully loading a data store item (or finding
     * that it did not exist in the store).
     * @return the number of successful loads
     */
    public long getLoadSuccessCount() {
      return loadSuccessCount;
    }

    /**
     * The number of times that an error occurred while querying the underlying data store.
     * @return the number of failed loads
     */
    public long getLoadExceptionCount() {
      return loadExceptionCount;
    }

    /**
     * The total number of nanoseconds that the cache has spent loading new values.
     * @return total time spent for all cache loads
     */
    public long getTotalLoadTime() {
      return totalLoadTime;
    }

    /**
     * The number of times cache entries have been evicted.
     * @return the number of evictions
     */
    public long getEvictionCount() {
      return evictionCount;
    }
    
    @Override
    public boolean equals(Object other) {
      if (!(other instanceof CacheStats)) {
        return false;
      }
      CacheStats o = (CacheStats)other;
      return hitCount == o.hitCount && missCount == o.missCount && loadSuccessCount == o.loadSuccessCount &&
          loadExceptionCount == o.loadExceptionCount && totalLoadTime == o.totalLoadTime && evictionCount == o.evictionCount;
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(hitCount, missCount, loadSuccessCount, loadExceptionCount, totalLoadTime, evictionCount);
    }
    
    @Override
    public String toString() {
      return "{hit=" + hitCount + ", miss=" + missCount + ", loadSuccess=" + loadSuccessCount +
          ", loadException=" + loadExceptionCount + ", totalLoadTime=" + totalLoadTime + ", evictionCount=" + evictionCount + "}";
    }
  }
}
