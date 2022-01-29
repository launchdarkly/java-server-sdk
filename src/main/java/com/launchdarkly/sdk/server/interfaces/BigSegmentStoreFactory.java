package com.launchdarkly.sdk.server.interfaces;

/**
 * Interface for a factory that creates some implementation of {@link BigSegmentStore}.
 *
 * @see com.launchdarkly.sdk.server.Components#bigSegments(BigSegmentStoreFactory)
 * @see com.launchdarkly.sdk.server.LDConfig.Builder#bigSegments(com.launchdarkly.sdk.server.integrations.BigSegmentsConfigurationBuilder)
 * @since 5.7.0
 */
public interface BigSegmentStoreFactory {
  /**
   * Called internally by the SDK to create an implementation instance. Applications do not need to
   * call this method.
   *
   * @param context allows access to the client configuration
   * @return a {@link BigSegmentStore} instance
   */
  BigSegmentStore createBigSegmentStore(ClientContext context);
}
