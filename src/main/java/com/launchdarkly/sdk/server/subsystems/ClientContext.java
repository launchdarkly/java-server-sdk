package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.ApplicationInfo;
import com.launchdarkly.sdk.server.interfaces.ServiceEndpoints;

/**
 * Context information provided by the {@link com.launchdarkly.sdk.server.LDClient} when creating components.
 * <p>
 * This is passed as a parameter to {@link DataStoreFactory#createDataStore(ClientContext, DataStoreUpdates)},
 * etc. Component factories do not receive the entire {@link com.launchdarkly.sdk.server.LDConfig} because
 * it could contain factory objects that have mutable state, and because components should not be able
 * to access the configurations of unrelated components.
 * <p>
 * The actual implementation class may contain other properties that are only relevant to the built-in
 * SDK components and are therefore not part of this base class; this allows the SDK to add its own context
 * information as needed without disturbing the public API.
 * 
 * @since 5.0.0
 */
public class ClientContext {
  private final String sdkKey;
  private final ApplicationInfo applicationInfo;
  private final HttpConfiguration http;
  private final LoggingConfiguration logging;
  private final boolean offline;
  private final ServiceEndpoints serviceEndpoints;
  private final int threadPriority;

  /**
   * Constructor that sets all properties. All should be non-null.
   * 
   * @param sdkKey the SDK key
   * @param applicationInfo application metadata properties from
   *   {@link LDConfig.Builder#applicationInfo(com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder)}
   * @param http HTTP configuration properties from
   *   {@link LDConfig.Builder#http(HttpConfigurationFactory)}
   * @param logging logging configuration properties from
   *   {@link LDConfig.Builder#logging(LoggingConfigurationFactory)}
   * @param offline true if the SDK should be entirely offline
   * @param serviceEndpoints service endpoint URI properties from
   *   {@link LDConfig.Builder#serviceEndpoints(com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder)}
   * @param threadPriority worker thread priority from
   *   {@link LDConfig.Builder#threadPriority(int)}
   */
  public ClientContext(
      String sdkKey,
      ApplicationInfo applicationInfo,
      HttpConfiguration http,
      LoggingConfiguration logging,
      boolean offline,
      ServiceEndpoints serviceEndpoints,
      int threadPriority
      ) {
    this.sdkKey = sdkKey;
    this.applicationInfo = applicationInfo;
    this.http = http;
    this.logging = logging;
    this.offline = offline;
    this.serviceEndpoints = serviceEndpoints;
    this.threadPriority = threadPriority;
  }
  
  /**
   * Basic constructor for convenience in testing, using defaults for most properties.
   * 
   * @param sdkKey the SDK key
   */
  public ClientContext(String sdkKey) {
    this(
        sdkKey,
        new ApplicationInfo(null, null),
        defaultHttp(sdkKey),
        defaultLogging(),
        false,
        Components.serviceEndpoints().createServiceEndpoints(),
        Thread.MIN_PRIORITY
        );
  }
  
  private static HttpConfiguration defaultHttp(String sdkKey) {
    ClientContext minimalContext = new ClientContext(sdkKey, null, null, null, false, null, 0);
    return Components.httpConfiguration().createHttpConfiguration(minimalContext);
  }
  
  private static LoggingConfiguration defaultLogging() {
    ClientContext minimalContext = new ClientContext("", null, null, null, false, null, 0);
    return Components.logging().createLoggingConfiguration(minimalContext);
  }
  
  public String getSdkKey() {
    return sdkKey;
  }
  
  public ApplicationInfo getApplicationInfo() {
    return applicationInfo;
  }
  
  /**
   * The configured networking properties that apply to all components.
   * 
   * @return the HTTP configuration
   */
  public HttpConfiguration getHttp() {
    return http;
  }

  /**
   * The configured logging properties that apply to all components.
   * @return the logging configuration
   */
  public LoggingConfiguration getLogging() {
    return logging;
  }
  
  public boolean isOffline() {
    return offline;
  }
  
  public ServiceEndpoints getServiceEndpoints() {
    return serviceEndpoints;
  }
  
  public int getThreadPriority() {
    return threadPriority;
  }
}
