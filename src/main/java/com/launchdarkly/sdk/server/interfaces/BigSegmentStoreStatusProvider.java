package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.integrations.BigSegmentsConfigurationBuilder;

import java.time.Duration;
import java.util.Objects;

/**
 * An interface for querying the status of a Big Segment store.
 * <p>
 * The Big Segment store is the component that receives information about Big Segments, normally
 * from a database populated by the LaunchDarkly Relay Proxy.
 * <p>
 * Big Segments are a specific type of user segments. For more information, read the
 * <a href="https://docs.launchdarkly.com/home/users/big-segments">LaunchDarkly documentation
 * </a>.
 * <p>
 * An implementation of this interface is returned by
 * {@link LDClient#getBigSegmentStoreStatusProvider()}. Application code never needs to implement
 * this interface.
 *
 * @since 5.7.0
 */
public interface BigSegmentStoreStatusProvider {
  /**
   * Returns the current status of the store.
   *
   * @return the latest status; will never be null
   */
  Status getStatus();

  /**
   * Subscribes for notifications of status changes.
   *
   * @param listener the listener to add
   */
  void addStatusListener(StatusListener listener);

  /**
   * Unsubscribes from notifications of status changes.
   *
   * @param listener the listener to remove; if no such listener was added, this does nothing
   */
  void removeStatusListener(StatusListener listener);

  /**
   * Information about the status of a Big Segment store, provided by
   * {@link BigSegmentStoreStatusProvider}
   * <p>
   * Big Segments are a specific type of user segments. For more information, read the
   * <a href="https://docs.launchdarkly.com/home/users/big-segments">LaunchDarkly documentation
   * </a>.
   */
  public static final class Status {
    private final boolean available;
    private final boolean stale;

    /**
     * Constructor for a Big Segment status.
     *
     * @param available whether the Big Segment store is available
     * @param stale whether the Big Segment store has not been recently updated
     */
    public Status(boolean available, boolean stale) {
      this.available = available;
      this.stale = stale;
    }

    /**
     * True if the Big Segment store is able to respond to queries, so that the SDK can evaluate
     * whether a user is in a segment or not.
     * <p>
     * If this property is false, the store is not able to make queries (for instance, it may not
     * have a valid database connection). In this case, the SDK will treat any reference to a Big
     * Segment as if no users are included in that segment. Also, the {@link EvaluationReason}
     * associated with any flag evaluation that references a Big Segment when the store is not
     * available will have a {@link EvaluationReason.BigSegmentsStatus} of
     * {@link EvaluationReason.BigSegmentsStatus#STORE_ERROR}.
     *
     * @return whether the Big Segment store is able to respond to queries
     */
    public boolean isAvailable() {
      return available;
    }

    /**
     * True if the Big Segment store is available, but has not been updated within the amount of
     * time specified by
     * {@link BigSegmentsConfigurationBuilder#staleAfter(Duration)}.
     * <p>
     * This may indicate that the LaunchDarkly Relay Proxy, which populates the store, has stopped
     * running or has become unable to receive fresh data from LaunchDarkly. Any feature flag
     * evaluations that reference a Big Segment will be using the last known data, which may be out
     * of date.
     *
     * @return whether the data in the Big Segment store is considered to be stale
     */
    public boolean isStale() {
      return stale;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof Status) {
        Status o = (Status)other;
        return available == o.available && stale == o.stale;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(available, stale);
    }

    @Override
    public String toString() {
      return "Status(Available=" + available + ",Stale=" + stale + ")";
    }
  }

  /**
   * Interface for receiving Big Segment status change notifications.
   */
  public static interface StatusListener {
    /**
     * Called when any property of the Big Segment store status has changed.
     *
     * @param newStatus the new status of the Big Segment store
     */
    void bigSegmentStoreStatusChanged(Status newStatus);
  }
}
