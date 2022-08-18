package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;

/**
 * Base class for all analytics events that are generated by the client. Also defines all of its own subclasses.
 * 
 * Applications do not need to reference these types directly. They are used internally in analytics event
 * processing, and are visible only to support writing a custom implementation of {@link EventProcessor} if
 * desired.
 */
public class Event {
  private final long creationDate;
  private final LDContext context;

  /**
   * Base event constructor.
   * @param creationDate the timestamp in milliseconds
   * @param context the context associated with the event
   */
  public Event(long creationDate, LDContext context) {
    this.creationDate = creationDate;
    this.context = context;
  }
  
  /**
   * The event timestamp.
   * @return the timestamp in milliseconds
   */
  public long getCreationDate() {
    return creationDate;
  }
  
  /**
   * The context associated with the event.
   * @return the context object
   */
  public LDContext getContext() {
    return context;
  }

  /**
   * A custom event created with {@link LDClientInterface#track(String, LDUser)} or one of its overloads.
   */
  public static final class Custom extends Event {
    private final String key;
    private final LDValue data;
    private final Double metricValue;

    /**
     * Constructs a custom event.
     * @param timestamp the timestamp in milliseconds
     * @param key the event key
     * @param context the context associated with the event
     * @param data custom data if any (null is the same as {@link LDValue#ofNull()})
     * @param metricValue custom metric value if any
     * @since 4.8.0
     */
    public Custom(long timestamp, String key, LDContext context, LDValue data, Double metricValue) {
      super(timestamp, context);
      this.key = key;
      this.data = LDValue.normalize(data);
      this.metricValue = metricValue;
    }

    /**
     * The custom event key.
     * @return the event key
     */
    public String getKey() {
      return key;
    }
    
    /**
     * The custom data associated with the event, if any.
     * @return the event data (null is equivalent to {@link LDValue#ofNull()})
     */
    public LDValue getData() {
      return data;
    }
    
    /**
     * The numeric metric value associated with the event, if any.
     * @return the metric value or null
     */
    public Double getMetricValue() {
      return metricValue;
    }
  }

  /**
   * An event created with {@link LDClientInterface#identify(LDUser)}.
   */
  public static final class Identify extends Event {
    /**
     * Constructs an identify event.
     * @param timestamp the timestamp in milliseconds
     * @param context the context associated with the event
     */
    public Identify(long timestamp, LDContext context) {
      super(timestamp, context);
    }
  }

  /**
   * An event created internally by the SDK to hold user data that may be referenced by multiple events.
   */
  public static final class Index extends Event {
    /**
     * Constructs an index event.
     * @param timestamp the timestamp in milliseconds
     * @param context the context associated with the event
     */
    public Index(long timestamp, LDContext context) {
      super(timestamp, context);
    }
  }
  
  /**
   * An event generated by a feature flag evaluation.
   */
  public static final class FeatureRequest extends Event {
    private final String key;
    private final int variation;
    private final LDValue value;
    private final LDValue defaultVal;
    private final int version;
    private final String prereqOf;
    private final boolean trackEvents;
    private final long debugEventsUntilDate;
    private final EvaluationReason reason;
    private final boolean debug;

    /**
     * Constructs a feature request event.
     * @param timestamp the timestamp in milliseconds
     * @param key the flag key
     * @param context the context associated with the event
     * @param version the flag version, or -1 if the flag was not found
     * @param variation the result variation, or -1 if there was an error
     * @param value the result value
     * @param defaultVal the default value passed by the application
     * @param reason the evaluation reason, if it is to be included in the event
     * @param prereqOf if this flag was evaluated as a prerequisite, this is the key of the flag that referenced it
     * @param trackEvents true if full event tracking is turned on for this flag
     * @param debugEventsUntilDate if non-null, the time until which event debugging should be enabled
     * @param debug true if this is a debugging event
     * @since 4.8.0
     */
    public FeatureRequest(long timestamp, String key, LDContext context, int version, int variation, LDValue value,
        LDValue defaultVal, EvaluationReason reason, String prereqOf, boolean trackEvents, long debugEventsUntilDate, boolean debug) {
      super(timestamp, context);
      this.key = key;
      this.version = version;
      this.variation = variation;
      this.value = value;
      this.defaultVal = defaultVal;
      this.prereqOf = prereqOf;
      this.trackEvents = trackEvents;
      this.debugEventsUntilDate = debugEventsUntilDate;
      this.reason = reason;
      this.debug = debug;
    }

    /**
     * The key of the feature flag that was evaluated.
     * @return the flag key
     */
    public String getKey() {
      return key;
    }

    /**
     * The index of the selected flag variation, or -1 if the application default value was used.
     * @return zero-based index of the variation, or -1
     */
    public int getVariation() {
      return variation;
    }

    /**
     * The value of the selected flag variation.
     * @return the value
     */
    public LDValue getValue() {
      return value;
    }

    /**
     * The application default value used in the evaluation.
     * @return the application default
     */
    public LDValue getDefaultVal() {
      return defaultVal;
    }

    /**
     * The version of the feature flag that was evaluated, or -1 if the flag was not found.
     * @return the flag version or null
     */
    public int getVersion() {
      return version;
    }

    /**
     * If this flag was evaluated as a prerequisite for another flag, the key of the other flag.
     * @return a flag key or null
     */
    public String getPrereqOf() {
      return prereqOf;
    }

    /**
     * True if full event tracking is enabled for this flag.
     * @return true if full event tracking is on
     */
    public boolean isTrackEvents() {
      return trackEvents;
    }

    /**
     * If debugging is enabled for this flag, the Unix millisecond time at which to stop debugging.
     * @return a timestamp or zero
     */
    public long getDebugEventsUntilDate() {
      return debugEventsUntilDate;
    }

    /**
     * The {@link EvaluationReason} for this evaluation, or null if the reason was not requested for this evaluation. 
     * @return a reason object or null
     */
    public EvaluationReason getReason() {
      return reason;
    }

    /**
     * True if this event was generated due to debugging being enabled.
     * @return true if this is a debug event
     */
    public boolean isDebug() {
      return debug;
    }
  }
}