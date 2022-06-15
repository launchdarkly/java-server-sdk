package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.ServiceEndpoints;
import java.net.URI;

/**
 * Contains methods for configuring the SDK's service URIs.
 * <p>
 * If you want to set non-default values for any of these properties, create a builder with {@link Components#serviceEndpoints()},
 * change its properties with the methods of this class, and pass it to {@link LDConfig.Builder#serviceEndpoints(ServiceEndpointsBuilder)}.
 * <p>
 * The default behavior, if you do not change any of these properties, is that the SDK will connect to the standard endpoints
 * in the LaunchDarkly production service. There are several use cases for changing these properties:
 * <ul>
 * <li> You are using the <a href="https://docs.launchdarkly.com/home/advanced/relay-proxy">LaunchDarkly Relay Proxy</a>.
 * In this case, set {@link #relayProxy(URI)}.
 * <li> You are connecting to a private instance of LaunchDarkly, rather than the standard production services.
 * In this case, there will be custom base URIs for each service, so you must set {@link #streaming(URI)},
 * {@link #polling(URI)}, and {@link #events(URI)}.
 * <li> You are connecting to a test fixture that simulates the service endpoints. In this case, you may set the
 * base URIs to whatever you want, although the SDK will still set the URI paths to the expected paths for
 * LaunchDarkly services.
 * </ul>
 * <p>
 * Each of the setter methods can be called with either a {@link URI} or an equivalent string.
 * Passing a string that is not a valid URI will cause an immediate {@link IllegalArgumentException}.
 * <p>
 * If you are using a private instance and you set some of the base URIs, but not all of them, the SDK
 * will log an error and may not work properly. The only exception is if you have explicitly disabled
 * the SDK's use of one of the services: for instance, if you have disabled analytics events with
 * {@link Components#noEvents()}, you do not have to set {@link #events(URI)}.
 *
 * <pre><code>
 *     // Example of specifying a Relay Proxy instance
 *     LDConfig config = new LDConfig.Builder()
 *         .serviceEndpoints(
 *             Components.serviceEndpoints()
 *                 .relayProxy("http://my-relay-hostname:80")
 *         )
 *         .build();
 * 
 *     // Example of specifying a private LaunchDarkly instance
 *     LDConfig config = new LDConfig.Builder()
 *         .serviceEndpoints(
 *             Components.serviceEndpoints()
 *                 .streaming("https://stream.mycompany.launchdarkly.com")
 *                 .polling("https://app.mycompany.launchdarkly.com")
 *                 .events("https://events.mycompany.launchdarkly.com"))
 *         )
 *         .build();
 * </code></pre>
 *
 * @since 5.9.0
 */
public abstract class ServiceEndpointsBuilder {
  protected URI streamingBaseUri;
  protected URI pollingBaseUri;
  protected URI eventsBaseUri;

  /**
   * Sets a custom base URI for the events service.
   * <p>
   * You should only call this method if you are using a private instance or test fixture
   * (see {@link ServiceEndpointsBuilder}). If you are using the LaunchDarkly Relay Proxy,
   * call {@link #relayProxy(URI)} instead.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *       .serviceEndpoints(
   *           Components.serviceEndpoints()
   *               .streaming("https://stream.mycompany.launchdarkly.com")
   *               .polling("https://app.mycompany.launchdarkly.com")
   *               .events("https://events.mycompany.launchdarkly.com")
   *       )
   *       .build();
   * </code></pre>
   * 
   * @param eventsBaseUri the base URI of the events service; null to use the default
   * @return the builder
   */
  public ServiceEndpointsBuilder events(URI eventsBaseUri) {
    this.eventsBaseUri = eventsBaseUri;
    return this;
  }

  /**
   * Equivalent to {@link #events(URI)}, specifying the URI as a string.
   * @param eventsBaseUri the base URI of the events service; null to use the default
   * @return the builder
   */
  public ServiceEndpointsBuilder events(String eventsBaseUri) {
    return events(eventsBaseUri == null ? null : URI.create(eventsBaseUri));
  }

