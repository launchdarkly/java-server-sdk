package com.launchdarkly.client;

import java.io.Closeable;

/**
 * Interface for an object that can send or store analytics events.
 */
interface EventProcessor extends Closeable {
  /**
   * Processes an event. This method is asynchronous; the event may be sent later in the background
   * at an interval set by {@link LDConfig#flushInterval}, or due to a call to {@link #flush()}.
   * @param e an event
   */
  void sendEvent(Event e);
  
  /**
   * Finishes processing any events that have been buffered. In the default implementation, this means
   * sending the events to LaunchDarkly. This method is synchronous; when it returns, you can assume
   * that all events queued prior to the {@link #flush()} have now been delivered.
   */
  void flush();
}
