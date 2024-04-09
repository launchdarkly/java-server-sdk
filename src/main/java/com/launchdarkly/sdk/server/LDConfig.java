package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.BigSegmentsStatus;
import com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder;
import com.launchdarkly.sdk.server.integrations.HooksConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder;
import com.launchdarkly.sdk.server.integrations.WrapperInfoBuilder;
import com.launchdarkly.sdk.server.interfaces.ApplicationInfo;
import com.launchdarkly.sdk.server.interfaces.BigSegmentsConfiguration;
import com.launchdarkly.sdk.server.interfaces.ServiceEndpoints;
import com.launchdarkly.sdk.server.interfaces.WrapperInfo;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;
import com.launchdarkly.sdk.server.subsystems.HookConfiguration;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

import java.time.Duration;

/**
 * This class exposes advanced configuration options for the {@link LDClient}. Instances of this class must be constructed with a {@link com.launchdarkly.sdk.server.LDConfig.Builder}.
 */
public final class LDConfig {
  /**
   * The default value for {@link Builder#startWait(Duration)}: 5 seconds.
   */
  public static final Duration DEFAULT_START_WAIT = Duration.ofSeconds(5);

  protected static final LDConfig DEFAULT = new Builder().build();

  final ApplicationInfo applicationInfo;
  final ComponentConfigurer<BigSegmentsConfiguration> bigSegments;
  final ComponentConfigurer<DataSource> dataSource;
  final ComponentConfigurer<DataStore> dataStore;
  final boolean diagnosticOptOut;
  final ComponentConfigurer<EventProcessor> events;
  final HookConfiguration hooks;
  final ComponentConfigurer<HttpConfiguration> http;
  final ComponentConfigurer<LoggingConfiguration> logging;
  final ServiceEndpoints serviceEndpoints;
  final boolean offline;
  final Duration startWait;
  final int threadPriority;
  final WrapperInfo wrapperInfo;

