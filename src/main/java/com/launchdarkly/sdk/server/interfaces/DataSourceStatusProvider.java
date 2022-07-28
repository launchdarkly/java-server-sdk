package com.launchdarkly.sdk.server.interfaces;

import com.google.common.base.Strings;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An interface for querying the status of the SDK's data source. The data source is the component
 * that receives updates to feature flag data; normally this is a streaming connection, but it could
 * be polling or file data depending on your configuration.
 * <p>
 * An implementation of this interface is returned by {@link com.launchdarkly.sdk.server.interfaces.LDClientInterface#getDataSourceStatusProvider}.
 * Application code never needs to implement this interface.
 * 
 * @since 5.0.0
 */
public interface DataSourceStatusProvider {
  /**
   * Returns the current status of the data source.
   * <p>
   * All of the built-in data source implementations are guaranteed to update this status whenever they
   * successfully initialize, encounter an error, or recover after an error.
   * <p>
   * For a custom data source implementation, it is the responsibility of the data source to push
   * status updates to the SDK; if it does not do so, the status will always be reported as
   * {@link State#INITIALIZING}. 
   * 
   * @return the latest status; will never be null
   */
  public Status getStatus();

  /**
   * Subscribes for notifications of status changes.
   * <p>
   * The listener will be notified whenever any property of the status has changed. See {@link Status} for an
   * explanation of the meaning of each property and what could cause it to change.
   * <p>
   * Notifications will be dispatched on a worker thread. It is the listener's responsibility to return as soon as
   * possible so as not to block subsequent notifications.
   * 
   * @param listener the listener to add
   */
  public void addStatusListener(StatusListener listener);

  /**
   * Unsubscribes from notifications of status changes.
   * 
   * @param listener the listener to remove; if no such listener was added, this does nothing
   */
  public void removeStatusListener(StatusListener listener);

  /**
   * A synchronous method for waiting for a desired connection state.
   * <p>
   * If the current state is already {@code desiredState} when this method is called, it immediately returns.
   * Otherwise, it blocks until 1. the state has become {@code desiredState}, 2. the state has become
   * {@link State#OFF} (since that is a permanent condition), 3. the specified timeout elapses, or 4.
   * the current thread is deliberately interrupted with {@link Thread#interrupt()}.
   * <p>
   * A scenario in which this might be useful is if you want to create the {@code LDClient} without waiting
   * for it to initialize, and then wait for initialization at a later time or on a different thread:
   * <pre><code>
   *     // create the client but do not wait
   *     LDConfig config = new LDConfig.Builder().startWait(Duration.ZERO).build();
   *     client = new LDClient(sdkKey, config);
   *     
   *     // later, possibly on another thread:
   *     boolean inited = client.getDataSourceStatusProvider().waitFor(
   *         DataSourceStatusProvider.State.VALID, Duration.ofSeconds(10));
   *     if (!inited) {
   *         // do whatever is appropriate if initialization has timed out
   *     }       
   * </code></pre>
   * 
   * @param desiredState the desired connection state (normally this would be {@link State#VALID}) 
   * @param timeout the maximum amount of time to wait-- or {@link Duration#ZERO} to block indefinitely
   *   (unless the thread is explicitly interrupted) 
   * @return true if the connection is now in the desired state; false if it timed out, or if the state
   *   changed to {@link State#OFF} and that was not the desired state
   * @throws InterruptedException if {@link Thread#interrupt()} was called on this thread while blocked
   */
  public boolean waitFor(State desiredState, Duration timeout) throws InterruptedException;
  
  /**
   * An enumeration of possible values for {@link DataSourceStatusProvider.Status#getState()}.
   */
  public enum State {
    /**
     * The initial state of the data source when the SDK is being initialized.
     * <p>
     * If it encounters an error that requires it to retry initialization, the state will remain at
     * {@link #INITIALIZING} until it either succeeds and becomes {@link #VALID}, or permanently fails and
     * becomes {@link #OFF}.
     */
    INITIALIZING,
    
    /**
     * Indicates that the data source is currently operational and has not had any problems since the
     * last time it received data.
     * <p>
     * In streaming mode, this means that there is currently an open stream connection and that at least
     * one initial message has been received on the stream. In polling mode, it means that the last poll
     * request succeeded.
     */
    VALID,
    
    /**
     * Indicates that the data source encountered an error that it will attempt to recover from.
     * <p>
     * In streaming mode, this means that the stream connection failed, or had to be dropped due to some
     * other error, and will be retried after a backoff delay. In polling mode, it means that the last poll
     * request failed, and a new poll request will be made after the configured polling interval.
     */
    INTERRUPTED,
    
    /**
     * Indicates that the data source has been permanently shut down.
     * <p>
     * This could be because it encountered an unrecoverable error (for instance, the LaunchDarkly service
     * rejected the SDK key; an invalid SDK key will never become valid), or because the SDK client was
     * explicitly shut down.
     */
    OFF;
  }

  /**
   * An enumeration describing the general type of an error reported in {@link ErrorInfo}.
   * 
   * @see ErrorInfo#getKind()
   */
  public static enum ErrorKind {
    /**
     * An unexpected error, such as an uncaught exception, further described by {@link ErrorInfo#getMessage()}.
     */
    UNKNOWN,
    
    /**
     * An I/O error such as a dropped connection.
     */
    NETWORK_ERROR,
    
    /**
     * The LaunchDarkly service returned an HTTP response with an error status, available with
     * {@link ErrorInfo#getStatusCode()}.
     */
    ERROR_RESPONSE,
    
    /**
     * The SDK received malformed data from the LaunchDarkly service.
     */
    INVALID_DATA,
    
    /**
     * The data source itself is working, but when it tried to put an update into the data store, the data
     * store failed (so the SDK may not have the latest data).
     * <p>
     * Data source implementations do not need to report this kind of error; it will be automatically
     * reported by the SDK when exceptions are detected.
     */
    STORE_ERROR
  }
  
  /**
   * A description of an error condition that the data source encountered.
   * 
   * @see Status#getLastError()
   */
  public static final class ErrorInfo {
    private final ErrorKind kind;
    private final int statusCode;
    private final String message;
    private final Instant time;
    
    /**
     * Constructs an instance.
     * 
     * @param kind the general category of the error
     * @param statusCode an HTTP status or zero
     * @param message an error message if applicable, or null
     * @param time the error timestamp
     */
    public ErrorInfo(ErrorKind kind, int statusCode, String message, Instant time) {
      this.kind = kind;
      this.statusCode = statusCode;
      this.message = message;
      this.time = time;
    }

    /**
     * Constructs an instance based on an exception.
     * 
     * @param kind the general category of the error
     * @param t the exception
     * @return an ErrorInfo
     */
    public static ErrorInfo fromException(ErrorKind kind, Throwable t) {
      return new ErrorInfo(kind, 0, t.toString(), Instant.now());
    }

    /**
     * Constructs an instance based on an HTTP error status.
     * 
     * @param statusCode the status code
     * @return an ErrorInfo
     */
    public static ErrorInfo fromHttpError(int statusCode) {
      return new ErrorInfo(ErrorKind.ERROR_RESPONSE, statusCode, null, Instant.now());
    }
    
    /**
     * Returns an enumerated value representing the general category of the error.
     * 
     * @return the general category of the error
     */
    public ErrorKind getKind() {
      return kind;
    }

    /**
     * Returns the HTTP status code if the error was {@link ErrorKind#ERROR_RESPONSE}, or zero otherwise.
     * 
     * @return an HTTP status or zero
     */
    public int getStatusCode() {
      return statusCode;
    }

    /**
     * Returns any additional human-readable information relevant to the error. The format of this message
     * is subject to change and should not be relied on programmatically.
     * 
     * @return an error message if applicable, or null
     */
    public String getMessage() {
      return message;
    }

    /**
     * Returns the date/time that the error occurred.
     * 
     * @return the error timestamp
     */
    public Instant getTime() {
      return time;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof ErrorInfo) {
        ErrorInfo o = (ErrorInfo)other;
        return kind == o.kind && statusCode == o.statusCode && Objects.equals(message, o.message) &&
            Objects.equals(time, o.time);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(kind, statusCode, message, time);
    }
    
    @Override
    public String toString() {
      StringBuilder s = new StringBuilder();
      s.append(kind.toString());
      if (statusCode > 0 || !Strings.isNullOrEmpty(message)) {
        s.append("(");
        if (statusCode > 0) {
          s.append(statusCode);
        }
        if (!Strings.isNullOrEmpty(message)) {
          if (statusCode > 0) {
            s.append(",");
          }
          s.append(message);
        }
        s.append(")");
      }
      if (time != null) {
        s.append("@");
        s.append(time.toString());
      }
      return s.toString();
    }
  }
  
  /**
   * Information about the data source's status and about the last status change.
   */
  public static final class Status {
    private final State state;
    private final Instant stateSince;
    private final ErrorInfo lastError;

    /**
     * Constructs a new instance.
     * 
     * @param state the basic state as an enumeration
     * @param stateSince timestamp of the last state transition
     * @param lastError a description of the last error, or null if no errors have occurred since startup
     */
    public Status(State state, Instant stateSince, ErrorInfo lastError) {
      this.state = state;
      this.stateSince = stateSince;
      this.lastError = lastError;
    }
    
    /**
     * Returns an enumerated value representing the overall current state of the data source.
     * 
     * @return the basic state
     */
    public State getState() {
      return state;
    }
    
    /**
     * Returns the date/time that the value of {@link #getState()} most recently changed.
     * <p>
     * The meaning of this depends on the current state:
     * <ul>
     * <li> For {@link State#INITIALIZING}, it is the time that the SDK started initializing.
     * <li> For {@link State#VALID}, it is the time that the data source most recently entered a valid
     * state, after previously having been either {@link State#INITIALIZING} or {@link State#INTERRUPTED}.
     * <li> For {@link State#INTERRUPTED}, it is the time that the data source most recently entered an
     * error state, after previously having been {@link State#VALID}.
     * <li> For {@link State#OFF}, it is the time that the data source encountered an unrecoverable error
     * or that the SDK was explicitly shut down.
     * </ul>
     *  
     * @return the timestamp of the last state change
     */
    public Instant getStateSince() {
      return stateSince;
    }
    
    /**
     * Returns information about the last error that the data source encountered, if any.
     * <p>
     * This property should be updated whenever the data source encounters a problem, even if it does
     * not cause {@link #getState()} to change. For instance, if a stream connection fails and the
     * state changes to {@link State#INTERRUPTED}, and then subsequent attempts to restart the
     * connection also fail, the state will remain {@link State#INTERRUPTED} but the error information
     * will be updated each time-- and the last error will still be reported in this property even if
     * the state later becomes {@link State#VALID}.
     * 
     * @return a description of the last error, or null if no errors have occurred since startup
     */
    public ErrorInfo getLastError() {
      return lastError;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof Status) {
        Status o = (Status)other;
        return state == o.state && Objects.equals(stateSince, o.stateSince) && Objects.equals(lastError, o.lastError);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(state, stateSince, lastError);
    }
    
    @Override
    public String toString() {
      return "Status(" + state + "," + stateSince + "," + lastError + ")";
    }
  }

  /**
   * Interface for receiving status change notifications.
   */
  public static interface StatusListener {
    /**
     * Called when any property of the data source status has changed.
     * 
     * @param newStatus the new status
     */
    public void dataSourceStatusChanged(Status newStatus);
  }
}
