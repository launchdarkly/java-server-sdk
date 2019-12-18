package com.launchdarkly.client;

import com.launchdarkly.client.RedisDataStore.UpdateListener;

import org.junit.Assume;
import org.junit.BeforeClass;

import java.net.URI;

import static org.junit.Assume.assumeTrue;

import redis.clients.jedis.Jedis;

public class RedisDataStoreTest extends DataStoreDatabaseTestBase<RedisDataStore> {

  private static final URI REDIS_URI = URI.create("redis://localhost:6379");
  
  public RedisDataStoreTest(boolean cached) {
    super(cached);
  }
  
  @BeforeClass
  public static void maybeSkipDatabaseTests() {
    String skipParam = System.getenv("LD_SKIP_DATABASE_TESTS");
    assumeTrue(skipParam == null || skipParam.equals(""));
  }
  
  @Override
  protected RedisDataStore makeStore() {
    RedisDataStoreBuilder builder = new RedisDataStoreBuilder(REDIS_URI);
    builder.caching(cached ? DataStoreCacheConfig.enabled().ttlSeconds(30) : DataStoreCacheConfig.disabled());
    return builder.build();
  }
  
  @Override
  protected RedisDataStore makeStoreWithPrefix(String prefix) {
    return new RedisDataStoreBuilder(REDIS_URI).caching(DataStoreCacheConfig.disabled()).prefix(prefix).build();
  }
  
  @Override
  protected void clearAllData() {
    try (Jedis client = new Jedis("localhost")) {
      client.flushDB();
    }
  }
  
  @Override
  protected boolean setUpdateHook(RedisDataStore storeUnderTest, final Runnable hook) {
    storeUnderTest.setUpdateListener(new UpdateListener() {
      @Override
      public void aboutToUpdate(String baseKey, String itemKey) {
        hook.run();
      }
    });
    return true;
  }
}
