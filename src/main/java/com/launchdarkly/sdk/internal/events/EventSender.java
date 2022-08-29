package com.launchdarkly.sdk.internal.events;

import java.io.Closeable;
import java.net.URI;
import java.util.Date;

/**
 * Internal interface for a component that can deliver preformatted event data.
 * <p>
 * This is separate from the public EventSender interface in the SDK that applications can use to
 * provide a custom implementation. The latter is used as a wrapper for this one, so we do not have
 * to expose any types from the internal events code. The public interface is simpler because it
 * only needs to return success/failure/shutdown status; the use of the Date header is an
 * implementation detail that is specific to the default HTTP implementation of event delivery.
 */
public interface EventSender extends Closeable {
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
   * @param data the preformatted JSON data, as a string
   * @param eventsBaseUri the configured events endpoint base URI
   * @return a {@link Result}
   */
  Result sendDiagnosticEvent(byte[] data, URI eventsBaseUri);
  
  /**
   * Encapsulates the results of a call to an EventSender method.
   */
  public static final class Result {
    private boolean success;
    private boolean mustShutDown;
    private Date timeFromServer;
    
    /**
     * Constructs an instance.
     * 
     * @param success true if the events were delivered
     * @param mustShutDown true if an unrecoverable error (such as an HTTP 401 error, implying that the
     *   SDK key is invalid) means the SDK should permanently stop trying to send events
     * @param timeFromServer the parsed value of an HTTP Date header received from the remote server,
     *   if any; this is used to compensate for differences between the application's time and server time
     */
    public Result(boolean success, boolean mustShutDown, Date timeFromServer) {
      this.success = success;
      this.mustShutDown = mustShutDown;
      this.timeFromServer = timeFromServer;
    }

    /**
     * Returns true if the events were delivered.
     * 
     * @return true if the events were delivered
     */
    public boolean isSuccess() {
      return success;
    }

    /**
     * Returns true if an unrecoverable error (such as an HTTP 401 error, implying that the
     * SDK key is invalid) means the SDK should permanently stop trying to send events
     * 
     * @return true if event delivery should shut down
     */
    public boolean isMustShutDown() {
      return mustShutDown;
    }

    /**
     * Returns the parsed value of an HTTP Date header received from the remote server, if any. This
     * is used to compensate for differences between the application's time and server time.
     * 
     * @return a date value or null
     */
    public Date getTimeFromServer() {
      return timeFromServer;
    }
  }
}
