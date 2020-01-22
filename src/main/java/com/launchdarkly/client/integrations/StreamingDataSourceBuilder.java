package com.launchdarkly.client.integrations;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.UpdateProcessorFactory;

import java.net.URI;

/**
 * Contains methods for configuring the streaming data source.
 * <p>
 * By default, the SDK uses a streaming connection to receive feature flag data from LaunchDarkly. If you want
 * to customize the behavior of the connection, create a builder with {@link Components#streamingDataSource()},
 * change its properties with the methods of this class, and pass it to {@link com.launchdarkly.client.LDConfig.Builder#dataSource(UpdateProcessorFactory)}:
 * <code><pre>
 *     LDConfig config = new LDConfig.Builder()
 *         .dataSource(Components.streamingDataSource().initialReconnectDelayMillis(500))
 *         .build();
 * </pre></code>
 * <p>
 * These properties will override any equivalent deprecated properties that were set with {@code LDConfig.Builder},
 * such as {@link com.launchdarkly.client.LDConfig.Builder#reconnectTimeMs(long)}.
 * <p>
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#pollingDataSource()}.
 * 
 * @since 4.12.0
 */
public abstract class StreamingDataSourceBuilder implements UpdateProcessorFactory {
  /**
   * The default value for {@link #initialReconnectDelayMillis(long)}.
   */
  public static final long DEFAULT_INITIAL_RECONNECT_DELAY_MILLIS = 1000;
  
  protected URI baseUri;
  protected URI pollingBaseUri;
  protected long initialReconnectDelayMillis = DEFAULT_INITIAL_RECONNECT_DELAY_MILLIS;

  /**
   * Sets a custom base URI for the streaming service.
   * <p>
   * You will only need to change this value in the following cases:
   * <ul>
   * <li> You are using the <a href="https://docs.launchdarkly.com/docs/the-relay-proxy">Relay Proxy</a>. Set
   *   {@code baseUri} to the base URI of the Relay Proxy instance.
   * <li> You are connecting to a test server or a nonstandard endpoint for the LaunchDarkly service.
   * </ul>
   * 
   * @param baseUri the base URI of the streaming service; null to use the default
   * @return the builder
   */
  public StreamingDataSourceBuilder baseUri(URI baseUri) {
    this.baseUri = baseUri;
    return this;
  }
  
  /**
   * Sets the initial reconnect delay for the streaming connection.
   * <p>
   * The streaming service uses a backoff algorithm (with jitter) every time the connection needs
   * to be reestablished. The delay for the first reconnection will start near this value, and then
   * increase exponentially for any subsequent connection failures.
   * <p>
   * The default value is {@link #DEFAULT_INITIAL_RECONNECT_DELAY_MILLIS}.
   * 
   * @param initialReconnectDelayMillis the reconnect time base value in milliseconds
   * @return the builder
   */
  
  public StreamingDataSourceBuilder initialReconnectDelayMillis(long initialReconnectDelayMillis) {
    this.initialReconnectDelayMillis = initialReconnectDelayMillis;
    return this;
  }
  
  /**
   * Sets a custom base URI for special polling requests.
   * <p>
   * Even in streaming mode, the SDK sometimes temporarily must do a polling request. You do not need to
   * modify this property unless you are connecting to a test server or a nonstandard endpoing for the
   * LaunchDarkly service. If you are using the <a href="https://docs.launchdarkly.com/docs/the-relay-proxy">Relay Proxy</a>,
   * you only need to set {@link #baseUri(URI)}.
   *  
   * @param pollingBaseUri the polling endpoint URI; null to use the default
   * @return the builder
   */
  public StreamingDataSourceBuilder pollingBaseUri(URI pollingBaseUri) {
    this.pollingBaseUri = pollingBaseUri;
    return this;
  }
}
