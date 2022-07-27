package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DiagnosticEvent.ConfigProperty;
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
import com.launchdarkly.sdk.server.subsystems.Event;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;
import com.launchdarkly.sdk.server.subsystems.EventSender;
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
    public void sendEvent(Event e) {
    }
    
    @Override
    public void flush() {
    }
    
    @Override
    public void close() {
    }
  }
  
  static final class NullDataSourceFactory implements ComponentConfigurer<DataSource>, DiagnosticDescription {
    static final NullDataSourceFactory INSTANCE = new NullDataSourceFactory();
    
    @Override
    public DataSource build(ClientContext context) {
      if (context.isOffline()) {
        // If they have explicitly called offline(true) to disable everything, we'll log this slightly
        // more specific message.
        Loggers.MAIN.info("Starting LaunchDarkly client in offline mode");
      } else {
        Loggers.MAIN.info("LaunchDarkly client will not connect to Launchdarkly for feature flag data");
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
          .put(ConfigProperty.CUSTOM_BASE_URI.name, false)
          .put(ConfigProperty.CUSTOM_STREAM_URI.name, false)
          .put(ConfigProperty.STREAMING_DISABLED.name, false)
          .put(ConfigProperty.USING_RELAY_DAEMON.name, true)
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
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      Loggers.DATA_SOURCE.info("Enabling streaming API");

      URI streamUri = StandardEndpoints.selectBaseUri(
          context.getServiceEndpoints().getStreamingBaseUri(),
          StandardEndpoints.DEFAULT_STREAMING_BASE_URI,
          "streaming",
          Loggers.MAIN
          );
      
      return new StreamProcessor(
          context.getHttp(),
          context.getDataSourceUpdateSink(),
          context.getThreadPriority(),
          ClientContextImpl.get(context).diagnosticAccumulator,
          streamUri,
          initialReconnectDelay
          );
    }

    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return LDValue.buildObject()
          .put(ConfigProperty.STREAMING_DISABLED.name, false)
          .put(ConfigProperty.CUSTOM_BASE_URI.name, false)
          .put(ConfigProperty.CUSTOM_STREAM_URI.name,
              StandardEndpoints.isCustomBaseUri(
                  clientContext.getServiceEndpoints().getStreamingBaseUri(),
                  StandardEndpoints.DEFAULT_STREAMING_BASE_URI))
          .put(ConfigProperty.RECONNECT_TIME_MILLIS.name, initialReconnectDelay.toMillis())
          .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
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
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      Loggers.DATA_SOURCE.info("Disabling streaming API");
      Loggers.DATA_SOURCE.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");

      URI pollUri = StandardEndpoints.selectBaseUri(
          context.getServiceEndpoints().getPollingBaseUri(),
          StandardEndpoints.DEFAULT_POLLING_BASE_URI,
          "polling",
          Loggers.MAIN
          );

      DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(context.getHttp(), pollUri);
      return new PollingProcessor(
          requestor,
          context.getDataSourceUpdateSink(),
          ClientContextImpl.get(context).sharedExecutor,
          pollInterval
          );
    }

    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return LDValue.buildObject()
          .put(ConfigProperty.STREAMING_DISABLED.name, true)
          .put(ConfigProperty.CUSTOM_BASE_URI.name,
              StandardEndpoints.isCustomBaseUri(
                  clientContext.getServiceEndpoints().getPollingBaseUri(),
                  StandardEndpoints.DEFAULT_POLLING_BASE_URI))
          .put(ConfigProperty.CUSTOM_STREAM_URI.name, false)
          .put(ConfigProperty.POLLING_INTERVAL_MILLIS.name, pollInterval.toMillis())
          .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
          .build();
    }
  }
  
  static final class EventProcessorBuilderImpl extends EventProcessorBuilder
      implements DiagnosticDescription {
    @Override
    public EventProcessor build(ClientContext context) {
      EventSender eventSender =
          (eventSenderConfigurer == null ? new DefaultEventSender.Factory() : eventSenderConfigurer)
          .build(context);
      URI eventsUri = StandardEndpoints.selectBaseUri(
          context.getServiceEndpoints().getEventsBaseUri(),
          StandardEndpoints.DEFAULT_EVENTS_BASE_URI,
          "events",
          Loggers.MAIN
          );
      return new DefaultEventProcessor(
          new EventsConfiguration(
              allAttributesPrivate,
              capacity,
              eventSender,
              eventsUri,
              flushInterval,
              privateAttributes,
              userKeysCapacity,
              userKeysFlushInterval,
              diagnosticRecordingInterval
              ),
          ClientContextImpl.get(context).sharedExecutor,
          context.getThreadPriority(),
          ClientContextImpl.get(context).diagnosticAccumulator,
          ClientContextImpl.get(context).diagnosticInitEvent
          );
    }
    
    @Override
    public LDValue describeConfiguration(ClientContext clientContext) {
      return LDValue.buildObject()
          .put(ConfigProperty.ALL_ATTRIBUTES_PRIVATE.name, allAttributesPrivate)
          .put(ConfigProperty.CUSTOM_EVENTS_URI.name,
              StandardEndpoints.isCustomBaseUri(
                  clientContext.getServiceEndpoints().getEventsBaseUri(),
                  StandardEndpoints.DEFAULT_EVENTS_BASE_URI))
          .put(ConfigProperty.DIAGNOSTIC_RECORDING_INTERVAL_MILLIS.name, diagnosticRecordingInterval.toMillis())
          .put(ConfigProperty.EVENTS_CAPACITY.name, capacity)
          .put(ConfigProperty.EVENTS_FLUSH_INTERVAL_MILLIS.name, flushInterval.toMillis())
          .put(ConfigProperty.SAMPLING_INTERVAL.name, 0)
          .put(ConfigProperty.USER_KEYS_CAPACITY.name, userKeysCapacity)
          .put(ConfigProperty.USER_KEYS_FLUSH_INTERVAL_MILLIS.name, userKeysFlushInterval.toMillis())
          .build();
    }
  }

  static final class HttpConfigurationBuilderImpl extends HttpConfigurationBuilder {
    @Override
    public HttpConfiguration build(ClientContext clientContext) {
      // Build the default headers
      ImmutableMap.Builder<String, String> headers = ImmutableMap.builder();
      headers.put("Authorization", clientContext.getSdkKey());
      headers.put("User-Agent", "JavaClient/" + Version.SDK_VERSION);
      if (clientContext.getApplicationInfo() != null) {
        String tagHeader = Util.applicationTagHeader(clientContext.getApplicationInfo());
        if (!tagHeader.isEmpty()) {
          headers.put("X-LaunchDarkly-Tags", tagHeader);
        }
      }
      if (wrapperName != null) {
        String wrapperId = wrapperVersion == null ? wrapperName : (wrapperName + "/" + wrapperVersion);
        headers.put("X-LaunchDarkly-Wrapper", wrapperId);        
      }
      
      Proxy proxy = proxyHost == null ? null : new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
      if (proxy != null) {
        Loggers.MAIN.info("Using proxy: {} {} authentication.", proxy, proxyAuth == null ? "without" : "with");
      }
      
      return new HttpConfiguration(
          connectTimeout,
          headers.build(),
          proxy,
          proxyAuth,
          socketTimeout,
          socketFactory,
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
          ClientContextImpl.get(context).sharedExecutor
          );
    }
  }
  
  static final class LoggingConfigurationBuilderImpl extends LoggingConfigurationBuilder {
    @Override
    public LoggingConfiguration build(ClientContext clientContext) {
      return new LoggingConfiguration(logDataSourceOutageAsErrorAfter);
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
}
