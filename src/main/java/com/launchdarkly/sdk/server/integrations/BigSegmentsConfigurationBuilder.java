package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.BigSegmentsStatus;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig.Builder;
import com.launchdarkly.sdk.server.interfaces.BigSegmentsConfiguration;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;

import java.time.Duration;

/**
 * Contains methods for configuring the SDK's Big Segments behavior.
 * <p>
 * Big Segments are a specific type of user segments. For more information, read the
 * <a href="https://docs.launchdarkly.com/home/users/big-segments">LaunchDarkly documentation
 * </a>.
 * <p>
 * If you want non-default values for any of these properties create a builder with
 * {@link Components#bigSegments(ComponentConfigurer)}, change its properties with the methods
 * of this class, and pass it to {@link Builder#bigSegments(ComponentConfigurer)}
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .bigSegments(Components.bigSegments(Redis.dataStore().prefix("app1"))
 *             .userCacheSize(2000))
 *         .build();
 * </code></pre>
 *
 * @since 5.7.0
 */
public final class BigSegmentsConfigurationBuilder implements ComponentConfigurer<BigSegmentsConfiguration> {
  /**
   * The default value for {@link #userCacheSize(int)}.
   */
  public static final int DEFAULT_USER_CACHE_SIZE = 1000;

  /**
   * The default value for {@link #userCacheTime(Duration)}.
   */
  public static final Duration DEFAULT_USER_CACHE_TIME = Duration.ofSeconds(5);

  /**
   * The default value for {@link #statusPollInterval(Duration)}.
   */
  public static final Duration DEFAULT_STATUS_POLL_INTERVAL = Duration.ofSeconds(5);

  /**
   * The default value for {@link #staleAfter(Duration)}.
   */
  public static final Duration DEFAULT_STALE_AFTER = Duration.ofMinutes(2);

  private final ComponentConfigurer<BigSegmentStore> storeConfigurer;
  private int userCacheSize = DEFAULT_USER_CACHE_SIZE;
  private Duration userCacheTime = DEFAULT_USER_CACHE_TIME;
  private Duration statusPollInterval = DEFAULT_STATUS_POLL_INTERVAL;
  private Duration staleAfter = DEFAULT_STALE_AFTER;

  /**
   * Creates a new builder for Big Segments configuration.
   *
   * @param storeConfigurer the factory implementation for the specific data store type
   */
  public BigSegmentsConfigurationBuilder(ComponentConfigurer<BigSegmentStore> storeConfigurer) {
    this.storeConfigurer = storeConfigurer;
  }

  /**
   * Sets the maximum number of users whose Big Segment state will be cached by the SDK at any given
   * time.
   * <p>
   * To reduce database traffic, the SDK maintains a least-recently-used cache by user key. When a
   * feature flag that references a Big Segment is evaluated for some user who is not currently in
   * the cache, the SDK queries the database for all Big Segment memberships of that user, and
   * stores them together in a single cache entry. If the cache is full, the oldest entry is
   * dropped.
   * <p>
   * A higher value for {@code userCacheSize} means that database queries for Big Segments will be
   * done less often for recently-referenced users, if the application has many users, at the cost
   * of increased memory used by the cache.
   * <p>
   * Cache entries can also expire based on the setting of {@link #userCacheTime(Duration)}.
   *
   * @param userCacheSize the maximum number of user states to cache
   * @return the builder
   * @see #DEFAULT_USER_CACHE_SIZE
   */
  public BigSegmentsConfigurationBuilder userCacheSize(int userCacheSize) {
    this.userCacheSize = Math.max(userCacheSize, 0);
    return this;
  }

  /**
   * Sets the maximum length of time that the Big Segment state for a user will be cached by the
   * SDK.
   * <p>
   * See {@link #userCacheSize(int)} for more about this cache. A higher value for
   * {@code userCacheTime} means that database queries for the Big Segment state of any given user
   * will be done less often, but that changes to segment membership may not be detected as soon.
   *
   * @param userCacheTime the cache TTL (a value of null, or a negative value will be changed to
   *                      {@link #DEFAULT_USER_CACHE_TIME}
   * @return the builder
   * @see #DEFAULT_USER_CACHE_TIME
   */
  public BigSegmentsConfigurationBuilder userCacheTime(Duration userCacheTime) {
    this.userCacheTime = userCacheTime != null && userCacheTime.compareTo(Duration.ZERO) >= 0
        ? userCacheTime : DEFAULT_USER_CACHE_TIME;
    return this;
  }

  /**
   * Sets the interval at which the SDK will poll the Big Segment store to make sure it is available
   * and to determine how long ago it was updated.
   *
   * @param statusPollInterval the status polling interval (a null, zero, or negative value will
   *                           be changed to {@link #DEFAULT_STATUS_POLL_INTERVAL})
   * @return the builder
   * @see #DEFAULT_STATUS_POLL_INTERVAL
   */
  public BigSegmentsConfigurationBuilder statusPollInterval(Duration statusPollInterval) {
    this.statusPollInterval = statusPollInterval != null && statusPollInterval.compareTo(Duration.ZERO) > 0
        ? statusPollInterval : DEFAULT_STATUS_POLL_INTERVAL;
    return this;
  }

  /**
   * Sets the maximum length of time between updates of the Big Segments data before the data is
   * considered out of date.
   * <p>
   * Normally, the LaunchDarkly Relay Proxy updates a timestamp in the Big Segments store at
   * intervals to confirm that it is still in sync with the LaunchDarkly data, even if there have
   * been no changes to the data. If the timestamp falls behind the current time by the amount
   * specified by {@code staleAfter}, the SDK assumes that something is not working correctly in
   * this process and that the data may not be accurate.
   * <p>
   * While in a stale state, the SDK will still continue using the last known data, but
   * {@link com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider.Status} will return
   * true in its {@code stale} property, and any {@link EvaluationReason} generated from a feature
   * flag that references a Big Segment will have a {@link BigSegmentsStatus} of
   * {@link BigSegmentsStatus#STALE}.
   *
   * @param staleAfter the time limit for marking the data as stale (a null, zero, or negative
   *                   value will be changed to {@link #DEFAULT_STALE_AFTER})
   * @return the builder
   * @see #DEFAULT_STALE_AFTER
   */
  public BigSegmentsConfigurationBuilder staleAfter(Duration staleAfter) {
    this.staleAfter = staleAfter != null && staleAfter.compareTo(Duration.ZERO) > 0
        ? staleAfter : DEFAULT_STALE_AFTER;
    return this;
  }

  @Override
  public BigSegmentsConfiguration build(ClientContext context) {
    BigSegmentStore store = storeConfigurer == null ? null : storeConfigurer.build(context);
    return new BigSegmentsConfiguration(
        store,
        userCacheSize,
        userCacheTime,
        statusPollInterval,
        staleAfter);
  }
}
