package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.internal.events.EventContextDeduplicator;

import java.time.Duration;

final class ServerSideEventContextDeduplicator implements EventContextDeduplicator {
  private final SimpleLRUCache<String, String> contextKeys;
  private final Duration flushInterval;
  
  public ServerSideEventContextDeduplicator(
      int capacity,
      Duration flushInterval
      ) {
    this.contextKeys = new SimpleLRUCache<>(capacity);
    this.flushInterval = flushInterval;
  }
  
  @Override
  public Long getFlushInterval() {
    return flushInterval.toMillis();
  }

  @Override
  public boolean processContext(LDContext context) {
    String key = context.getFullyQualifiedKey();
    if (key == null || key.isEmpty()) {
      return false;
    }
    String previousValue = contextKeys.put(key, key);
    return previousValue == null;
  }

  @Override
  public void flush() {
    contextKeys.clear(); 
  }
}
