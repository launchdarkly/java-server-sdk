package com.launchdarkly.client.integrations;

import com.launchdarkly.client.integrations.RedisDataStoreImpl.UpdateListener;

import org.junit.BeforeClass;

import java.net.URI;

import static org.junit.Assume.assumeTrue;

import redis.clients.jedis.Jedis;

@SuppressWarnings("javadoc")
public class RedisDataStoreImplTest extends PersistentDataStoreTestBase<RedisDataStoreImpl> {

  private static final URI REDIS_URI = URI.create("redis://localhost:6379");
  
  @BeforeClass
  public static void maybeSkipDatabaseTests() {
    String skipParam = System.getenv("LD_SKIP_DATABASE_TESTS");
    assumeTrue(skipParam == null || skipParam.equals(""));
  }
  
  @Override
  protected RedisDataStoreImpl makeStore() {
    return (RedisDataStoreImpl)Redis.dataStore().uri(REDIS_URI).createPersistentDataStore(null);
  }
  
  @Override
  protected RedisDataStoreImpl makeStoreWithPrefix(String prefix) {
    return (RedisDataStoreImpl)Redis.dataStore().uri(REDIS_URI).prefix(prefix).createPersistentDataStore(null);
  }
  
  @Override
  protected void clearAllData() {
    try (Jedis client = new Jedis("localhost")) {
      client.flushDB();
    }
  }
  
  @Override
  protected boolean setUpdateHook(RedisDataStoreImpl storeUnderTest, final Runnable hook) {
    storeUnderTest.setUpdateListener(new UpdateListener() {
      @Override
      public void aboutToUpdate(String baseKey, String itemKey) {
        hook.run();
      }
    });
    return true;
  }
}
