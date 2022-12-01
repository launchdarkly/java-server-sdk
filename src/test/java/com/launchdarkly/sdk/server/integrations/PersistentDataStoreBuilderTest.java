package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder.StaleValuesPolicy;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.PersistentDataStore;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.sdk.server.Components.persistentDataStore;
import static com.launchdarkly.sdk.server.integrations.PersistentDataStoreBuilder.DEFAULT_CACHE_TTL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class PersistentDataStoreBuilderTest {
  private static final ComponentConfigurer<PersistentDataStore> factory = context -> null;
  
  @Test
  public void factory() {
    assertSame(factory, persistentDataStore(factory).persistentDataStoreConfigurer);
  }
  
  @Test
  public void cacheTime() {
    assertEquals(DEFAULT_CACHE_TTL, persistentDataStore(factory).cacheTime);
    
    assertEquals(Duration.ofMinutes(3), persistentDataStore(factory).cacheTime(Duration.ofMinutes(3)).cacheTime);

    assertEquals(Duration.ofMillis(3), persistentDataStore(factory).cacheMillis(3).cacheTime);

    assertEquals(Duration.ofSeconds(3), persistentDataStore(factory).cacheSeconds(3).cacheTime);

    assertEquals(DEFAULT_CACHE_TTL,
        persistentDataStore(factory).cacheTime(Duration.ofMinutes(3)).cacheTime(null).cacheTime);

    assertEquals(Duration.ZERO, persistentDataStore(factory).noCaching().cacheTime);
    
    assertEquals(Duration.ofMillis(-1), persistentDataStore(factory).cacheForever().cacheTime);
  }
  
  @Test
  public void staleValuesPolicy() {
    assertEquals(StaleValuesPolicy.EVICT, persistentDataStore(factory).staleValuesPolicy);
    
    assertEquals(StaleValuesPolicy.REFRESH,
        persistentDataStore(factory).staleValuesPolicy(StaleValuesPolicy.REFRESH).staleValuesPolicy);
    
    assertEquals(StaleValuesPolicy.EVICT,
        persistentDataStore(factory).staleValuesPolicy(StaleValuesPolicy.REFRESH).staleValuesPolicy(null).staleValuesPolicy);
  }
  
  @Test
  public void recordCacheStats() {
    assertFalse(persistentDataStore(factory).recordCacheStats);
    
    assertTrue(persistentDataStore(factory).recordCacheStats(true).recordCacheStats);

    assertFalse(persistentDataStore(factory).recordCacheStats(true).recordCacheStats(false).recordCacheStats);
  }
}
