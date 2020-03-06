package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.SimpleLRUCache;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class SimpleLRUCacheTest {
  @Test
  public void getReturnsNullForNeverSeenValue() {
    SimpleLRUCache<String, String> cache = new SimpleLRUCache<>(10);
    assertNull(cache.get("a"));
  }
  
  @Test
  public void putReturnsNullForNeverSeenValue() {
    SimpleLRUCache<String, String> cache = new SimpleLRUCache<>(10);
    assertNull(cache.put("a", "1"));
  }
  
  @Test
  public void putReturnsPreviousValueForAlreadySeenValue() {
    SimpleLRUCache<String, String> cache = new SimpleLRUCache<>(10);
    cache.put("a", "1");
    assertEquals("1", cache.put("a", "2"));
  }
  
  @Test
  public void oldestValueIsDiscardedWhenCapacityIsExceeded() {
    SimpleLRUCache<String, String> cache = new SimpleLRUCache<>(2);
    cache.put("a", "1");
    cache.put("b", "2");
    cache.put("c", "3");
    assertEquals("3", cache.get("c"));
    assertEquals("2", cache.get("b"));
    assertNull(cache.get("a"));
  }
  
  @Test
  public void reAddingValueMakesItNewAgain() {
    SimpleLRUCache<String, String> cache = new SimpleLRUCache<>(2);
    cache.put("a", "1");
    cache.put("b", "2");
    cache.put("c", "3");
    cache.put("a", "1");
    assertEquals("3", cache.get("c"));
    assertEquals("1", cache.get("a"));
    assertNull(cache.get("b"));
  }
  
  @Test
  public void zeroLengthCacheTreatsValuesAsNew() {
    SimpleLRUCache<String, String> cache = new SimpleLRUCache<>(0);
    cache.put("a", "1");
    assertNull(cache.put("a", "2"));
  }
}
