package com.launchdarkly.client;

/**
 * Common interface for string-keyed, versioned objects that can be kept in a {@link FeatureStore}.
 * @since 3.0.0
 */
public interface VersionedData {
  String getKey();
  int getVersion();
  boolean isDeleted();
}
