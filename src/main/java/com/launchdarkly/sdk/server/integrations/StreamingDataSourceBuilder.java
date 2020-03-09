package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;

import java.net.URI;
import java.time.Duration;

/**
 * Contains methods for configuring the streaming data source.
 * <p>
 * By default, the SDK uses a streaming connection to receive feature flag data from LaunchDarkly. If you want
 * to customize the behavior of the connection, create a builder with {@link Components#streamingDataSource()},
 * change its properties with the methods of this class, and pass it to {@link com.launchdarkly.sdk.server.LDConfig.Builder#dataSource(DataSourceFactory)}:
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
public abstract class StreamingDataSourceBuilder implements DataSourceFactory {
  /**
   * The default value for {@link #initialReconnectDelay(Duration)}: 1000 milliseconds.
   */
  public static final Duration DEFAULT_INITIAL_RECONNECT_DELAY = Duration.ofMillis(1000);
  
  protected URI baseURI;
  protected URI pollingBaseURI;
  protected Duration initialReconnectDelay = DEFAULT_INITIAL_RECONNECT_DELAY;

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
   * @param baseURI the base URI of the streaming service; null to use the default
   * @return the builder
   */
  public StreamingDataSourceBuilder baseURI(URI baseURI) {
    this.baseURI = baseURI;
    return this;
  }
  
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
   * Sets a custom base URI for special polling requests.
   * <p>
   * Even in streaming mode, the SDK sometimes temporarily must do a polling request. You do not need to
   * modify this property unless you are connecting to a test server or a nonstandard endpoint for the
   * LaunchDarkly service. If you are using the <a href="https://docs.launchdarkly.com/docs/the-relay-proxy">Relay Proxy</a>,
   * you only need to set {@link #baseURI(URI)}.
   *  
   * @param pollingBaseURI the polling endpoint URI; null to use the default
   * @return the builder
   */
  public StreamingDataSourceBuilder pollingBaseURI(URI pollingBaseURI) {
    this.pollingBaseURI = pollingBaseURI;
    return this;
  }
}
