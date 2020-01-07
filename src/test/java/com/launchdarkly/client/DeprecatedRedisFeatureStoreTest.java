package com.launchdarkly.client;

import org.junit.BeforeClass;

import java.net.URI;

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
}
