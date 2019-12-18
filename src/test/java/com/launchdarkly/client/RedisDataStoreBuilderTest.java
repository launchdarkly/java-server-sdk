package com.launchdarkly.client;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

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

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedUriBuildingConstructor() throws URISyntaxException {
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder("badscheme", "example", 1234, 100);
    assertEquals(URI.create("badscheme://example:1234"), conf.uri);
    assertEquals(100, conf.caching.getCacheTime());
    assertEquals(TimeUnit.SECONDS, conf.caching.getCacheTimeUnit());
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.connectTimeout);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.socketTimeout);
    assertEquals(DataStoreCacheConfig.StaleValuesPolicy.EVICT, conf.caching.getStaleValuesPolicy());
    assertEquals(RedisDataStoreBuilder.DEFAULT_PREFIX, conf.prefix);
    assertNull(conf.poolConfig);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testRefreshStaleValues() throws URISyntaxException {
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder().refreshStaleValues(true);
    assertEquals(DataStoreCacheConfig.StaleValuesPolicy.REFRESH, conf.caching.getStaleValuesPolicy());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testAsyncRefresh() throws URISyntaxException {
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder().refreshStaleValues(true).asyncRefresh(true);
    assertEquals(DataStoreCacheConfig.StaleValuesPolicy.REFRESH_ASYNC, conf.caching.getStaleValuesPolicy());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testRefreshStaleValuesWithoutAsyncRefresh() throws URISyntaxException {
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder().asyncRefresh(true);
    assertEquals(DataStoreCacheConfig.StaleValuesPolicy.EVICT, conf.caching.getStaleValuesPolicy());
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

  @SuppressWarnings("deprecation")
  @Test
  public void testCacheTimeWithUnit() throws URISyntaxException {
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder().cacheTime(2000, TimeUnit.MILLISECONDS);
    assertEquals(2000, conf.caching.getCacheTime());
    assertEquals(TimeUnit.MILLISECONDS, conf.caching.getCacheTimeUnit());
  }

  @Test
  public void testPoolConfigConfigured() throws URISyntaxException {
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    RedisDataStoreBuilder conf = new RedisDataStoreBuilder().poolConfig(poolConfig);
    assertEquals(poolConfig, conf.poolConfig);
  }
}
