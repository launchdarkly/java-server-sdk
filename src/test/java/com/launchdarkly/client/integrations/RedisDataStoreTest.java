package com.launchdarkly.client.integrations;

import com.launchdarkly.client.DataStoreCacheConfig;
import com.launchdarkly.client.DataStoreDatabaseTestBase;
import com.launchdarkly.client.integrations.RedisDataStoreImpl.UpdateListener;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.utils.CachingStoreWrapper;

import org.junit.BeforeClass;

import java.net.URI;

import static org.junit.Assume.assumeTrue;

import redis.clients.jedis.Jedis;

@SuppressWarnings("javadoc")
public class RedisDataStoreTest extends DataStoreDatabaseTestBase {

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
  protected DataStore makeStore() {
    RedisDataStoreBuilder builder = Redis.dataStore().uri(REDIS_URI);
    builder.caching(cached ? DataStoreCacheConfig.enabled().ttlSeconds(30) : DataStoreCacheConfig.disabled());
    return builder.createDataStore();
  }
  
  @Override
  protected DataStore makeStoreWithPrefix(String prefix) {
    return Redis.dataStore().uri(REDIS_URI).caching(DataStoreCacheConfig.disabled()).prefix(prefix).createDataStore();
  }
  
  @Override
  protected void clearAllData() {
    try (Jedis client = new Jedis("localhost")) {
      client.flushDB();
    }
  }
  
  @Override
  protected boolean setUpdateHook(DataStore storeUnderTest, final Runnable hook) {
    RedisDataStoreImpl core = (RedisDataStoreImpl)((CachingStoreWrapper)storeUnderTest).getCore();
    core.setUpdateListener(new UpdateListener() {
      @Override
      public void aboutToUpdate(String baseKey, String itemKey) {
        hook.run();
      }
    });
    return true;
  }
}
