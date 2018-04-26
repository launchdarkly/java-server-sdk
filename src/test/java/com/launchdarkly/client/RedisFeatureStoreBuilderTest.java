package com.launchdarkly.client;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

public class RedisFeatureStoreBuilderTest {
  @Test
  public void testDefaultValues() {
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder();
    assertEquals(RedisFeatureStoreBuilder.DEFAULT_URI, conf.uri);
    assertEquals(RedisFeatureStoreBuilder.DEFAULT_CACHE_TIME_SECONDS, conf.cacheTimeSecs);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.connectTimeout);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.socketTimeout);
    assertEquals(false, conf.refreshStaleValues);
    assertEquals(false, conf.asyncRefresh);
    assertEquals(RedisFeatureStoreBuilder.DEFAULT_PREFIX, conf.prefix);
    assertNull(conf.poolConfig);
  }

  @Test
  public void testConstructorSpecifyingUri() {
    URI uri = URI.create("redis://host:1234");
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder(uri);
    assertEquals(uri, conf.uri);
    assertEquals(RedisFeatureStoreBuilder.DEFAULT_CACHE_TIME_SECONDS, conf.cacheTimeSecs);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.connectTimeout);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.socketTimeout);
    assertEquals(false, conf.refreshStaleValues);
    assertEquals(false, conf.asyncRefresh);
    assertEquals(RedisFeatureStoreBuilder.DEFAULT_PREFIX, conf.prefix);
    assertNull(conf.poolConfig);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedUriBuildingConstructor() throws URISyntaxException {
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder("badscheme", "example", 1234, 100);
    assertEquals(URI.create("badscheme://example:1234"), conf.uri);
    assertEquals(100, conf.cacheTimeSecs);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.connectTimeout);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.socketTimeout);
    assertEquals(false, conf.refreshStaleValues);
    assertEquals(false, conf.asyncRefresh);
    assertEquals(RedisFeatureStoreBuilder.DEFAULT_PREFIX, conf.prefix);
    assertNull(conf.poolConfig);
  }

  @Test
  public void testRefreshStaleValues() throws URISyntaxException {
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder().refreshStaleValues(true);
    assertEquals(true, conf.refreshStaleValues);
  }

  @Test
  public void testAsyncRefresh() throws URISyntaxException {
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder().asyncRefresh(true);
    assertEquals(true, conf.asyncRefresh);
  }

  @Test
  public void testPrefixConfigured() throws URISyntaxException {
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder().prefix("prefix");
    assertEquals("prefix", conf.prefix);
  }

  @Test
  public void testConnectTimeoutConfigured() throws URISyntaxException {
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder().connectTimeout(1, TimeUnit.SECONDS);
    assertEquals(1000, conf.connectTimeout);
  }

  @Test
  public void testSocketTimeoutConfigured() throws URISyntaxException {
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder().socketTimeout(1, TimeUnit.SECONDS);
    assertEquals(1000, conf.socketTimeout);
  }

  @Test
  public void testCacheTimeConfiguredInSeconds() throws URISyntaxException {
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder().cacheTime(2000, TimeUnit.MILLISECONDS);
    assertEquals(2, conf.cacheTimeSecs);
  }

  @Test
  public void testPoolConfigConfigured() throws URISyntaxException {
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder().poolConfig(poolConfig);
    assertEquals(poolConfig, conf.poolConfig);
  }
}
