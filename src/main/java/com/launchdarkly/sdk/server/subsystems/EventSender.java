package com.launchdarkly.sdk.server.subsystems;

import java.io.Closeable;
import java.net.URI;

/**
 * Interface for a component that can deliver preformatted event data.
 * <p>
 * By default, the SDK sends event data to the LaunchDarkly events service via HTTP. You may
 * provide a different implementation of event delivery by implementing this interface-- for
 * instance, to create a test fixture, or to store the data somewhere else.
 * 
 * @see com.launchdarkly.sdk.server.integrations.EventProcessorBuilder#eventSender(ComponentConfigurer)
 * @since 4.14.0
 */
public interface EventSender extends Closeable {
  /**
   * Result type for event sending methods.
   */
  public enum Result {
    /**
     * The EventSender successfully delivered the event(s).
     */
    SUCCESS,
    
    /**
     * The EventSender was not able to deliver the events.
     */
    FAILURE,
    
    /**
     * The EventSender was not able to deliver the events, and the nature of the error indicates that
     * the SDK should not attempt to send any more events.
     */
    STOP
  };
  
  /**
   * Attempt to deliver an analytics event data payload.
   * <p>
   * This method will be called synchronously from an event delivery worker thread. 
   * 
   * @param data the preformatted JSON data, in UTF-8 encoding
   * @param eventCount the number of individual events in the data
   * @param eventsBaseUri the configured events endpoint base URI
   * @return a {@link Result}
   */
  Result sendAnalyticsEvents(byte[] data, int eventCount, URI eventsBaseUri);

  /**
   * Attempt to deliver a diagnostic event data payload.
   * <p>
   * This method will be called synchronously from an event delivery worker thread. 
   * 
   * @param data the preformatted JSON data, in UTF-8 encoding
   * @param eventsBaseUri the configured events endpoint base URI
   * @return a {@link Result}
   */
  Result sendDiagnosticEvent(byte[] data, URI eventsBaseUri);
}
