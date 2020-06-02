package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataStoreFactory;
import com.launchdarkly.sdk.server.interfaces.EventProcessor;
import com.launchdarkly.sdk.server.interfaces.EventProcessorFactory;
import com.launchdarkly.sdk.server.interfaces.HttpConfigurationFactory;
import com.launchdarkly.sdk.server.interfaces.LoggingConfigurationFactory;

import java.net.URI;
import java.time.Duration;

/**
 * This class exposes advanced configuration options for the {@link LDClient}. Instances of this class must be constructed with a {@link com.launchdarkly.sdk.server.LDConfig.Builder}.
 */
public final class LDConfig {
  static final URI DEFAULT_BASE_URI = URI.create("https://app.launchdarkly.com");
  static final URI DEFAULT_EVENTS_URI = URI.create("https://events.launchdarkly.com");
  static final URI DEFAULT_STREAM_URI = URI.create("https://stream.launchdarkly.com");

  static final Duration DEFAULT_START_WAIT = Duration.ofSeconds(5);
  
  protected static final LDConfig DEFAULT = new Builder().build();

  final DataSourceFactory dataSourceFactory;
  final DataStoreFactory dataStoreFactory;
  final boolean diagnosticOptOut;
  final EventProcessorFactory eventProcessorFactory;
  final HttpConfigurationFactory httpConfigFactory;
  final LoggingConfigurationFactory loggingConfigFactory;
  final boolean offline;
  final Duration startWait;
  final int threadPriority;

  protected LDConfig(Builder builder) {
    this.dataStoreFactory = builder.dataStoreFactory == null ? Components.inMemoryDataStore() :
      builder.dataStoreFactory;
    this.eventProcessorFactory = builder.eventProcessorFactory == null ? Components.sendEvents() :
      builder.eventProcessorFactory;
    this.dataSourceFactory = builder.dataSourceFactory == null ? Components.streamingDataSource() :
      builder.dataSourceFactory;
    this.diagnosticOptOut = builder.diagnosticOptOut;
    this.httpConfigFactory = builder.httpConfigFactory == null ? Components.httpConfiguration() :
      builder.httpConfigFactory;
    this.loggingConfigFactory = builder.loggingConfigFactory == null ? Components.logging() :
      builder.loggingConfigFactory;
    this.offline = builder.offline;
    this.startWait = builder.startWait;
    this.threadPriority = builder.threadPriority;
  }
  
  /**
   * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> that helps construct
   * {@link com.launchdarkly.sdk.server.LDConfig} objects. Builder calls can be chained, enabling the
   * following pattern:
   * <pre>
   * LDConfig config = new LDConfig.Builder()
   *      .connectTimeoutMillis(3)
   *      .socketTimeoutMillis(3)
   *      .build()
   * </pre>
   */
  public static class Builder {
    private DataSourceFactory dataSourceFactory = null;
    private DataStoreFactory dataStoreFactory = null;
    private boolean diagnosticOptOut = false;
    private EventProcessorFactory eventProcessorFactory = null;
    private HttpConfigurationFactory httpConfigFactory = null;
    private LoggingConfigurationFactory loggingConfigFactory = null;
    private boolean offline = false;
    private Duration startWait = DEFAULT_START_WAIT;
    private int threadPriority = Thread.MIN_PRIORITY;

    /**
     * Creates a builder with all configuration parameters set to the default
     */
    public Builder() {
    }
    
    /**
     * Sets the implementation of the component that receives feature flag data from LaunchDarkly,
     * using a factory object. Depending on the implementation, the factory may be a builder that
     * allows you to set other configuration options as well.
     * <p>
     * The default is {@link Components#streamingDataSource()}. You may instead use
     * {@link Components#pollingDataSource()}, or a test fixture such as
     * {@link com.launchdarkly.sdk.server.integrations.FileData#dataSource()}. See those methods
     * for details on how to configure them.
     * 
     * @param factory the factory object
     * @return the builder
     * @since 4.12.0
     */
    public Builder dataSource(DataSourceFactory factory) {
      this.dataSourceFactory = factory;
      return this;
    }

    /**
     * Sets the implementation of the data store to be used for holding feature flags and
     * related data received from LaunchDarkly, using a factory object. The default is
     * {@link Components#inMemoryDataStore()}; for database integrations, use
     * {@link Components#persistentDataStore(com.launchdarkly.sdk.server.interfaces.PersistentDataStoreFactory)}.
     * 
     * @param factory the factory object
     * @return the builder
     * @since 4.12.0
     */
    public Builder dataStore(DataStoreFactory factory) {
      this.dataStoreFactory = factory;
      return this;
    }

