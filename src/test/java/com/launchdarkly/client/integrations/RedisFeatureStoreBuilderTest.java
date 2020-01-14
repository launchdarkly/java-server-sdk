package com.launchdarkly.client.integrations;

import com.launchdarkly.client.FeatureStoreCacheConfig;

import org.junit.Test;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

@SuppressWarnings({ "deprecation", "javadoc" })
public class RedisFeatureStoreBuilderTest {
  @Test
  public void testDefaultValues() {
    RedisDataStoreBuilder conf = Redis.dataStore();
    assertEquals(RedisDataStoreBuilder.DEFAULT_URI, conf.uri);
    assertEquals(FeatureStoreCacheConfig.DEFAULT, conf.caching);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.connectTimeout);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.socketTimeout);
    assertEquals(RedisDataStoreBuilder.DEFAULT_PREFIX, conf.prefix);
    assertNull(conf.poolConfig);
  }

  @Test
  public void testPrefixConfigured() throws URISyntaxException {
    RedisDataStoreBuilder conf = Redis.dataStore().prefix("prefix");
    assertEquals("prefix", conf.prefix);
  }

  @Test
  public void testConnectTimeoutConfigured() throws URISyntaxException {
    RedisDataStoreBuilder conf = Redis.dataStore().connectTimeout(1, TimeUnit.SECONDS);
    assertEquals(1000, conf.connectTimeout);
  }

  @Test
  public void testSocketTimeoutConfigured() throws URISyntaxException {
    RedisDataStoreBuilder conf = Redis.dataStore().socketTimeout(1, TimeUnit.SECONDS);
    assertEquals(1000, conf.socketTimeout);
  }

  @Test
  public void testPoolConfigConfigured() throws URISyntaxException {
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    RedisDataStoreBuilder conf = Redis.dataStore().poolConfig(poolConfig);
    assertEquals(poolConfig, conf.poolConfig);
  }
}
