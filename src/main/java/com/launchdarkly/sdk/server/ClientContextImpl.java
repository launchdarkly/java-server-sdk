package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.server.interfaces.BasicConfiguration;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;
import com.launchdarkly.sdk.server.interfaces.LoggingConfiguration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * This is the package-private implementation of {@link ClientContext} that contains additional non-public
 * SDK objects that may be used by our internal components.
 * <p>
 * All component factories, whether they are built-in ones or custom ones from the application, receive a
 * {@link ClientContext} and can access its public properties. But only our built-in ones can see the
 * package-private properties, which they can do by calling {@code ClientContextImpl.get(ClientContext)}
 * to make sure that what they have is really a {@code ClientContextImpl} (as opposed to some other
 * implementation of {@link ClientContext}, which might have been created for instance in application
 * test code).
 */
final class ClientContextImpl implements ClientContext {
  private static volatile ScheduledExecutorService fallbackSharedExecutor = null;
  
  private final BasicConfiguration basicConfiguration;
  private final HttpConfiguration httpConfiguration;
  private final LoggingConfiguration loggingConfiguration;
  final ScheduledExecutorService sharedExecutor;
  final DiagnosticAccumulator diagnosticAccumulator;
  final DiagnosticEvent.Init diagnosticInitEvent;

  private ClientContextImpl(
      BasicConfiguration basicConfiguration,
      HttpConfiguration httpConfiguration,
      LoggingConfiguration loggingConfiguration,
      ScheduledExecutorService sharedExecutor,
      DiagnosticAccumulator diagnosticAccumulator,
      DiagnosticEvent.Init diagnosticInitEvent
  ) {
    this.basicConfiguration = basicConfiguration;
    this.httpConfiguration = httpConfiguration;
    this.loggingConfiguration = loggingConfiguration;
    this.sharedExecutor = sharedExecutor;
    this.diagnosticAccumulator = diagnosticAccumulator;
    this.diagnosticInitEvent = diagnosticInitEvent;
  }

  ClientContextImpl(
      String sdkKey,
      LDConfig configuration,
      ScheduledExecutorService sharedExecutor,
      DiagnosticAccumulator diagnosticAccumulator
  ) {
    LDLogger baseLogger;
    // There is some temporarily over-elaborate logic here because the component factory interfaces can't
    // be updated to make the dependencies more sensible till the next major version.
    BasicConfiguration tempBasic = new BasicConfiguration(sdkKey, configuration.offline, configuration.threadPriority,
        configuration.applicationInfo, configuration.serviceEndpoints, LDLogger.none());
    this.loggingConfiguration = configuration.loggingConfigFactory.createLoggingConfiguration(tempBasic);
    if (this.loggingConfiguration instanceof LoggingConfiguration.AdapterOptions) {
      LoggingConfiguration.AdapterOptions ao = (LoggingConfiguration.AdapterOptions)this.loggingConfiguration;
      baseLogger = LDLogger.withAdapter(ao.getLogAdapter(), ao.getBaseLoggerName());
    } else {
      baseLogger = makeDefaultLogger(tempBasic);
    }
    
    this.basicConfiguration = new BasicConfiguration(
        sdkKey,
        configuration.offline,
        configuration.threadPriority,
        configuration.applicationInfo,
        configuration.serviceEndpoints,
        baseLogger
        );
    
    this.httpConfiguration = configuration.httpConfigFactory.createHttpConfiguration(basicConfiguration);
 
    
    if (this.httpConfiguration.getProxy() != null) {
      baseLogger.info("Using proxy: {} {} authentication.",
          this.httpConfiguration.getProxy(),
          this.httpConfiguration.getProxyAuthentication() == null ? "without" : "with");
    }
    
    this.sharedExecutor = sharedExecutor;
    
    if (!configuration.diagnosticOptOut && diagnosticAccumulator != null) {
      this.diagnosticAccumulator = diagnosticAccumulator;
      this.diagnosticInitEvent = new DiagnosticEvent.Init(
          diagnosticAccumulator.dataSinceDate,
          diagnosticAccumulator.diagnosticId,
          configuration,
          basicConfiguration,
          httpConfiguration
          );
    } else {
      this.diagnosticAccumulator = null;
      this.diagnosticInitEvent = null;
    }
  }

  @Override
  public BasicConfiguration getBasic() {
    return basicConfiguration;
  }
  
  @Override
  public HttpConfiguration getHttp() {
    return httpConfiguration;
  }

  @Override
  public LoggingConfiguration getLogging() {
    return loggingConfiguration;
  }
  
//  @Override
//  public LDLogger getBaseLogger() {
//    return baseLogger;
//  }
  
  private static LDLogger makeDefaultLogger(BasicConfiguration basicConfiguration) {
    LoggingConfiguration lc = Components.logging().createLoggingConfiguration(basicConfiguration);
    if (lc instanceof LoggingConfiguration.AdapterOptions) {
      LoggingConfiguration.AdapterOptions ao = (LoggingConfiguration.AdapterOptions)lc;
      return LDLogger.withAdapter(ao.getLogAdapter(), ao.getBaseLoggerName());
    }
    return LDLogger.withAdapter(Logs.none(), "");
  }
  
  /**
   * This mechanism is a convenience for internal components to access the package-private fields of the
   * context if it is a ClientContextImpl, and to receive null values for those fields if it is not.
   * The latter case should only happen in application test code where the application developer has no
   * way to create our package-private ClientContextImpl. In that case, we also generate a temporary
   * sharedExecutor so components can work correctly in tests.
   */
  static ClientContextImpl get(ClientContext context) {
    if (context instanceof ClientContextImpl) {
      return (ClientContextImpl)context;
    }
    synchronized (ClientContextImpl.class) {
      if (fallbackSharedExecutor == null) {
        fallbackSharedExecutor = Executors.newSingleThreadScheduledExecutor();
      }
    }
    return new ClientContextImpl(
        context.getBasic(),
        context.getHttp(),
        context.getLogging(),
        fallbackSharedExecutor,
        null,
        null
        );
  }
}