    /**
     * Set to true to opt out of sending diagnostics data.
     * <p>
     * Unless {@code diagnosticOptOut} is set to true, the client will send some diagnostics data to the
     * LaunchDarkly servers in order to assist in the development of future SDK improvements. These diagnostics
     * consist of an initial payload containing some details of SDK in use, the SDK's configuration, and the platform
     * the SDK is being run on; as well as payloads sent periodically with information on irregular occurrences such
     * as dropped events.
     *
     * @see com.launchdarkly.sdk.server.integrations.EventProcessorBuilder#diagnosticRecordingInterval(Duration)
     *
     * @param diagnosticOptOut true if you want to opt out of sending any diagnostics data
     * @return the builder
     * @since 4.12.0
     */
    public Builder diagnosticOptOut(boolean diagnosticOptOut) {
      this.diagnosticOptOut = diagnosticOptOut;
      return this;
    }

    /**
     * Sets the implementation of {@link EventProcessor} to be used for processing analytics events.
     * <p>
     * The default is {@link Components#sendEvents()}, but you may choose to use a custom implementation
     * (for instance, a test fixture), or disable events with {@link Components#noEvents()}.
     * 
     * @param factory a builder/factory object for event configuration
     * @return the builder
     * @since 4.12.0
     */
    public Builder events(EventProcessorFactory factory) {
      this.eventProcessorFactory = factory;
      return this;
    }

    /**
     * Sets the SDK's networking configuration, using a factory object. This object is normally a
     * configuration builder obtained from {@link Components#httpConfiguration()}, which has methods
     * for setting individual HTTP-related properties.
     * 
     * @param factory the factory object
     * @return the builder
     * @since 4.13.0
     * @see Components#httpConfiguration()
     */
    public Builder http(HttpConfigurationFactory factory) {
      this.httpConfigFactory = factory;
      return this;
    }

    /**
     * Sets the SDK's logging configuration, using a factory object. This object is normally a
     * configuration builder obtained from {@link Components#logging()}, which has methods
     * for setting individual logging-related properties.
     * 
     * @param factory the factory object
     * @return the builder
     * @since 5.0.0
     * @see Components#logging()
     */
    public Builder logging(LoggingConfigurationFactory factory) {
      this.loggingConfigFactory = factory;
      return this;
    }
    
    /**
     * Set whether this client is offline.
     * <p>
     * In offline mode, the SDK will not make network connections to LaunchDarkly for any purpose. Feature
     * flag data will only be available if it already exists in the data store, and analytics events will
     * not be sent.
     * <p>
     * This is equivalent to calling {@code dataSource(Components.externalUpdatesOnly())} and
     * {@code events(Components.noEvents())}. It overrides any other values you may have set for
     * {@link #dataSource(DataSourceFactory)} or {@link #events(EventProcessorFactory)}.
     * 
     * @param offline when set to true no calls to LaunchDarkly will be made
     * @return the builder
     */
    public Builder offline(boolean offline) {
      this.offline = offline;
      return this;
    }

    /**
     * Set how long the constructor will block awaiting a successful connection to LaunchDarkly.
     * Setting this to a zero or negative duration will not block and cause the constructor to return immediately.
     * Default value: 5000
     *
     * @param startWait maximum time to wait; null to use the default
     * @return the builder
     */
    public Builder startWait(Duration startWait) {
      this.startWait = startWait == null ? DEFAULT_START_WAIT : startWait;
      return this;
    }

    /**
     * Set the priority to use for all threads created by the SDK.
     * <p>
     * By default, the SDK's worker threads use {@code Thread.MIN_PRIORITY} so that they will yield to
     * application threads if the JVM is busy. You may increase this if you want the SDK to be prioritized
     * over some other low-priority tasks.
     * <p>
     * Values outside the range of [{@code Thread.MIN_PRIORITY}, {@code Thread.MAX_PRIORITY}] will be set
     * to the minimum or maximum.
     *  
     * @param threadPriority the priority for SDK threads
     * @return the builder
     * @since 5.0.0
     */
    public Builder threadPriority(int threadPriority) {
      this.threadPriority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, threadPriority));
      return this;
    }
    
    /**
     * Builds the configured {@link com.launchdarkly.sdk.server.LDConfig} object.
     *
     * @return the {@link com.launchdarkly.sdk.server.LDConfig} configured by this builder
     */
    public LDConfig build() {
      return new LDConfig(this);
    }
  }
}
