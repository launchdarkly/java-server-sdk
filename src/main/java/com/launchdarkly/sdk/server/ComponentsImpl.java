package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.logging.LDLogLevel;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LDSLF4J;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.internal.events.DefaultEventSender;
import com.launchdarkly.sdk.internal.events.DiagnosticConfigProperty;
import com.launchdarkly.sdk.internal.events.EventSender;
import com.launchdarkly.sdk.internal.events.EventsConfiguration;
import com.launchdarkly.sdk.internal.http.HttpProperties;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder;
import com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;
import com.launchdarkly.sdk.server.interfaces.ServiceEndpoints;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DiagnosticDescription;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import okhttp3.Credentials;

/**
 * This class contains the package-private implementations of component factories and builders whose
 * public factory methods are in {@link Components}.
 */
abstract class ComponentsImpl {
  private ComponentsImpl() {}

  static final class InMemoryDataStoreFactory implements ComponentConfigurer<DataStore>, DiagnosticDescription {
    static final InMemoryDataStoreFactory INSTANCE = new InMemoryDataStoreFactory();
    @Override
    public DataStore build(ClientContext context) {
      return new InMemoryDataStore();
    }

    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return LDValue.of("memory");
    }
  }
  
  static final ComponentConfigurer<EventProcessor> NULL_EVENT_PROCESSOR_FACTORY = context -> NullEventProcessor.INSTANCE;
  
  /**
   * Stub implementation of {@link EventProcessor} for when we don't want to send any events.
   */
  static final class NullEventProcessor implements EventProcessor {
    static final NullEventProcessor INSTANCE = new NullEventProcessor();
    
    private NullEventProcessor() {}
    
    @Override
    public void flush() {}
    
    @Override
    public void close() {}

    @Override
    public void recordEvaluationEvent(LDContext context, String flagKey, int flagVersion, int variation, LDValue value,
        EvaluationReason reason, LDValue defaultValue, String prerequisiteOfFlagKey, boolean requireFullEvent,
        Long debugEventsUntilDate) {}

    @Override
    public void recordIdentifyEvent(LDContext context) {}

    @Override
    public void recordCustomEvent(LDContext context, String eventKey, LDValue data, Double metricValue) {}
  }
  
  static final class NullDataSourceFactory implements ComponentConfigurer<DataSource>, DiagnosticDescription {
    static final NullDataSourceFactory INSTANCE = new NullDataSourceFactory();
    
    @Override
    public DataSource build(ClientContext context) {
      LDLogger logger = context.getBaseLogger();
      if (context.isOffline()) {
        // If they have explicitly called offline(true) to disable everything, we'll log this slightly
        // more specific message.
        logger.info("Starting LaunchDarkly client in offline mode");
      } else {
        logger.info("LaunchDarkly client will not connect to Launchdarkly for feature flag data");
      }
      context.getDataSourceUpdateSink().updateStatus(DataSourceStatusProvider.State.VALID, null);
      return NullDataSource.INSTANCE;
    }

    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      // The difference between "offline" and "using the Relay daemon" is irrelevant from the data source's
      // point of view, but we describe them differently in diagnostic events. This is easy because if we were
      // configured to be completely offline... we wouldn't be sending any diagnostic events. Therefore, if
      // Components.externalUpdatesOnly() was specified as the data source and we are sending a diagnostic
      // event, we can assume usingRelayDaemon should be true.
      return LDValue.buildObject()
          .put(DiagnosticConfigProperty.CUSTOM_BASE_URI.name, false)
          .put(DiagnosticConfigProperty.CUSTOM_STREAM_URI.name, false)
          .put(DiagnosticConfigProperty.STREAMING_DISABLED.name, false)
          .put(DiagnosticConfigProperty.USING_RELAY_DAEMON.name, true)
          .build();
    }
  }
  
  // Package-private for visibility in tests
  static final class NullDataSource implements DataSource {
    static final DataSource INSTANCE = new NullDataSource();
    @Override
    public Future<Void> start() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isInitialized() {
      return true;
    }
    
    @Override
    public void close() throws IOException {}
  }
  
  static final class StreamingDataSourceBuilderImpl extends StreamingDataSourceBuilder
      implements DiagnosticDescription {
    @Override
    public DataSource build(ClientContext context) {
      LDLogger baseLogger = context.getBaseLogger();
      LDLogger logger = baseLogger.subLogger(Loggers.DATA_SOURCE_LOGGER_NAME);
      logger.info("Enabling streaming API");

      URI streamUri = StandardEndpoints.selectBaseUri(
          context.getServiceEndpoints().getStreamingBaseUri(),
          StandardEndpoints.DEFAULT_STREAMING_BASE_URI,
          "streaming",
          baseLogger
          );
      
      return new StreamProcessor(
          toHttpProperties(context.getHttp()),
          context.getDataSourceUpdateSink(),
          context.getThreadPriority(),
          ClientContextImpl.get(context).diagnosticStore,
          streamUri,
          initialReconnectDelay,
          logger
          );
    }

    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return LDValue.buildObject()
          .put(DiagnosticConfigProperty.STREAMING_DISABLED.name, false)
          .put(DiagnosticConfigProperty.CUSTOM_BASE_URI.name, false)
          .put(DiagnosticConfigProperty.CUSTOM_STREAM_URI.name,
              StandardEndpoints.isCustomBaseUri(
                  clientContext.getServiceEndpoints().getStreamingBaseUri(),
                  StandardEndpoints.DEFAULT_STREAMING_BASE_URI))
          .put(DiagnosticConfigProperty.RECONNECT_TIME_MILLIS.name, initialReconnectDelay.toMillis())
          .put(DiagnosticConfigProperty.USING_RELAY_DAEMON.name, false)
          .build();
    }
  }
  
  static final class PollingDataSourceBuilderImpl extends PollingDataSourceBuilder implements DiagnosticDescription {
    // for testing only
    PollingDataSourceBuilderImpl pollIntervalWithNoMinimum(Duration pollInterval) {
      this.pollInterval = pollInterval;
      return this;
    }
    
    @Override
    public DataSource build(ClientContext context) {
      LDLogger baseLogger = context.getBaseLogger();
      LDLogger logger = baseLogger.subLogger(Loggers.DATA_SOURCE_LOGGER_NAME);
      
      logger.info("Disabling streaming API");
      logger.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");
      
      URI pollUri = StandardEndpoints.selectBaseUri(
          context.getServiceEndpoints().getPollingBaseUri(),
          StandardEndpoints.DEFAULT_POLLING_BASE_URI,
          "polling",
          baseLogger
          );

      DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(toHttpProperties(context.getHttp()), pollUri, logger);
      return new PollingProcessor(
          requestor,
          context.getDataSourceUpdateSink(),
          ClientContextImpl.get(context).sharedExecutor,
          pollInterval,
          logger
          );
    }

    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return LDValue.buildObject()
          .put(DiagnosticConfigProperty.STREAMING_DISABLED.name, true)
          .put(DiagnosticConfigProperty.CUSTOM_BASE_URI.name,
              StandardEndpoints.isCustomBaseUri(
                  clientContext.getServiceEndpoints().getPollingBaseUri(),
                  StandardEndpoints.DEFAULT_POLLING_BASE_URI))
          .put(DiagnosticConfigProperty.CUSTOM_STREAM_URI.name, false)
          .put(DiagnosticConfigProperty.POLLING_INTERVAL_MILLIS.name, pollInterval.toMillis())
          .put(DiagnosticConfigProperty.USING_RELAY_DAEMON.name, false)
          .build();
    }
  }
  
  static final class EventProcessorBuilderImpl extends EventProcessorBuilder
      implements DiagnosticDescription {
    @Override
    public EventProcessor build(ClientContext context) {
      EventSender eventSender;
      if (eventSenderConfigurer == null) {
        eventSender = new DefaultEventSender(toHttpProperties(context.getHttp()), 0, // 0 means default retry delay
            context.getBaseLogger().subLogger(Loggers.EVENTS_LOGGER_NAME));
      } else {
        eventSender = new EventSenderWrapper(eventSenderConfigurer.build(context));
      }
      URI eventsUri = StandardEndpoints.selectBaseUri(
          context.getServiceEndpoints().getEventsBaseUri(),
          StandardEndpoints.DEFAULT_EVENTS_BASE_URI,
          "events",
          context.getBaseLogger()
          );
      EventsConfiguration eventsConfig = new EventsConfiguration(
          allAttributesPrivate,
          capacity,
          new ServerSideEventContextDeduplicator(userKeysCapacity, userKeysFlushInterval),
          diagnosticRecordingInterval.toMillis(),
          ClientContextImpl.get(context).diagnosticStore,
          eventSender,
          eventsUri,
          flushInterval.toMillis(),
          privateAttributes
          );
      return new DefaultEventProcessorWrapper(context, eventsConfig);
    }
    
    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return LDValue.buildObject()
          .put(DiagnosticConfigProperty.ALL_ATTRIBUTES_PRIVATE.name, allAttributesPrivate)
          .put(DiagnosticConfigProperty.CUSTOM_EVENTS_URI.name,
              StandardEndpoints.isCustomBaseUri(
                  clientContext.getServiceEndpoints().getEventsBaseUri(),
                  StandardEndpoints.DEFAULT_EVENTS_BASE_URI))
          .put(DiagnosticConfigProperty.DIAGNOSTIC_RECORDING_INTERVAL_MILLIS.name, diagnosticRecordingInterval.toMillis())
          .put(DiagnosticConfigProperty.EVENTS_CAPACITY.name, capacity)
          .put(DiagnosticConfigProperty.EVENTS_FLUSH_INTERVAL_MILLIS.name, flushInterval.toMillis())
          .put(DiagnosticConfigProperty.SAMPLING_INTERVAL.name, 0)
          .put(DiagnosticConfigProperty.USER_KEYS_CAPACITY.name, userKeysCapacity)
          .put(DiagnosticConfigProperty.USER_KEYS_FLUSH_INTERVAL_MILLIS.name, userKeysFlushInterval.toMillis())
          .build();
    }
    
    static final class EventSenderWrapper implements EventSender {
      private final com.launchdarkly.sdk.server.subsystems.EventSender wrappedSender;
      
      EventSenderWrapper(com.launchdarkly.sdk.server.subsystems.EventSender wrappedSender) {
        this.wrappedSender = wrappedSender;
      }

      @Override
      public void close() throws IOException {
        wrappedSender.close();
      }

      @Override
      public Result sendAnalyticsEvents(byte[] data, int eventCount, URI eventsBaseUri) {
        return transformResult(wrappedSender.sendAnalyticsEvents(data, eventCount, eventsBaseUri));
      }

      @Override
      public Result sendDiagnosticEvent(byte[] data, URI eventsBaseUri) {
        return transformResult(wrappedSender.sendDiagnosticEvent(data, eventsBaseUri));
      }
      
      private Result transformResult(com.launchdarkly.sdk.server.subsystems.EventSender.Result result) {
        switch (result) {
        case FAILURE:
          return new Result(false, false, null);
        case STOP:
          return new Result(false, true, null);
        default:
          return new Result(true, false, null);
        }
      }
    }
  }

  static final class HttpConfigurationBuilderImpl extends HttpConfigurationBuilder {
    @Override
    public HttpConfiguration build(ClientContext clientContext) {
      LDLogger logger = clientContext.getBaseLogger();
      // Build the default headers
      ImmutableMap.Builder<String, String> headers = ImmutableMap.builder();
      headers.put("Authorization", clientContext.getSdkKey());
      headers.put("User-Agent", "JavaClient/" + Version.SDK_VERSION);
      if (clientContext.getApplicationInfo() != null) {
        String tagHeader = Util.applicationTagHeader(clientContext.getApplicationInfo(), logger);
        if (!tagHeader.isEmpty()) {
          headers.put("X-LaunchDarkly-Tags", tagHeader);
        }
      }
      if (wrapperName != null) {
        String wrapperId = wrapperVersion == null ? wrapperName : (wrapperName + "/" + wrapperVersion);
        headers.put("X-LaunchDarkly-Wrapper", wrapperId);        
      }
      
      Proxy proxy = proxyHost == null ? null : new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
      
      return new HttpConfiguration(
          connectTimeout,
          headers.build(),
          proxy,
          proxyAuth,
          socketFactory,
          socketTimeout,
          sslSocketFactory,
          trustManager
      );
    }
  }
  
  static final class HttpBasicAuthentication implements HttpAuthentication {
    private final String username;
    private final String password;
    
    HttpBasicAuthentication(String username, String password) {
      this.username = username;
      this.password = password;
    }

    @Override
    public String provideAuthorization(Iterable<Challenge> challenges) {
      return Credentials.basic(username, password);
    }
  }
  
  static final class PersistentDataStoreBuilderImpl extends PersistentDataStoreBuilder implements DiagnosticDescription {
    public PersistentDataStoreBuilderImpl(ComponentConfigurer<PersistentDataStore> storeConfigurer) {
      super(storeConfigurer);
    }

    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      if (persistentDataStoreConfigurer instanceof DiagnosticDescription) {
        return ((DiagnosticDescription)persistentDataStoreConfigurer).describeConfiguration(clientContext);
      }
      return LDValue.of("custom");
    }
    
    @Override
    public DataStore build(ClientContext context) {
      PersistentDataStore core = persistentDataStoreConfigurer.build(context);
      return new PersistentDataStoreWrapper(
          core,
          cacheTime,
          staleValuesPolicy,
          recordCacheStats,
          context.getDataStoreUpdateSink(),
          ClientContextImpl.get(context).sharedExecutor,
          context.getBaseLogger().subLogger(Loggers.DATA_STORE_LOGGER_NAME)
          );
    }
  }
  
  static final class LoggingConfigurationBuilderImpl extends LoggingConfigurationBuilder {
    @Override
    public LoggingConfiguration build(ClientContext clientContext) {
      LDLogAdapter adapter = logAdapter == null ? LDSLF4J.adapter() : logAdapter;
      LDLogAdapter filteredAdapter = Logs.level(adapter,
          minimumLevel == null ? LDLogLevel.INFO : minimumLevel);
      // If the adapter is for a framework like SLF4J or java.util.logging that has its own external
      // configuration system, then calling Logs.level here has no effect and filteredAdapter will be
      // just the same as adapter.
      String name = baseName == null ? Loggers.BASE_LOGGER_NAME : baseName;
      return new LoggingConfiguration(name, filteredAdapter, logDataSourceOutageAsErrorAfter);
    }
  }

  static final class ServiceEndpointsBuilderImpl extends ServiceEndpointsBuilder {
    @Override
    public ServiceEndpoints createServiceEndpoints() {
      // If *any* custom URIs have been set, then we do not want to use default values for any that were not set,
      // so we will leave those null. That way, if we decide later on (in other component factories, such as
      // EventProcessorBuilder) that we are actually interested in one of these values, and we
      // see that it is null, we can assume that there was a configuration mistake and log an
      // error.
      if (streamingBaseUri == null && pollingBaseUri == null && eventsBaseUri == null) {
        return new ServiceEndpoints(
          StandardEndpoints.DEFAULT_STREAMING_BASE_URI,
          StandardEndpoints.DEFAULT_POLLING_BASE_URI,
          StandardEndpoints.DEFAULT_EVENTS_BASE_URI
        );
      }
      return new ServiceEndpoints(streamingBaseUri, pollingBaseUri, eventsBaseUri);
    }
  }
  
  static HttpProperties toHttpProperties(HttpConfiguration httpConfig) {
    okhttp3.Authenticator proxyAuth = null;
    if (httpConfig.getProxyAuthentication() != null) {
      proxyAuth = Util.okhttpAuthenticatorFromHttpAuthStrategy(httpConfig.getProxyAuthentication());
    }
    return new HttpProperties(
        httpConfig.getConnectTimeout().toMillis(),
        ImmutableMap.copyOf(httpConfig.getDefaultHeaders()),
        httpConfig.getProxy(),
        proxyAuth,
        httpConfig.getSocketFactory(),
        httpConfig.getSocketTimeout().toMillis(),
        httpConfig.getSslSocketFactory(),
        httpConfig.getTrustManager()
        );
  }
}
