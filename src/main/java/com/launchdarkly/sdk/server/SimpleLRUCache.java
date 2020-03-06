package com.launchdarkly.sdk.server;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A very basic implementation of a LRU cache with a fixed capacity.  Note that in this
 * implementation, entries only become new again when written, not when read.
 * See: http://chriswu.me/blog/a-lru-cache-in-10-lines-of-java/
 */
@SuppressWarnings("serial")
class SimpleLRUCache<K, V> extends LinkedHashMap<K, V> {
  private final int capacity;
  
  SimpleLRUCache(int capacity) {
    super(16, 0.75f, true);
    this.capacity = capacity;
  }
  
  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > capacity;
  }
}
