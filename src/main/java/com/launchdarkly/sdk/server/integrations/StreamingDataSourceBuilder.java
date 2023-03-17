package com.launchdarkly.sdk.server.integrations;

import java.time.Duration;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig.Builder;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;

/**
 * Contains methods for configuring the streaming data source.
 * <p>
 * By default, the SDK uses a streaming connection to receive feature flag data from LaunchDarkly. If you want
 * to customize the behavior of the connection, create a builder with {@link Components#streamingDataSource()},
 * change its properties with the methods of this class, and pass it to {@link Builder#dataSource(ComponentConfigurer)}:
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .dataSource(Components.streamingDataSource().initialReconnectDelayMillis(500))
 *         .build();
 * </code></pre>
 * <p>
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#streamingDataSource()}.
 * 
 * @since 4.12.0
 */
public abstract class StreamingDataSourceBuilder implements ComponentConfigurer<DataSource> {
  /**
   * The default value for {@link #initialReconnectDelay(Duration)}: 1000 milliseconds.
   */
  public static final Duration DEFAULT_INITIAL_RECONNECT_DELAY = Duration.ofMillis(1000);
  
  protected Duration initialReconnectDelay = DEFAULT_INITIAL_RECONNECT_DELAY;

  protected String payloadFilter;

  /**
   * Sets the initial reconnect delay for the streaming connection.
   * <p>
   * The streaming service uses a backoff algorithm (with jitter) every time the connection needs
   * to be reestablished. The delay for the first reconnection will start near this value, and then
   * increase exponentially for any subsequent connection failures.
   * <p>
   * The default value is {@link #DEFAULT_INITIAL_RECONNECT_DELAY}.
   * 
   * @param initialReconnectDelay the reconnect time base value; null to use the default
   * @return the builder
   */
  
  public StreamingDataSourceBuilder initialReconnectDelay(Duration initialReconnectDelay) {
    this.initialReconnectDelay = initialReconnectDelay == null ? DEFAULT_INITIAL_RECONNECT_DELAY : initialReconnectDelay;
    return this;
  }

  /**
   * Sets the Payload Filter that will be used to filter the objects (flags, segments, etc.)
   * from this data source.
   * 
   * @param payloadFilter the filter to be used
   * @return the builder
   */
  public StreamingDataSourceBuilder payloadFilter(String payloadFilter) {
    this.payloadFilter = payloadFilter;
    return this;
  }
}
