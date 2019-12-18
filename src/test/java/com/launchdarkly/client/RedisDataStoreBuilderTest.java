package com.launchdarkly.client;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

@SuppressWarnings("javadoc")
public class RedisDataStoreBuilderTest {
  @Test
  public void testDefaultValues() {
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder();
    assertEquals(RedisDataStoreBuilder.DEFAULT_URI, conf.uri);
    assertEquals(DataStoreCacheConfig.DEFAULT, conf.caching);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.connectTimeout);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.socketTimeout);
    assertEquals(RedisDataStoreBuilder.DEFAULT_PREFIX, conf.prefix);
    assertNull(conf.poolConfig);
  }

  @Test
  public void testConstructorSpecifyingUri() {
    URI uri = URI.create("redis://host:1234");
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder(uri);
    assertEquals(uri, conf.uri);
    assertEquals(DataStoreCacheConfig.DEFAULT, conf.caching);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.connectTimeout);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.socketTimeout);
    assertEquals(RedisDataStoreBuilder.DEFAULT_PREFIX, conf.prefix);
    assertNull(conf.poolConfig);
  }

  @Test
  public void testPrefixConfigured() throws URISyntaxException {
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder().prefix("prefix");
    assertEquals("prefix", conf.prefix);
  }

  @Test
  public void testConnectTimeoutConfigured() throws URISyntaxException {
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder().connectTimeout(1, TimeUnit.SECONDS);
    assertEquals(1000, conf.connectTimeout);
  }

  @Test
  public void testSocketTimeoutConfigured() throws URISyntaxException {
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder().socketTimeout(1, TimeUnit.SECONDS);
    assertEquals(1000, conf.socketTimeout);
  }

  @Test
  public void testPoolConfigConfigured() throws URISyntaxException {
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder().poolConfig(poolConfig);
    assertEquals(poolConfig, conf.poolConfig);
  }
}
