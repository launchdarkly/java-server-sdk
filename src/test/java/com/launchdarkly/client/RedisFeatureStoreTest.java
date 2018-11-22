package com.launchdarkly.client;

import com.launchdarkly.client.RedisFeatureStore.UpdateListener;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import redis.clients.jedis.Jedis;

public class RedisFeatureStoreTest extends FeatureStoreDatabaseTestBase<RedisFeatureStore> {

  private static final URI REDIS_URI = URI.create("redis://localhost:6379");
  
  public RedisFeatureStoreTest(boolean cached) {
    super(cached);
  }
  
  @Override
  protected RedisFeatureStore makeStore() {
    RedisFeatureStoreBuilder builder = new RedisFeatureStoreBuilder(REDIS_URI);
    builder.cacheTime(cached ? 30 : 0, TimeUnit.SECONDS);
    return builder.build();
  }
  
  @Override
  protected RedisFeatureStore makeStoreWithPrefix(String prefix) {
    return new RedisFeatureStoreBuilder(REDIS_URI).cacheTime(0, TimeUnit.SECONDS).prefix(prefix).build();
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
