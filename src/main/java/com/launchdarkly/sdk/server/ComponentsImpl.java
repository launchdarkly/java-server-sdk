package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DiagnosticEvent.ConfigProperty;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.integrations.HttpConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.BasicConfiguration;
import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.DataSource;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceUpdates;
import com.launchdarkly.sdk.server.interfaces.DataStore;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreUpdates;
import com.launchdarkly.sdk.server.interfaces.DiagnosticDescription;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.EventSender;
import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;
import com.launchdarkly.sdk.server.interfaces.LoggingConfiguration;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStore;
import com.launchdarkly.sdk.server.interfaces.PersistentDataStoreFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import okhttp3.Credentials;

/**
 * This class contains the package-private implementations of component factories and builders whose
 * public factory methods are in {@link Components}.
 */
abstract class ComponentsImpl {
  private ComponentsImpl() {}

  static final class InMemoryDataStoreFactory implements DataStoreFactory, DiagnosticDescription {
    static final DataStoreFactory INSTANCE = new InMemoryDataStoreFactory();
    @Override
    public DataStore createDataStore(ClientContext context, DataStoreUpdates dataStoreUpdates) {
      return new InMemoryDataStore();
    }

    @Override
    public LDValue describeConfiguration(BasicConfiguration basicConfiguration) {
      return LDValue.of("memory");
    }
  }
  
  static final EventProcessorFactory NULL_EVENT_PROCESSOR_FACTORY = context -> NullEventProcessor.INSTANCE;
  
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
  
  static final class NullDataSourceFactory implements DataSourceFactory, DiagnosticDescription {
    static final NullDataSourceFactory INSTANCE = new NullDataSourceFactory();
    
    @Override
    public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
      if (context.getBasic().isOffline()) {
        // If they have explicitly called offline(true) to disable everything, we'll log this slightly
        // more specific message.
        LDClient.logger.info("Starting LaunchDarkly client in offline mode");
      } else {
        LDClient.logger.info("LaunchDarkly client will not connect to Launchdarkly for feature flag data");
      }
      dataSourceUpdates.updateStatus(DataSourceStatusProvider.State.VALID, null);
      return NullDataSource.INSTANCE;
    }

    @Override
    public LDValue describeConfiguration(BasicConfiguration basicConfiguration) {
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
    public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      if (context.getBasic().isOffline()) {
        return Components.externalUpdatesOnly().createDataSource(context, dataSourceUpdates);
      }
      
      LDClient.logger.info("Enabling streaming API");

      URI streamUri = baseURI == null ? LDConfig.DEFAULT_STREAM_URI : baseURI;
      URI pollUri;
      if (pollingBaseURI != null) {
        pollUri = pollingBaseURI;
      } else {
        // If they have set a custom base URI, and they did *not* set a custom polling URI, then we can
        // assume they're using Relay in which case both of those values are the same.
        pollUri = baseURI == null ? LDConfig.DEFAULT_BASE_URI : baseURI;
      }
      
      DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(
          context.getHttp(),
          pollUri,
          false
          );
      
      return new StreamProcessor(
          context.getHttp(),
          requestor,
          dataSourceUpdates,
          null,
          context.getBasic().getThreadPriority(),
          ClientContextImpl.get(context).diagnosticAccumulator,
          streamUri,
          initialReconnectDelay
          );
    }

