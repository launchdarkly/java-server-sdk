package com.launchdarkly.sdk.server.subsystems;

import java.io.Closeable;

/**
 * Interface for an object that can send or store analytics events.
 * @since 4.0.0
 */
public interface EventProcessor extends Closeable {
  /**
   * Records an event asynchronously.
   * @param e an event
   */
  void sendEvent(Event e);
  
  /**
   * Specifies that any buffered events should be sent as soon as possible, rather than waiting
   * for the next flush interval. This method is asynchronous, so events still may not be sent
   * until a later time. However, calling {@link Closeable#close()} will synchronously deliver
   * any events that were not yet delivered prior to shutting down.
   */
  void flush();
}
