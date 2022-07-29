package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.internal.events.DiagnosticStore;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

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
final class ClientContextImpl extends ClientContext {
  private static volatile ScheduledExecutorService fallbackSharedExecutor = null;
  
  final ScheduledExecutorService sharedExecutor;
  final DiagnosticStore diagnosticStore;

  private ClientContextImpl(
      ClientContext baseContext,
      ScheduledExecutorService sharedExecutor,
      DiagnosticStore diagnosticStore
  ) {
    super(baseContext.getSdkKey(), baseContext.getApplicationInfo(), baseContext.getHttp(),
        baseContext.getLogging(), baseContext.isOffline(), baseContext.getServiceEndpoints(),
        baseContext.getThreadPriority());
    this.sharedExecutor = sharedExecutor;
    this.diagnosticStore = diagnosticStore;
  }

  static ClientContextImpl fromConfig(
      String sdkKey,
      LDConfig config,
      ScheduledExecutorService sharedExecutor
      ) {
    ClientContext minimalContext = new ClientContext(sdkKey, config.applicationInfo, null,
        null, config.offline, config.serviceEndpoints, config.threadPriority);
    LoggingConfiguration loggingConfig = config.loggingConfigFactory.createLoggingConfiguration(minimalContext);
    
    ClientContext contextWithLogging = new ClientContext(sdkKey, config.applicationInfo, null,
        loggingConfig, config.offline, config.serviceEndpoints, config.threadPriority);
    HttpConfiguration httpConfig = config.httpConfigFactory.createHttpConfiguration(contextWithLogging);
    
    if (httpConfig.getProxy() != null) {
      contextWithLogging.getBaseLogger().info("Using proxy: {} {} authentication.",
          httpConfig.getProxy(),
          httpConfig.getProxyAuthentication() == null ? "without" : "with");
    }
    
    ClientContext contextWithHttpAndLogging = new ClientContext(sdkKey, config.applicationInfo, httpConfig,
        loggingConfig, config.offline, config.serviceEndpoints, config.threadPriority);
    
    // Create a diagnostic store only if diagnostics are enabled. Diagnostics are enabled as long as 1. the
    // opt-out property was not set in the config, and 2. we are using the standard event processor.
    DiagnosticStore diagnosticStore = null;
    if (!config.diagnosticOptOut && config.eventProcessorFactory instanceof EventProcessorBuilder) {
      diagnosticStore = new DiagnosticStore(
          ServerSideDiagnosticEvents.getSdkDiagnosticParams(contextWithHttpAndLogging, config));
    }
    
    return new ClientContextImpl(
        contextWithHttpAndLogging,
        sharedExecutor,
        diagnosticStore
        );
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
    return new ClientContextImpl(context, fallbackSharedExecutor, null);
  }
}
