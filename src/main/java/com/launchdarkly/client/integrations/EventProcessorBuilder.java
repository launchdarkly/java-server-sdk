package com.launchdarkly.client.integrations;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.EventProcessorFactory;
import com.launchdarkly.client.interfaces.EventSender;
import com.launchdarkly.client.interfaces.EventSenderFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Contains methods for configuring delivery of analytics events.
 * <p>
 * The SDK normally buffers analytics events and sends them to LaunchDarkly at intervals. If you want
 * to customize this behavior, create a builder with {@link Components#sendEvents()}, change its
 * properties with the methods of this class, and pass it to {@link com.launchdarkly.client.LDConfig.Builder#events(EventProcessorFactory)}:
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .events(Components.sendEvents().capacity(5000).flushIntervalSeconds(2))
 *         .build();
 * </code></pre>
 * <p>
 * These properties will override any equivalent deprecated properties that were set with {@code LDConfig.Builder},
 * such as {@link com.launchdarkly.client.LDConfig.Builder#capacity(int)}.
 * <p>
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#sendEvents()}.
 * 
 * @since 4.12.0
 */
public abstract class EventProcessorBuilder implements EventProcessorFactory {
  /**
   * The default value for {@link #capacity(int)}.
   */
  public static final int DEFAULT_CAPACITY = 10000;

  /**
   * The default value for {@link #diagnosticRecordingIntervalSeconds(int)}.
   */
  public static final int DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL_SECONDS = 60 * 15;
  
  /**
   * The default value for {@link #flushIntervalSeconds(int)}.
   */
  public static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 5;
  
  /**
   * The default value for {@link #userKeysCapacity(int)}.
   */
  public static final int DEFAULT_USER_KEYS_CAPACITY = 1000;
  
  /**
   * The default value for {@link #userKeysFlushIntervalSeconds(int)}.
   */
  public static final int DEFAULT_USER_KEYS_FLUSH_INTERVAL_SECONDS = 60 * 5;

  /**
   * The minimum value for {@link #diagnosticRecordingIntervalSeconds(int)}.
   */
  public static final int MIN_DIAGNOSTIC_RECORDING_INTERVAL_SECONDS = 60;
  
  protected boolean allAttributesPrivate = false;
  protected URI baseURI;
  protected int capacity = DEFAULT_CAPACITY;
  protected int diagnosticRecordingIntervalSeconds = DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL_SECONDS;
  protected int flushIntervalSeconds = DEFAULT_FLUSH_INTERVAL_SECONDS;
  protected boolean inlineUsersInEvents = false;
  protected Set<String> privateAttrNames;
  protected int userKeysCapacity = DEFAULT_USER_KEYS_CAPACITY;
  protected int userKeysFlushIntervalSeconds = DEFAULT_USER_KEYS_FLUSH_INTERVAL_SECONDS;
  protected EventSenderFactory eventSenderFactory = null;

  /**
   * Sets whether or not all optional user attributes should be hidden from LaunchDarkly.
   * <p>
   * If this is {@code true}, all user attribute values (other than the key) will be private, not just
   * the attributes specified in {@link #privateAttributeNames(String...)} or on a per-user basis with
   * {@link com.launchdarkly.client.LDUser.Builder} methods. By default, it is {@code false}. 
   * 
   * @param allAttributesPrivate true if all user attributes should be private
   * @return the builder
   * @see #privateAttributeNames(String...)
   * @see com.launchdarkly.client.LDUser.Builder
   */
  public EventProcessorBuilder allAttributesPrivate(boolean allAttributesPrivate) {
    this.allAttributesPrivate = allAttributesPrivate;
    return this;
  }
  
  /**
   * Sets a custom base URI for the events service.
   * <p>
   * You will only need to change this value in the following cases:
   * <ul>
   * <li> You are using the <a href="https://docs.launchdarkly.com/docs/the-relay-proxy">Relay Proxy</a> with
   *   event forwarding enabled. Set {@code streamUri} to the base URI of the Relay Proxy instance.
   * <li> You are connecting to a test server or a nonstandard endpoint for the LaunchDarkly service.
   * </ul>
   * 
   * @param baseURI the base URI of the events service; null to use the default
   * @return the builder
   */
  public EventProcessorBuilder baseURI(URI baseURI) {
    this.baseURI = baseURI;
    return this;
  }
  
  /**
   * Set the capacity of the events buffer.
   * <p>
   * The client buffers up to this many events in memory before flushing. If the capacity is exceeded before
   * the buffer is flushed (see {@link #flushIntervalSeconds(int)}, events will be discarded. Increasing the
   * capacity means that events are less likely to be discarded, at the cost of consuming more memory.
   * <p>
   * The default value is {@link #DEFAULT_CAPACITY}.
   *
   * @param capacity the capacity of the event buffer
   * @return the builder
   */
  public EventProcessorBuilder capacity(int capacity) {
    this.capacity = capacity;
    return this;
  }

  /**
   * Sets the interval at which periodic diagnostic data is sent.
   * <p>
   * The default value is {@link #DEFAULT_DIAGNOSTIC_RECORDING_INTERVAL_SECONDS}; the minimum value is
   * {@link #MIN_DIAGNOSTIC_RECORDING_INTERVAL_SECONDS}. This property is ignored if
   * {@link com.launchdarkly.client.LDConfig.Builder#diagnosticOptOut(boolean)} is set to {@code true}.
   *
   * @see com.launchdarkly.client.LDConfig.Builder#diagnosticOptOut(boolean)
   *
   * @param diagnosticRecordingIntervalSeconds the diagnostics interval in seconds
   * @return the builder
   */
  public EventProcessorBuilder diagnosticRecordingIntervalSeconds(int diagnosticRecordingIntervalSeconds) {
    this.diagnosticRecordingIntervalSeconds = diagnosticRecordingIntervalSeconds < MIN_DIAGNOSTIC_RECORDING_INTERVAL_SECONDS ?
        MIN_DIAGNOSTIC_RECORDING_INTERVAL_SECONDS : diagnosticRecordingIntervalSeconds;
    return this;
  }

  /**
   * Specifies a custom implementation for event delivery.
   * <p>
   * The standard event delivery implementation sends event data via HTTP/HTTPS to the LaunchDarkly events
   * service endpoint (or any other endpoint specified with {@link #baseURI(URI)}. Providing a custom
   * implementation may be useful in tests, or if the event data needs to be stored and forwarded. 
   * 
   * @param eventSenderFactory a factory for an {@link EventSender} implementation
   * @return the builder
   */
  public EventProcessorBuilder eventSender(EventSenderFactory eventSenderFactory) {
    this.eventSenderFactory = eventSenderFactory;
    return this;
  }
  
  /**
   * Sets the interval between flushes of the event buffer.
   * <p>
   * Decreasing the flush interval means that the event buffer is less likely to reach capacity.
   * <p>
   * The default value is {@link #DEFAULT_FLUSH_INTERVAL_SECONDS}.
   *
   * @param flushIntervalSeconds the flush interval in seconds
   * @return the builder
   */
  public EventProcessorBuilder flushIntervalSeconds(int flushIntervalSeconds) {
    this.flushIntervalSeconds = flushIntervalSeconds;
    return this;
  }
  
  /**
   * Sets whether to include full user details in every analytics event.
   * <p>
   * The default is {@code false}: events will only include the user key, except for one "index" event
   * that provides the full details for the user).
   * 
   * @param inlineUsersInEvents true if you want full user details in each event
   * @return the builder
   */
  public EventProcessorBuilder inlineUsersInEvents(boolean inlineUsersInEvents) {
    this.inlineUsersInEvents = inlineUsersInEvents;
    return this;
  }

  /**
   * Marks a set of attribute names as private.
   * <p>
   * Any users sent to LaunchDarkly with this configuration active will have attributes with these
   * names removed. This is in addition to any attributes that were marked as private for an
   * individual user with {@link com.launchdarkly.client.LDUser.Builder} methods.
   *
   * @param attributeNames a set of names that will be removed from user data set to LaunchDarkly
   * @return the builder
   * @see #allAttributesPrivate(boolean)
   * @see com.launchdarkly.client.LDUser.Builder
   */
  public EventProcessorBuilder privateAttributeNames(String... attributeNames) {
    this.privateAttrNames = new HashSet<>(Arrays.asList(attributeNames));
    return this;
  }

  /**
   * Sets the number of user keys that the event processor can remember at any one time.
   * <p>
   * To avoid sending duplicate user details in analytics events, the SDK maintains a cache of
   * recently seen user keys, expiring at an interval set by {@link #userKeysFlushIntervalSeconds(int)}.
   * <p>
   * The default value is {@link #DEFAULT_USER_KEYS_CAPACITY}.
   * 
   * @param userKeysCapacity the maximum number of user keys to remember
   * @return the builder
   */
  public EventProcessorBuilder userKeysCapacity(int userKeysCapacity) {
    this.userKeysCapacity = userKeysCapacity;
    return this;
  }
  
  /**
   * Sets the interval at which the event processor will reset its cache of known user keys.
   * <p>
   * The default value is {@link #DEFAULT_USER_KEYS_FLUSH_INTERVAL_SECONDS}.
   *
   * @param userKeysFlushIntervalSeconds the flush interval in seconds
   * @return the builder
   */
  public EventProcessorBuilder userKeysFlushIntervalSeconds(int userKeysFlushIntervalSeconds) {
    this.userKeysFlushIntervalSeconds = userKeysFlushIntervalSeconds;
    return this;
  }
}
