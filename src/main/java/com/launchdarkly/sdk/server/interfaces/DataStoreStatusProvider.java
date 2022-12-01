package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder;

import java.util.Objects;

/**
 * An interface for querying the status of a persistent data store.
 * <p>
 * An implementation of this interface is returned by {@link com.launchdarkly.sdk.server.interfaces.LDClientInterface#getDataStoreStatusProvider}.
 * Application code should not implement this interface.
 * 
 * @since 5.0.0
 */
public interface DataStoreStatusProvider {
  /**
   * Returns the current status of the store.
   * <p>
   * This is only meaningful for persistent stores, or any custom data store implementation that makes use of
   * the status reporting mechanism provided by the SDK. For the default in-memory store, the status will always
   * be reported as "available".
   * 
   * @return the latest status; will never be null
   */
  public Status getStatus();
  
  /**
   * Indicates whether the current data store implementation supports status monitoring.
   * <p>
   * This is normally true for all persistent data stores, and false for the default in-memory store. A true value
   * means that any listeners added with {@link #addStatusListener(StatusListener)} can expect to be notified if
   * there is any error in storing data, and then notified again when the error condition is resolved. A false
   * value means that the status is not meaningful and listeners should not expect to be notified.
   * 
   * @return true if status monitoring is enabled
   */
  public boolean isStatusMonitoringEnabled();
  
  /**
   * Subscribes for notifications of status changes.
   * <p>
   * Applications may wish to know if there is an outage in a persistent data store, since that could mean that
   * flag evaluations are unable to get the flag data from the store (unless it is currently cached) and therefore
   * might return default values.
   * <p>
   * If the SDK receives an exception while trying to query or update the data store, then it notifies listeners
   * that the store appears to be offline ({@link Status#isAvailable()} is false) and begins polling the store
   * at intervals until a query succeeds. Once it succeeds, it notifies listeners again with {@link Status#isAvailable()}
   * set to true.
   * <p>
   * This method has no effect if the data store implementation does not support status tracking, such as if you
   * are using the default in-memory store rather than a persistent store.
   * 
   * @param listener the listener to add
   */
  public void addStatusListener(StatusListener listener);

  /**
   * Unsubscribes from notifications of status changes.
   * <p>
   * This method has no effect if the data store implementation does not support status tracking, such as if you
   * are using the default in-memory store rather than a persistent store.
   * 
   * @param listener the listener to remove; if no such listener was added, this does nothing
   */
  public void removeStatusListener(StatusListener listener);

  /**
   * Queries the current cache statistics, if this is a persistent store with caching enabled.
   * <p>
   * This method returns null if the data store implementation does not support cache statistics because it is
   * not a persistent store, or because you did not enable cache monitoring with
   * {@link PersistentDataStoreBuilder#recordCacheStats(boolean)}. 
   * 
   * @return a {@link CacheStats} instance; null if not applicable
   */
  public CacheStats getCacheStats();
  
  /**
   * Information about a status change.
   */
  public static final class Status {
    private final boolean available;
    private final boolean refreshNeeded;
    
    /**
     * Creates an instance.
     * @param available see {@link #isAvailable()}
     * @param refreshNeeded see {@link #isRefreshNeeded()}
     */
    public Status(boolean available, boolean refreshNeeded) {
      this.available = available;
      this.refreshNeeded = refreshNeeded;
    }
    
    /**
     * Returns true if the SDK believes the data store is now available.
     * <p>
     * This property is normally true. If the SDK receives an exception while trying to query or update the data
     * store, then it sets this property to false (notifying listeners, if any) and polls the store at intervals
     * until a query succeeds. Once it succeeds, it sets the property back to true (again notifying listeners).
     *  
     * @return true if store is available
     */
    public boolean isAvailable() {
      return available;
    }
    
    /**
     * Returns true if the store may be out of date due to a previous outage, so the SDK should attempt to refresh
     * all feature flag data and rewrite it to the store.
     * <p>
     * This property is not meaningful to application code.
     * 
     * @return true if data should be rewritten
     */
    public boolean isRefreshNeeded() {
      return refreshNeeded;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof Status) {
        Status o = (Status)other;
        return available == o.available && refreshNeeded == o.refreshNeeded;
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(available, refreshNeeded);
    }
    
    @Override
    public String toString() {
      return "Status(" + available + "," + refreshNeeded + ")";
    }
  }
  
  /**
   * Interface for receiving status change notifications.
   */
  public static interface StatusListener {
    /**
     * Called when the store status has changed.
     * @param newStatus the new status
     */
    public void dataStoreStatusChanged(Status newStatus);
  }
  
  /**
   * A snapshot of cache statistics. The statistics are cumulative across the lifetime of the data store.
   * <p>
   * This is based on the data provided by Guava's caching framework. The SDK currently uses Guava
   * internally, but is not guaranteed to always do so, and to avoid embedding Guava API details in
   * the SDK API this is provided as a separate class.
   * 
   * @see DataStoreStatusProvider#getCacheStats()
   * @see PersistentDataStoreBuilder#recordCacheStats(boolean)
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
