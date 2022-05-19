package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.ApplicationInfo;
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
    this.basicConfiguration = new BasicConfiguration(sdkKey, configuration.offline, configuration.threadPriority, configuration.applicationInfo, configuration.serviceEndpoints);
    
    this.httpConfiguration = configuration.httpConfigFactory.createHttpConfiguration(basicConfiguration);
    this.loggingConfiguration = configuration.loggingConfigFactory.createLoggingConfiguration(basicConfiguration);
        
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
