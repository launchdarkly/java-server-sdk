package com.launchdarkly.client.integrations;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.FeatureStore;
import com.launchdarkly.client.FeatureStoreDatabaseTestBase;
import com.launchdarkly.client.integrations.RedisDataStoreImpl.UpdateListener;
import com.launchdarkly.client.utils.CachingStoreWrapper;

import org.junit.BeforeClass;

import java.net.URI;

import static org.junit.Assume.assumeTrue;

import redis.clients.jedis.Jedis;

@SuppressWarnings("javadoc")
public class RedisFeatureStoreTest extends FeatureStoreDatabaseTestBase<FeatureStore> {

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
  protected FeatureStore makeStore() {
    RedisDataStoreBuilder redisBuilder = Redis.dataStore().uri(REDIS_URI);
    PersistentDataStoreBuilder builder = Components.persistentDataStore(redisBuilder);
    if (cached) {
      builder.cacheSeconds(30);
    } else {
      builder.noCaching();
    }
    return builder.createFeatureStore();
  }
  
  @Override
  protected FeatureStore makeStoreWithPrefix(String prefix) {
    return Components.persistentDataStore(
        Redis.dataStore().uri(REDIS_URI).prefix(prefix)
        ).noCaching().createFeatureStore();
  }
  
  @Override
  protected void clearAllData() {
    try (Jedis client = new Jedis("localhost")) {
      client.flushDB();
    }
  }
  
  @Override
  protected boolean setUpdateHook(FeatureStore storeUnderTest, final Runnable hook) {
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
