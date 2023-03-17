package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig.Builder;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;

import java.time.Duration;

/**
 * Contains methods for configuring the polling data source.
 * <p>
 * Polling is not the default behavior; by default, the SDK uses a streaming connection to receive feature flag
 * data from LaunchDarkly. In polling mode, the SDK instead makes a new HTTP request to LaunchDarkly at regular
 * intervals. HTTP caching allows it to avoid redundantly downloading data if there have been no changes, but
 * polling is still less efficient than streaming and should only be used on the advice of LaunchDarkly support.
 * <p>
 * To use polling mode, create a builder with {@link Components#pollingDataSource()},
 * change its properties with the methods of this class, and pass it to {@link Builder#dataSource(ComponentConfigurer)}:
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .dataSource(Components.pollingDataSource().pollInterval(Duration.ofSeconds(45)))
 *         .build();
 * </code></pre>
 * <p>
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#pollingDataSource()}.
 * 
 * @since 4.12.0
 */
public abstract class PollingDataSourceBuilder implements ComponentConfigurer<DataSource> {
  /**
   * The default and minimum value for {@link #pollInterval(Duration)}: 30 seconds.
   */
  public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(30);
  
  protected Duration pollInterval = DEFAULT_POLL_INTERVAL;

  protected String payloadFilter;
 
  /**
   * Sets the interval at which the SDK will poll for feature flag updates.
   * <p>
   * The default and minimum value is {@link #DEFAULT_POLL_INTERVAL}. Values less than this will be
   * set to the default.
   * 
   * @param pollInterval the polling interval; null to use the default 
   * @return the builder
   */
  public PollingDataSourceBuilder pollInterval(Duration pollInterval) {
    if (pollInterval == null) {
      this.pollInterval = DEFAULT_POLL_INTERVAL;
    } else {
      this.pollInterval = pollInterval.compareTo(DEFAULT_POLL_INTERVAL) < 0 ? DEFAULT_POLL_INTERVAL : pollInterval;
    }
    return this;
  }

  /**
   * Sets the Payload Filter that will be used to filter the objects (flags, segments, etc.)
   * from this data source.
   * 
   * @param payloadFilter the filter to be used
   * @return the builder
   */
  public PollingDataSourceBuilder payloadFilter(String payloadFilter) {
    this.payloadFilter = payloadFilter;
    return this;
  }
}
