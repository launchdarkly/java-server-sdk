package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig.Builder;
import com.launchdarkly.sdk.server.integrations.WrapperInfoBuilder;
import com.launchdarkly.sdk.server.interfaces.ApplicationInfo;
import com.launchdarkly.sdk.server.interfaces.ServiceEndpoints;
import com.launchdarkly.sdk.server.interfaces.WrapperInfo;

/**
 * Context information provided by the {@link com.launchdarkly.sdk.server.LDClient} when creating components.
 * <p>
 * This is passed as a parameter to component factories that implement {@link ComponentConfigurer}.
 * Component factories do not receive the entire {@link com.launchdarkly.sdk.server.LDConfig} because
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
  private final LDLogger baseLogger;
  private final HttpConfiguration http;
  private final LoggingConfiguration logging;
  private final boolean offline;
  private final ServiceEndpoints serviceEndpoints;
  private final int threadPriority;
  private WrapperInfo wrapperInfo;

  /**
   * Constructor that sets all properties. All should be non-null.
   * 
   * @param sdkKey the SDK key
   * @param applicationInfo application metadata properties from
   *   {@link Builder#applicationInfo(com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder)}
   * @param http HTTP configuration properties from {@link Builder#http(ComponentConfigurer)}
   * @param logging logging configuration properties from {@link Builder#logging(ComponentConfigurer)}
   * @param offline true if the SDK should be entirely offline
   * @param serviceEndpoints service endpoint URI properties from
   *   {@link Builder#serviceEndpoints(com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder)}
   * @param threadPriority worker thread priority from {@link Builder#threadPriority(int)}
   * @param wrapperInfo wrapper configuration from {@link Builder#wrapper(com.launchdarkly.sdk.server.integrations.WrapperInfoBuilder)}
   */
  public ClientContext(
      String sdkKey,
      ApplicationInfo applicationInfo,
      HttpConfiguration http,
      LoggingConfiguration logging,
      boolean offline,
      ServiceEndpoints serviceEndpoints,
      int threadPriority,
      WrapperInfo wrapperInfo
      ) {
    this.sdkKey = sdkKey;
    this.applicationInfo = applicationInfo;
    this.http = http;
    this.logging = logging;
    this.offline = offline;
    this.serviceEndpoints = serviceEndpoints;
    this.threadPriority = threadPriority;
    this.wrapperInfo = wrapperInfo;
    
    this.baseLogger = logging == null ? LDLogger.none() :
      LDLogger.withAdapter(logging.getLogAdapter(), logging.getBaseLoggerName());
  }
  
  /**
   * Copy constructor.
   * 
   * @param copyFrom the instance to copy from
   */
  protected ClientContext(ClientContext copyFrom) {
    this(copyFrom.sdkKey, copyFrom.applicationInfo, copyFrom.http, copyFrom.logging,
        copyFrom.offline, copyFrom.serviceEndpoints, copyFrom.threadPriority, copyFrom.wrapperInfo);
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
        Thread.MIN_PRIORITY,
      null
        );
  }
  
  private static HttpConfiguration defaultHttp(String sdkKey) {
    ClientContext minimalContext = new ClientContext(sdkKey, null, null, null, false, null, 0, null);
    return Components.httpConfiguration().build(minimalContext);
  }
  
  private static LoggingConfiguration defaultLogging() {
    ClientContext minimalContext = new ClientContext("", null, null, null, false, null, 0, null);
    return Components.logging().build(minimalContext);
  }
  
  /**
   * Returns the configured SDK key.
   * 
   * @return the SDK key
   */
  public String getSdkKey() {
    return sdkKey;
  }
  
  /**
   * Returns the application metadata, if any, set by
   * {@link Builder#applicationInfo(com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder)}.
   * 
   * @return the application metadata or null
   */
  public ApplicationInfo getApplicationInfo() {
    return applicationInfo;
  }
  
  /**
   * The base logger for the SDK.
   * @return a logger instance
   */
  public LDLogger getBaseLogger() {
    return baseLogger;
  }

  /**
   * Returns the component that {@link DataSource} implementations use to deliver data and status
   * updates to the SDK.
   * <p>
   * This component is only available when the SDK is calling a {@link DataSource} factory.
   * Otherwise the method returns null.
   *
   * @return the {@link DataSourceUpdateSink}, if applicable
   */
  public DataSourceUpdateSink getDataSourceUpdateSink() {
    return null;
  }
  
  /**
   * Returns the component that {@link DataStore} implementations use to deliver data store status
   * updates to the SDK.
   * <p>
   * This component is only available when the SDK is calling a {@link DataStore} factory.
   * Otherwise the method returns null.
   *
   * @return the {@link DataStoreUpdateSink}, if applicable
   */
  public DataStoreUpdateSink getDataStoreUpdateSink() {
    return null;
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
  
  /**
   * Returns true if the SDK was configured to be completely offline.
   * 
   * @return true if configured to be offline
   */
  public boolean isOffline() {
    return offline;
  }
  
  /**
   * Returns the base service URIs used by SDK components.
   * 
   * @return the service endpoint URIs
   */
  public ServiceEndpoints getServiceEndpoints() {
    return serviceEndpoints;
  }
  
  /**
   * Returns the worker thread priority that is set by
   * {@link Builder#threadPriority(int)}.
   * 
   * @return the thread priority
   */
  public int getThreadPriority() {
    return threadPriority;
  }

  /**
   * Returns the wrapper information.
   *
   * @return the wrapper information
   */
  public WrapperInfo getWrapperInfo() {
    return wrapperInfo;
  }
}
