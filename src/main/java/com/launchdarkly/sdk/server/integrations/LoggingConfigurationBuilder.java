package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.interfaces.LoggingConfigurationFactory;

import java.time.Duration;

/**
 * Contains methods for configuring the SDK's logging behavior.
 * <p>
 * If you want to set non-default values for any of these properties, create a builder with
 * {@link Components#logging()}, change its properties with the methods of this class, and pass it
 * to {@link com.launchdarkly.sdk.server.LDConfig.Builder#logging(LoggingConfigurationFactory)}:
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
public abstract class LoggingConfigurationBuilder implements LoggingConfigurationFactory {
  /**
   * The default value for {@link #logDataSourceOutageAsErrorAfter(Duration)}: one minute.
   */
  public static final Duration DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER = Duration.ofMinutes(1);
  
  protected Duration logDataSourceOutageAsErrorAfter = DEFAULT_LOG_DATA_SOURCE_OUTAGE_AS_ERROR_AFTER;
  
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
