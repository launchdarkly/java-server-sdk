package com.launchdarkly.sdk.server.subsystems;

import java.io.Closeable;

/**
 * Interface for a read-only data store that allows querying of user membership in Big Segments.
 * <p>
 * Big Segments are a specific type of user segments. For more information, read the
 * <a href="https://docs.launchdarkly.com/home/users/big-segments">LaunchDarkly documentation
 * </a>.
 *
 * @since 5.7.0
 */
public interface BigSegmentStore extends Closeable {
  /**
   * Queries the store for a snapshot of the current segment state for a specific user.
   * <p>
   * The {@code userHash} is a base64-encoded string produced by hashing the user key as defined by
   * the Big Segments specification; the store implementation does not need to know the details of
   * how this is done, because it deals only with already-hashed keys, but the string can be assumed
   * to only contain characters that are valid in base64.
   * <p>
   * If the store is working, but no membership state is found for this user, the method may return
   * either {@code null} or an empty {@link BigSegmentStoreTypes.Membership}. It should not throw an
   * exception unless there is an unexpected database error or the retrieved data is malformed.
   *
   * @param userHash the hashed user identifier
   * @return the user's segment membership state or {@code null}
   */
  BigSegmentStoreTypes.Membership getMembership(String userHash);

  /**
   * Returns information about the overall state of the store.
   * <p>
   * This method will be called only when the SDK needs the latest state, so it should not be
   * cached.
   * <p>
   * If the store is working, but no metadata has been stored in it yet, the method should return
   * {@code null}. It should not throw an exception unless there is an unexpected database error or
   * the retrieved data is malformed.
   *
   * @return the store metadata or null
   */
  BigSegmentStoreTypes.StoreMetadata getMetadata();
}
