package com.launchdarkly.client;

/**
 * Stub implementation of {@link EventProcessor} for when we don't want to send any events.
 */
class NullEventProcessor implements EventProcessor {
  @Override
  public void sendEvent(Event e) {
  }
  
  @Override
  public void flush() {
  }
  
  @Override
  public void close() {
  }
}
