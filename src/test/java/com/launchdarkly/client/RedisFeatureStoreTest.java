package com.launchdarkly.client;

import com.launchdarkly.client.RedisFeatureStore.UpdateListener;

import org.junit.Assume;
import org.junit.BeforeClass;

import java.net.URI;

import static org.junit.Assume.assumeTrue;

import redis.clients.jedis.Jedis;

public class RedisFeatureStoreTest extends FeatureStoreDatabaseTestBase<RedisFeatureStore> {

  private static final URI REDIS_URI = URI.create("redis://localhost:6379");
  
  public RedisFeatureStoreTest(boolean cached) {
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
  
  @Override
  protected boolean setUpdateHook(RedisFeatureStore storeUnderTest, final Runnable hook) {
    storeUnderTest.setUpdateListener(new UpdateListener() {
      @Override
      public void aboutToUpdate(String baseKey, String itemKey) {
        hook.run();
      }
    });
    return true;
  }
}
