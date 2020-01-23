package com.launchdarkly.client.integrations;

import com.launchdarkly.client.Components;
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
    RedisDataStoreBuilder redisBuilder = Redis.dataStore().uri(REDIS_URI);
    PersistentDataStoreBuilder builder = Components.persistentDataStore(redisBuilder);
    if (cached) {
      builder.cacheSeconds(30);
    } else {
      builder.noCaching();
    }
    return builder.createDataStore();
  }
  
  @Override
  protected DataStore makeStoreWithPrefix(String prefix) {
    return Components.persistentDataStore(
        Redis.dataStore().uri(REDIS_URI).prefix(prefix)
        ).noCaching().createDataStore();
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