    @Override
    public LDValue describeConfiguration(BasicConfiguration basicConfiguration) {
      return LDValue.buildObject()
          .put(ConfigProperty.STREAMING_DISABLED.name, false)
          .put(ConfigProperty.CUSTOM_BASE_URI.name,
              (pollingBaseURI != null && !pollingBaseURI.equals(LDConfig.DEFAULT_BASE_URI)) ||
              (pollingBaseURI == null && baseURI != null && !baseURI.equals(LDConfig.DEFAULT_STREAM_URI)))
          .put(ConfigProperty.CUSTOM_STREAM_URI.name,
              baseURI != null && !baseURI.equals(LDConfig.DEFAULT_STREAM_URI))
          .put(ConfigProperty.RECONNECT_TIME_MILLIS.name, initialReconnectDelay.toMillis())
          .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
          .build();
    }
  }
  
  static final class PollingDataSourceBuilderImpl extends PollingDataSourceBuilder implements DiagnosticDescription {
    @Override
    public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
      // Note, we log startup messages under the LDClient class to keep logs more readable
      
      if (context.getBasic().isOffline()) {
        return Components.externalUpdatesOnly().createDataSource(context, dataSourceUpdates);
      }

      LDClient.logger.info("Disabling streaming API");
      LDClient.logger.warn("You should only disable the streaming API if instructed to do so by LaunchDarkly support");
      
      DefaultFeatureRequestor requestor = new DefaultFeatureRequestor(
          context.getHttp(),
          baseURI == null ? LDConfig.DEFAULT_BASE_URI : baseURI,
          true
          );
      return new PollingProcessor(
          requestor,
          dataSourceUpdates,
          ClientContextImpl.get(context).sharedExecutor,
          pollInterval
          );
    }

    @Override
    public LDValue describeConfiguration(BasicConfiguration basicConfiguration) {
      return LDValue.buildObject()
          .put(ConfigProperty.STREAMING_DISABLED.name, true)
          .put(ConfigProperty.CUSTOM_BASE_URI.name,
              baseURI != null && !baseURI.equals(LDConfig.DEFAULT_BASE_URI))
          .put(ConfigProperty.CUSTOM_STREAM_URI.name, false)
          .put(ConfigProperty.POLLING_INTERVAL_MILLIS.name, pollInterval.toMillis())
          .put(ConfigProperty.USING_RELAY_DAEMON.name, false)
          .build();
    }
  }
  
  static final class EventProcessorBuilderImpl extends EventProcessorBuilder
      implements DiagnosticDescription {
    @Override
    public EventProcessor createEventProcessor(ClientContext context) {
      if (context.getBasic().isOffline()) {
        return new NullEventProcessor();
      }
      EventSender eventSender =
          (eventSenderFactory == null ? new DefaultEventSender.Factory() : eventSenderFactory)
          .createEventSender(context.getBasic(), context.getHttp());
      return new DefaultEventProcessor(
          new EventsConfiguration(
              allAttributesPrivate,
              capacity,
              eventSender,
              baseURI == null ? LDConfig.DEFAULT_EVENTS_URI : baseURI,
              flushInterval,
              inlineUsersInEvents,
              privateAttributes,
              userKeysCapacity,
              userKeysFlushInterval,
              diagnosticRecordingInterval
              ),
          ClientContextImpl.get(context).sharedExecutor,
          context.getBasic().getThreadPriority(),
          ClientContextImpl.get(context).diagnosticAccumulator,
          ClientContextImpl.get(context).diagnosticInitEvent
          );
    }
    
    @Override
    public LDValue describeConfiguration(BasicConfiguration basicConfiguration) {
      return LDValue.buildObject()
          .put(ConfigProperty.ALL_ATTRIBUTES_PRIVATE.name, allAttributesPrivate)
          .put(ConfigProperty.CUSTOM_EVENTS_URI.name, baseURI != null && !baseURI.equals(LDConfig.DEFAULT_EVENTS_URI))
          .put(ConfigProperty.DIAGNOSTIC_RECORDING_INTERVAL_MILLIS.name, diagnosticRecordingInterval.toMillis())
          .put(ConfigProperty.EVENTS_CAPACITY.name, capacity)
          .put(ConfigProperty.EVENTS_FLUSH_INTERVAL_MILLIS.name, flushInterval.toMillis())
          .put(ConfigProperty.INLINE_USERS_IN_EVENTS.name, inlineUsersInEvents)
          .put(ConfigProperty.SAMPLING_INTERVAL.name, 0)
          .put(ConfigProperty.USER_KEYS_CAPACITY.name, userKeysCapacity)
          .put(ConfigProperty.USER_KEYS_FLUSH_INTERVAL_MILLIS.name, userKeysFlushInterval.toMillis())
          .build();
    }
  }

  static final class HttpConfigurationBuilderImpl extends HttpConfigurationBuilder {
    @Override
    public HttpConfiguration createHttpConfiguration(BasicConfiguration basicConfiguration) {
      // Build the default headers
      ImmutableMap.Builder<String, String> headers = ImmutableMap.builder();
      headers.put("Authorization", basicConfiguration.getSdkKey());
      headers.put("User-Agent", "JavaClient/" + Version.SDK_VERSION);
      if (wrapperName != null) {
        String wrapperId = wrapperVersion == null ? wrapperName : (wrapperName + "/" + wrapperVersion);
        headers.put("X-LaunchDarkly-Wrapper", wrapperId);        
      }
      
      return new HttpConfigurationImpl(
          connectTimeout,
          proxyHost == null ? null : new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)),
          proxyAuth,
          socketTimeout,
          sslSocketFactory,
          trustManager,
          headers.build()
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
    public PersistentDataStoreBuilderImpl(PersistentDataStoreFactory persistentDataStoreFactory) {
      super(persistentDataStoreFactory);
    }

    @Override
    public LDValue describeConfiguration(BasicConfiguration basicConfiguration) {
      if (persistentDataStoreFactory instanceof DiagnosticDescription) {
        return ((DiagnosticDescription)persistentDataStoreFactory).describeConfiguration(basicConfiguration);
      }
      return LDValue.of("custom");
    }
    
    /**
     * Called by the SDK to create the data store instance.
     */
    @Override
    public DataStore createDataStore(ClientContext context, DataStoreUpdates dataStoreUpdates) {
      PersistentDataStore core = persistentDataStoreFactory.createPersistentDataStore(context);
      return new PersistentDataStoreWrapper(
          core,
          cacheTime,
          staleValuesPolicy,
          recordCacheStats,
          dataStoreUpdates,
          ClientContextImpl.get(context).sharedExecutor
          );
    }
  }
  
  static final class LoggingConfigurationBuilderImpl extends LoggingConfigurationBuilder {
    @Override
    public LoggingConfiguration createLoggingConfiguration(BasicConfiguration basicConfiguration) {
      return new LoggingConfigurationImpl(logDataSourceOutageAsErrorAfter);
    }
  }
}
