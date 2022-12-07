package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.logging.LDLogLevel;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

import java.time.Duration;

/**
 * Contains methods for configuring the SDK's logging behavior.
 * <p>
 * If you want to set non-default values for any of these properties, create a builder with
 * {@link Components#logging()}, change its properties with the methods of this class, and pass it
 * to {@link com.launchdarkly.sdk.server.LDConfig.Builder#logging(ComponentConfigurer)}:
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .logging(
 *           Components.logging()
 *             .logDataSourceOutageAsErrorAfter(Duration.ofSeconds(120))
 *          )
 *         .build();
 * </code></pre>
 * <p>
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#logging()}.
 * 
 * @since 5.0.0
 */
public abstract class LoggingConfigurationBuilder implements ComponentConfigurer<LoggingConfiguration> {
  /**
   * The default value for {@link #logDataSourceOutageAsErrorAfter(Duration)}: one minute.
   */
  public static final Duration DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER = Duration.ofMinutes(1);
  
  protected String baseName = null;
  protected Duration logDataSourceOutageAsErrorAfter = DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER;
  protected LDLogAdapter logAdapter = null;
  protected LDLogLevel minimumLevel = null;
  
  /**
   * Specifies the implementation of logging to use.
   * <p>
   * The <a href="https://github.com/launchdarkly/java-logging"><code>com.launchdarkly.logging</code></a>
   * API defines the {@link LDLogAdapter} interface to specify where log output should be sent.
   * <p>
   * The default logging destination, if no adapter is specified, depends on whether
   * <a href="https://www.slf4j.org/">SLF4J</a> is present in the classpath. If it is, then the SDK uses
   * {@link com.launchdarkly.logging.LDSLF4J#adapter()}, causing output to go to SLF4J; what happens to
   * the output then is determined by the SLF4J configuration. If SLF4J is not present in the classpath,
   * the SDK uses {@link Logs#toConsole()} instead, causing output to go to the {@code System.err} stream.
   * <p>
   * You may use the {@link com.launchdarkly.logging.Logs} factory methods, or a custom implementation,
   * to handle log output differently. For instance, you may specify
   * {@link com.launchdarkly.logging.Logs#toJavaUtilLogging()} to use the <code>java.util.logging</code>
   * framework.
   * <p>
   * For more about logging adapters,
   * see the <a href="https://docs.launchdarkly.com/sdk/features/logging#java">SDK reference guide</a>
   * and the <a href="https://launchdarkly.github.io/java-logging">API documentation</a> for
   * <code>com.launchdarkly.logging</code>.
   * <p>
   * If you don't need to customize any options other than the adapter, you can call
   * {@link Components#logging(LDLogAdapter)} as a shortcut rather than using
   * {@link LoggingConfigurationBuilder}.
   * 
   * @param logAdapter an {@link LDLogAdapter} for the desired logging implementation
   * @return the builder
   * @since 5.10.0
   */
  public LoggingConfigurationBuilder adapter(LDLogAdapter logAdapter) {
    this.logAdapter = logAdapter;
    return this;
  }

  /**
   * Specifies a custom base logger name.
   * <p>
   * Logger names are used to give context to the log output, indicating that it is from the
   * LaunchDarkly SDK instead of another component, or indicating a more specific area of
   * functionality within the SDK. Many logging implementations show the logger name in
   * in brackets, for instance:
   * <pre><code>
   *     [com.launchdarkly.sdk.LDClient] INFO: Reconnected to LaunchDarkly stream
   * </code></pre>
   * <p>
   * If you are using an adapter for a third-party logging framework such as SLF4J (see
   * {@link #adapter(LDLogAdapter)}), most frameworks have a mechanism for filtering log
   * output by the logger name.
   * <p>
   * By default, the SDK uses a base logger name of <code>com.launchdarkly.sdk.LDClient</code>.
   * Messages will be logged either under this name, or with a suffix to indicate what
   * general area of functionality is involved:
   * <ul>
   * <li> <code>.DataSource</code>: problems or status messages regarding how the SDK gets
   * feature flag data from LaunchDarkly. </li>
   * <li> <code>.DataStore</code>: problems or status messages regarding how the SDK stores its
   * feature flag data (for instance, if you are using a database). </li> 
   * <li> <code>.Evaluation</code>: problems in evaluating a feature flag or flags, which were
   * caused by invalid flag data or incorrect usage of the SDK rather than for instance a
   * database problem. </li>
   * <li> <code>.Events</code> problems or status messages regarding the SDK's delivery of
   * analytics event data to LaunchDarkly. </li>
   * </ul>
   * <p>
   * Setting {@link #baseLoggerName(String)} to a non-null value overrides the default. The
   * SDK still adds the same suffixes to the name, so for instance if you set it to
   * <code>"LD"</code>, the example message above would show <code>[LD.DataSource]</code>.
   * 
   * @param name the base logger name
   * @return the builder
   * @since 5.10.0
   */
  public LoggingConfigurationBuilder baseLoggerName(String name) {
    this.baseName = name;
    return this;
  }
  
  /**
   * Specifies the lowest level of logging to enable.
   * <p>
   * This is only applicable when using an implementation of logging that does not have its own
   * external configuration mechanism, such as {@link Logs#toConsole()}. It adds a log level filter
   * so that log messages at lower levels are suppressed. For instance, setting the minimum level to
   * {@link LDLogLevel#INFO} means that <code>DEBUG</code>-level output is disabled. If not specified,
   * the default minimum level is {@link LDLogLevel#INFO}.
   * <p>
   * When using a logging framework like SLF4J or {@code java.util.logging} that has its own
   * separate mechanism for log filtering, you must use that framework's configuration options for
   * log levels; calling {@link #level(LDLogLevel)} in that case has no effect.  
   * 
   * @param minimumLevel the lowest level of logging to enable
   * @return the builder
   * @since 5.10.0
   */
  public LoggingConfigurationBuilder level(LDLogLevel minimumLevel) {
    this.minimumLevel = minimumLevel;
    return this;
  }
  
  /**
   * Sets the time threshold, if any, after which the SDK will log a data source outage at {@code ERROR}
   * level instead of {@code WARN} level.
   * <p>
   * A data source outage means that an error condition, such as a network interruption or an error from
   * the LaunchDarkly service, is preventing the SDK from receiving feature flag updates. Many outages are
   * brief and the SDK can recover from them quickly; in that case it may be undesirable to log an
   * {@code ERROR} line, which might trigger an unwanted automated alert depending on your monitoring
   * tools. So, by default, the SDK logs such errors at {@code WARN} level. However, if the amount of time
   * specified by this method elapses before the data source starts working again, the SDK will log an
   * additional message at {@code ERROR} level to indicate that this is a sustained problem.
   * <p>
   * The default is {@link #DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER}. Setting it to {@code null}
   * will disable this feature, so you will only get {@code WARN} messages.
   * 
   * @param logDataSourceOutageAsErrorAfter the error logging threshold, or null
   * @return the builder
   */
  public LoggingConfigurationBuilder logDataSourceOutageAsErrorAfter(Duration logDataSourceOutageAsErrorAfter) {
    this.logDataSourceOutageAsErrorAfter = logDataSourceOutageAsErrorAfter;
    return this;
  }
}
