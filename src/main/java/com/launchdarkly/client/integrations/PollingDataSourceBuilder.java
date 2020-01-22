package com.launchdarkly.client.integrations;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.UpdateProcessorFactory;

import java.net.URI;

/**
 * Contains methods for configuring the polling data source.
 * <p>
 * This is not the default behavior; by default, the SDK uses a streaming connection to receive feature flag
 * data from LaunchDarkly. In polling mode, the SDK instead makes a new HTTP request to LaunchDarkly at regular
 * intervals. HTTP caching allows it to avoid redundantly downloading data if there have been no changes, but
 * polling is still less efficient than streaming and should only be used on the advice of LaunchDarkly support.
 * <p>
 * To use polling mode, create a builder with {@link Components#pollingDataSource()},
 * change its properties with the methods of this class, and pass it to {@link com.launchdarkly.client.LDConfig.Builder#dataSource(UpdateProcessorFactory)}:
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .dataSource(Components.pollingDataSource().pollIntervalMillis(45000))
 *         .build();
 * </code></pre>
 * <p>
 * These properties will override any equivalent deprecated properties that were set with {@code LDConfig.Builder},
 * such as {@link com.launchdarkly.client.LDConfig.Builder#pollingIntervalMillis(long)}.
 * <p>
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#pollingDataSource()}.
 * 
 * @since 4.12.0
 */
public abstract class PollingDataSourceBuilder implements UpdateProcessorFactory {
  /**
   * The default and minimum value for {@link #pollIntervalMillis(long)}.
   */
  public static final long DEFAULT_POLL_INTERVAL_MILLIS = 30000L;
  
  protected URI baseUri;
  protected long pollIntervalMillis = DEFAULT_POLL_INTERVAL_MILLIS;

  /**
   * Sets a custom base URI for the polling service.
   * <p>
   * You will only need to change this value in the following cases:
   * <ul>
   * <li> You are using the <a href="https://docs.launchdarkly.com/docs/the-relay-proxy">Relay Proxy</a>. Set
   *   {@code streamUri} to the base URI of the Relay Proxy instance.
   * <li> You are connecting to a test server or anything else other than the standard LaunchDarkly service.
   * </ul>
   * 
   * @param baseUri the base URI of the polling service; null to use the default
   * @return the builder
   */
  public PollingDataSourceBuilder baseUri(URI baseUri) {
    this.baseUri = baseUri;
    return this;
  }
  
  /**
   * Sets the interval at which the SDK will poll for feature flag updates.
   * <p>
   * The default and minimum value is {@link #DEFAULT_POLL_INTERVAL_MILLIS}. Values less than this will be
   * set to the default.
   * 
   * @param pollIntervalMillis the polling interval in milliseconds 
   * @return the builder
   */
  public PollingDataSourceBuilder pollIntervalMillis(long pollIntervalMillis) {
    this.pollIntervalMillis = pollIntervalMillis < DEFAULT_POLL_INTERVAL_MILLIS ?
        DEFAULT_POLL_INTERVAL_MILLIS :
        pollIntervalMillis;
    return this;
  }
}