  protected LDConfig(Builder builder) {
    if (builder.offline) {
      this.dataSource = Components.externalUpdatesOnly();
      this.events = Components.noEvents();
    } else {
      this.dataSource = builder.dataSource == null ? Components.streamingDataSource() : builder.dataSource;
      this.events = builder.events == null ? Components.sendEvents() : builder.events;
    }
    this.applicationInfo = (builder.applicationInfoBuilder == null ? Components.applicationInfo() :
      builder.applicationInfoBuilder)
      .createApplicationInfo();
    this.bigSegments = builder.bigSegments == null ? Components.bigSegments(null) : builder.bigSegments;
    this.dataStore = builder.dataStore == null ? Components.inMemoryDataStore() : builder.dataStore;
    this.diagnosticOptOut = builder.diagnosticOptOut;
    this.hooks = (builder.hooksConfigurationBuilder == null ? Components.hooks() : builder.hooksConfigurationBuilder).build();
    this.http = builder.http == null ? Components.httpConfiguration() : builder.http;
    this.logging = builder.logging == null ? Components.logging() : builder.logging;
    this.offline = builder.offline;
    this.serviceEndpoints = (builder.serviceEndpointsBuilder == null ? Components.serviceEndpoints() :
      builder.serviceEndpointsBuilder)
      .createServiceEndpoints();
    this.startWait = builder.startWait;
    this.threadPriority = builder.threadPriority;
    this.wrapperInfo = builder.wrapperBuilder != null ? builder.wrapperBuilder.build() : null;
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
    private ApplicationInfoBuilder applicationInfoBuilder = null;
    private ComponentConfigurer<BigSegmentsConfiguration> bigSegments = null;
    private ComponentConfigurer<DataSource> dataSource = null;
    private ComponentConfigurer<DataStore> dataStore = null;
    private boolean diagnosticOptOut = false;
    private ComponentConfigurer<EventProcessor> events = null;
    private HooksConfigurationBuilder hooksConfigurationBuilder = null;
    private ComponentConfigurer<HttpConfiguration> http = null;
    private ComponentConfigurer<LoggingConfiguration> logging = null;
    private ServiceEndpointsBuilder serviceEndpointsBuilder = null;
    private boolean offline = false;
    private Duration startWait = DEFAULT_START_WAIT;
    private int threadPriority = Thread.MIN_PRIORITY;
    private WrapperInfoBuilder wrapperBuilder = null;

    /**
     * Creates a builder with all configuration parameters set to the default
     */
    public Builder() {
    }

    /**
     * Creates a {@link LDConfig.Builder} from the provided {@link LDConfig}
     *
     * @param config to be used to initialize the builder
     * @return the builder
     */
    public static Builder fromConfig(LDConfig config) {
      Builder newBuilder = new Builder();
      newBuilder.applicationInfoBuilder = ApplicationInfoBuilder.fromApplicationInfo(config.applicationInfo);
      newBuilder.bigSegments = config.bigSegments;
      newBuilder.dataSource = config.dataSource;
      newBuilder.dataStore = config.dataStore;
      newBuilder.diagnosticOptOut = config.diagnosticOptOut;
      newBuilder.events = config.events;
      newBuilder.hooksConfigurationBuilder = ComponentsImpl.HooksConfigurationBuilderImpl.fromHooksConfiguration(config.hooks);
      newBuilder.http = config.http;
      newBuilder.logging = config.logging;

      newBuilder.serviceEndpointsBuilder = ComponentsImpl.ServiceEndpointsBuilderImpl
        .fromServiceEndpoints(config.serviceEndpoints);
      newBuilder.offline = config.offline;
      newBuilder.startWait = config.startWait;
      newBuilder.threadPriority = config.threadPriority;
      newBuilder.wrapperBuilder = config.wrapperInfo != null ?
        ComponentsImpl.WrapperInfoBuilderImpl.fromInfo(config.wrapperInfo) : null;
      return newBuilder;
    }

    /**
     * Sets the SDK's application metadata, which may be used in LaunchDarkly analytics or other product features,
     * but does not affect feature flag evaluations.
     * <p>
     * This object is normally a configuration builder obtained from {@link Components#applicationInfo()},
     * which has methods for setting individual logging-related properties.
     *
     * @param applicationInfoBuilder a configuration builder object returned by {@link Components#applicationInfo()}
     * @return the builder
     * @since 5.8.0
     */
    public Builder applicationInfo(ApplicationInfoBuilder applicationInfoBuilder) {
      this.applicationInfoBuilder = applicationInfoBuilder;
      return this;
    }

    /**
     * Sets the configuration of the SDK's Big Segments feature.
     * <p>
     * Big Segments are a specific type of user segments. For more information, read the
     * <a href="https://docs.launchdarkly.com/home/users/big-segments">LaunchDarkly documentation
     * </a>.
     * <p>
     * If you are using this feature, you will normally specify a database implementation that
     * matches how the LaunchDarkly Relay Proxy is configured, since the Relay Proxy manages the
     * Big Segment data.
     * <p>
     * By default, there is no implementation and Big Segments cannot be evaluated. In this case,
     * any flag evaluation that references a Big Segment will behave as if no users are included in
     * any Big Segments, and the {@link EvaluationReason} associated with any such flag evaluation
     * will have a {@link BigSegmentsStatus} of {@link BigSegmentsStatus#NOT_CONFIGURED}.
     *
     * <pre><code>
     *     // This example uses the Redis integration
     *     LDConfig config = LDConfig.builder()
     *         .bigSegments(Components.bigSegments(Redis.dataStore().prefix("app1"))
     *             .userCacheSize(2000))
     *         .build();
     * </code></pre>
     *
     * @param bigSegmentsConfigurer the Big Segments configuration builder
     * @return the builder
     * @since 5.7.0
     * @see Components#bigSegments(ComponentConfigurer)
     */
    public Builder bigSegments(ComponentConfigurer<BigSegmentsConfiguration> bigSegmentsConfigurer) {
      this.bigSegments = bigSegmentsConfigurer;
      return this;
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
     * @param dataSourceConfigurer the data source configuration builder
     * @return the main configuration builder
     * @since 4.12.0
     */
    public Builder dataSource(ComponentConfigurer<DataSource> dataSourceConfigurer) {
      this.dataSource = dataSourceConfigurer;
      return this;
    }

    /**
     * Sets the implementation of the data store to be used for holding feature flags and
     * related data received from LaunchDarkly, using a factory object. The default is
     * {@link Components#inMemoryDataStore()}; for database integrations, use
     * {@link Components#persistentDataStore(ComponentConfigurer)}.
     *
     * @param dataStoreConfigurer the data store configuration builder
     * @return the main configuration builder
     * @since 4.12.0
     */
    public Builder dataStore(ComponentConfigurer<DataStore> dataStoreConfigurer) {
      this.dataStore = dataStoreConfigurer;
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
     * The default is {@link Components#sendEvents()} with no custom options. You may instead call
     * {@link Components#sendEvents()} and then set custom options for event processing; or, disable
     * events with {@link Components#noEvents()}; or, choose to use a custom implementation (for
     * instance, a test fixture).
     *
     * @param eventsConfigurer the events configuration builder
     * @return the main configuration builder
     * @since 4.12.0
     * @see Components#sendEvents()
     * @see Components#noEvents()
     */
    public Builder events(ComponentConfigurer<EventProcessor> eventsConfigurer) {
      this.events = eventsConfigurer;
      return this;
    }

    /**
     * Sets the SDK's hooks configuration, using a builder. This is normally a obtained from
     * {@link Components#hooks()} ()}, which has methods for setting individual other hook
     * related properties.
     *
     * @param hooksConfiguration the hooks configuration builder
     * @return the main configuration builder
     * @see Components#hooks()
     */
    public Builder hooks(HooksConfigurationBuilder hooksConfiguration) {
      this.hooksConfigurationBuilder = hooksConfiguration;
      return this;
    }

    /**
     * Sets the SDK's networking configuration, using a configuration builder. This builder is
     * obtained from {@link Components#httpConfiguration()}, and has methods for setting individual
     * HTTP-related properties.
     *
     * @param httpConfigurer the HTTP configuration builder
     * @return the main configuration builder
     * @since 4.13.0
     * @see Components#httpConfiguration()
     */
    public Builder http(ComponentConfigurer<HttpConfiguration> httpConfigurer) {
      this.http = httpConfigurer;
      return this;
    }

    /**
     * Sets the SDK's logging configuration, using a factory object. This object is normally a
     * configuration builder obtained from {@link Components#logging()}, which has methods
     * for setting individual logging-related properties.
     *
     * @param loggingConfigurer the logging configuration builder
     * @return the main configuration builder
     * @since 5.0.0
     * @see Components#logging()
     */
    public Builder logging(ComponentConfigurer<LoggingConfiguration> loggingConfigurer) {
      this.logging = loggingConfigurer;
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
     * {@link #dataSource(ComponentConfigurer)} or {@link #events(ComponentConfigurer)}.
     *
     * @param offline when set to true no calls to LaunchDarkly will be made
     * @return the builder
     */
    public Builder offline(boolean offline) {
      this.offline = offline;
      return this;
    }

    /**
     * Sets the base service URIs used by SDK components.
     * <p>
     * This object is normally a configuration builder obtained from {@link Components#serviceEndpoints()},
     * which has methods for setting each external endpoint to a custom URI.
     *
     * @param serviceEndpointsBuilder a configuration builder object returned by {@link Components#applicationInfo()}
     * @return the builder
     * @since 5.9.0
     */
    public Builder serviceEndpoints(ServiceEndpointsBuilder serviceEndpointsBuilder) {
      this.serviceEndpointsBuilder = serviceEndpointsBuilder;
      return this;
    }

    /**
     * Set how long the constructor will block awaiting a successful connection to LaunchDarkly.
     * Setting this to a zero or negative duration will not block and cause the constructor to return immediately.
     * <p>
     * The default is {@link #DEFAULT_START_WAIT}.
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
     * Set the wrapper information.
     * <p>
     * This is intended for use with wrapper SDKs from LaunchDarkly.
     * <p>
     * If the WrapperBuilder is set, then it will replace the wrapper information from the HttpPropertiesBuilder.
     * Additionally, any wrapper SDK may overwrite any application developer provided wrapper information.
     *
     * @param wrapperBuilder the wrapper builder
     * @return the builder
     * @since 7.1.0
     */
    public Builder wrapper(WrapperInfoBuilder wrapperBuilder) {
      this.wrapperBuilder = wrapperBuilder;
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
