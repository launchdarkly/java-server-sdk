package com.launchdarkly.sdk.internal.events;

import com.launchdarkly.sdk.LDContext;

/**
 * Interface for a strategy for removing duplicate contexts from the event stream. This has
 * been factored out of DefaultEventProcessor because the client-side and server-side SDKs
 * behave differently (client-side does not send index events).
 */
public interface EventContextDeduplicator {
  /**
   * Returns the millisecond interval, if any, at which the event processor should call flush().
   * 
   * @return a number of milliseconds, or null if not applicable
   */
  Long getFlushInterval();
  
  /**
   * Updates the internal state if necessary to reflect that we have seen the given context.
   * Returns true if it is time to insert an index event for this context into the event output.
   * 
   * @param context a context object
   * @return true if an index event should be emitted
   */
  boolean processContext(LDContext context);
  
  /**
   * Forgets any cached context information, so all subsequent contexs will be treated as new.
   */
  void flush();
}
