package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.integrations.BigSegmentsConfigurationBuilder;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;

import java.time.Duration;

/**
 * Encapsulates the SDK's configuration with regard to Big Segments.
 * <p>
 * Big Segments are a specific type of user segments. For more information, read the
 * <a href="https://docs.launchdarkly.com/home/users/big-segments">LaunchDarkly documentation
 * </a>.
 * <p>
 * See {@link BigSegmentsConfigurationBuilder} for more details on these properties.
 *
 * @see BigSegmentsConfigurationBuilder
 * @since 5.7.0
 */
public final class BigSegmentsConfiguration {
  private final BigSegmentStore bigSegmentStore;
  private final int userCacheSize;
  private final Duration userCacheTime;
  private final Duration statusPollInterval;
  private final Duration staleAfter;

  /**
   * Creates a new {@link BigSegmentsConfiguration} instance with the specified values.
   * <p>
   * See {@link BigSegmentsConfigurationBuilder} for more information on the configuration fields.
   *
   * @param bigSegmentStore the Big Segments store instance
   * @param userCacheSize the user cache size
   * @param userCacheTime the user cache time
   * @param statusPollInterval the status poll interval
   * @param staleAfter the interval after which store data is considered stale
   */
  public BigSegmentsConfiguration(BigSegmentStore bigSegmentStore,
                                  int userCacheSize,
                                  Duration userCacheTime,
                                  Duration statusPollInterval,
                                  Duration staleAfter) {
    this.bigSegmentStore = bigSegmentStore;
    this.userCacheSize = userCacheSize;
    this.userCacheTime = userCacheTime;
    this.statusPollInterval = statusPollInterval;
    this.staleAfter = staleAfter;
  }

  /**
   * Gets the data store instance that is used for Big Segments data.
   *
   * @return the configured Big Segment store
   */
  public BigSegmentStore getStore() {
    return this.bigSegmentStore;
  }

  /**
   * Gets the value set by {@link BigSegmentsConfigurationBuilder#userCacheSize(int)}
   *
   * @return the configured user cache size limit
   */
  public int getUserCacheSize() {
    return this.userCacheSize;
  }

  /**
   * Gets the value set by {@link BigSegmentsConfigurationBuilder#userCacheTime(Duration)}
   *
   * @return the configured user cache time duration
   */
  public Duration getUserCacheTime() {
    return this.userCacheTime;
  }

  /**
   * Gets the value set by {@link BigSegmentsConfigurationBuilder#statusPollInterval(Duration)}
   *
   * @return the configured status poll interval
   */
  public Duration getStatusPollInterval() {
    return this.statusPollInterval;
  }

  /**
   * Gets the value set by {@link BigSegmentsConfigurationBuilder#staleAfter(Duration)}
   *
   * @return the configured stale after interval
   */
  public Duration getStaleAfter() {
    return this.staleAfter;
  }
}
