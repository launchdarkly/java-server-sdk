package com.launchdarkly.client.interfaces;

/**
 * Common interface for string-keyed, versioned objects that can be kept in a {@link FeatureStore}.
 * @since 3.0.0
 */
public interface VersionedData {
  /**
   * The key for this item, unique within the namespace of each {@link VersionedDataKind}.
   * @return the key
   */
  String getKey();
  /**
   * The version number for this item.
   * @return the version number
   */
  int getVersion();
  /**
   * True if this is a placeholder for a deleted item.
   * @return true if deleted
   */
  boolean isDeleted();
}
