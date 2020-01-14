package com.launchdarkly.client;

import com.google.common.cache.CacheStats;

import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import redis.clients.jedis.Jedis;

@SuppressWarnings({ "javadoc", "deprecation" })
public class DeprecatedRedisFeatureStoreTest extends FeatureStoreDatabaseTestBase<RedisFeatureStore> {

  private static final URI REDIS_URI = URI.create("redis://localhost:6379");
  
  public DeprecatedRedisFeatureStoreTest(boolean cached) {
    super(cached);
  }
  
  @BeforeClass
  public static void maybeSkipDatabaseTests() {
    String skipParam = System.getenv("LD_SKIP_DATABASE_TESTS");
    assumeTrue(skipParam == null || skipParam.equals(""));
  }
  
  @Override
  protected RedisFeatureStore makeStore() {
    RedisFeatureStoreBuilder builder = new RedisFeatureStoreBuilder(REDIS_URI);
    builder.caching(cached ? FeatureStoreCacheConfig.enabled().ttlSeconds(30) : FeatureStoreCacheConfig.disabled());
    return builder.build();
  }
  
  @Override
  protected RedisFeatureStore makeStoreWithPrefix(String prefix) {
    return new RedisFeatureStoreBuilder(REDIS_URI).caching(FeatureStoreCacheConfig.disabled()).prefix(prefix).build();
  }
  
  @Override
  protected void clearAllData() {
    try (Jedis client = new Jedis("localhost")) {
      client.flushDB();
    }
  }
  
  @Test
  public void canGetCacheStats() {
    assumeThat(cached, is(true));
    
    CacheStats stats = store.getCacheStats();
    
    assertThat(stats, equalTo(new CacheStats(0, 0, 0, 0, 0, 0)));
    
    // Cause a cache miss
    store.get(FEATURES, "key1");
    stats = store.getCacheStats();
    assertThat(stats.hitCount(), equalTo(0L));
    assertThat(stats.missCount(), equalTo(1L));
    assertThat(stats.loadSuccessCount(), equalTo(1L)); // even though it's a miss, it's a "success" because there was no exception
    assertThat(stats.loadExceptionCount(), equalTo(0L));
    
    // Cause a cache hit
    store.upsert(FEATURES, new FeatureFlagBuilder("key2").version(1).build()); // inserting the item also caches it
    store.get(FEATURES, "key2"); // now it's a cache hit
    stats = store.getCacheStats();
    assertThat(stats.hitCount(), equalTo(1L));
    assertThat(stats.missCount(), equalTo(1L));
    assertThat(stats.loadSuccessCount(), equalTo(1L));
    assertThat(stats.loadExceptionCount(), equalTo(0L));
    
    // We have no way to force a load exception with a real Redis store
  }
}