  /**
   * Sets a custom base URI for the polling service.
   * <p>
   * You should only call this method if you are using a private instance or test fixture
   * (see {@link ServiceEndpointsBuilder}). If you are using the LaunchDarkly Relay Proxy,
   * call {@link #relayProxy(URI)} instead.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *       .serviceEndpoints(
   *           Components.serviceEndpoints()
   *               .streaming("https://stream.mycompany.launchdarkly.com")
   *               .polling("https://app.mycompany.launchdarkly.com")
   *               .events("https://events.mycompany.launchdarkly.com")
   *       )
   *       .build();
   * </code></pre>
   * 
   * @param pollingBaseUri the base URI of the polling service; null to use the default
   * @return the builder
   */
  public ServiceEndpointsBuilder polling(URI pollingBaseUri) {
    this.pollingBaseUri = pollingBaseUri;
    return this;
  }

  /**
   * Equivalent to {@link #polling(URI)}, specifying the URI as a string.
   * @param pollingBaseUri the base URI of the events service; null to use the default
   * @return the builder
   */
  public ServiceEndpointsBuilder polling(String pollingBaseUri) {
    return polling(pollingBaseUri == null ? null : URI.create(pollingBaseUri));
  }

  /**
   * Specifies a single base URI for a Relay Proxy instance.
   * <p>
   * When using the LaunchDarkly Relay Proxy, the SDK only needs to know the single base URI
   * of the Relay Proxy, which will provide all the proxied service endpoints.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *       .serviceEndpoints(
   *           Components.serviceEndpoints()
   *               .relayProxy("http://my-relay-hostname:8080")
   *       )
   *       .build();
   * </code></pre>
   * 
   * @param relayProxyBaseUri the Relay Proxy base URI, or null to reset to default endpoints
   * @return the builder
   */
  public ServiceEndpointsBuilder relayProxy(URI relayProxyBaseUri) {
    this.eventsBaseUri = relayProxyBaseUri;
    this.pollingBaseUri = relayProxyBaseUri;
    this.streamingBaseUri = relayProxyBaseUri;
    return this;
  }

  /**
   * Equivalent to {@link #relayProxy(URI)}, specifying the URI as a string.
   * @param relayProxyBaseUri the Relay Proxy base URI, or null to reset to default endpoints
   * @return the builder
   */
  public ServiceEndpointsBuilder relayProxy(String relayProxyBaseUri) {
    return relayProxy(relayProxyBaseUri == null ? null : URI.create(relayProxyBaseUri));
  }

  /**
   * Sets a custom base URI for the streaming service.
   * <p>
   * You should only call this method if you are using a private instance or test fixture
   * (see {@link ServiceEndpointsBuilder}). If you are using the LaunchDarkly Relay Proxy,
   * call {@link #relayProxy(URI)} instead.
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *       .serviceEndpoints(
   *           Components.serviceEndpoints()
   *               .streaming("https://stream.mycompany.launchdarkly.com")
   *               .polling("https://app.mycompany.launchdarkly.com")
   *               .events("https://events.mycompany.launchdarkly.com")
   *       )
   *       .build();
   * </code></pre>
   * 
   * @param streamingBaseUri the base URI of the streaming service; null to use the default
   * @return the builder
   */
  public ServiceEndpointsBuilder streaming(URI streamingBaseUri) {
    this.streamingBaseUri = streamingBaseUri;
    return this;
  }

  /**
   * Equivalent to {@link #streaming(URI)}, specifying the URI as a string.
   * @param streamingBaseUri the base URI of the events service; null to use the default
   * @return the builder
   */
  public ServiceEndpointsBuilder streaming(String streamingBaseUri) {
    return streaming(streamingBaseUri == null ? null : URI.create(streamingBaseUri));
  }

  /**
   * Called internally by the SDK to create a configuration instance. Applications do not need
   * to call this method.
   * @return the configuration object
   */
  abstract public ServiceEndpoints createServiceEndpoints();
}
