package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder;

import java.util.Objects;

/**
 * An interface for querying the status of a persistent data store.
 * <p>
 * An implementation of this interface is returned by {@link com.launchdarkly.sdk.server.LDClientInterface#getDataStoreStatusProvider}.
 * If the data store is a persistent data store, then these methods are implemented by the SDK; if it is a custom
 * class that implements this interface, then these methods delegate to the corresponding methods of the class;
 * if it is the default in-memory data store, then these methods do nothing and return null values.
 * <p>
 * Application code should not implement this interface.
 * 
 * @since 5.0.0
 */
public interface DataStoreStatusProvider {
  /**
   * Returns the current status of the store.
   * <p>
   * This is only meaningful for persistent stores, or any other {@link DataStore} implementation that makes use of
   * the reporting mechanism provided by {@link DataStoreFactory#createDataStore(ClientContext, java.util.function.Consumer)}.
   * For the default in-memory store, the status will always be reported as "available".
   * 
   * @return the latest status; will never be null
   */
  public Status getStoreStatus();
  
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
   * @return a {@link DataStoreTypes.CacheStats} instance; null if not applicable
   */
  public DataStoreTypes.CacheStats getCacheStats();
  
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
}
