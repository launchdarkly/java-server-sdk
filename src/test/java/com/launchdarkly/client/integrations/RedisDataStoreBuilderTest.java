package com.launchdarkly.client.integrations;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

@SuppressWarnings("javadoc")
public class RedisDataStoreBuilderTest {
  @Test
  public void testDefaultValues() {
    RedisDataStoreBuilder conf = Redis.dataStore();
    assertEquals(RedisDataStoreBuilder.DEFAULT_URI, conf.uri);
    assertNull(conf.database);
    assertNull(conf.password);
    assertFalse(conf.tls);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.connectTimeout);
    assertEquals(Protocol.DEFAULT_TIMEOUT, conf.socketTimeout);
    assertEquals(RedisDataStoreBuilder.DEFAULT_PREFIX, conf.prefix);
    assertNull(conf.poolConfig);
  }

  @Test
  public void testUriConfigured() {
    URI uri = URI.create("redis://other:9999");
    RedisDataStoreBuilder conf = Redis.dataStore().uri(uri);
    assertEquals(uri, conf.uri);
  }
  
  @Test
  public void testDatabaseConfigured() {
    RedisDataStoreBuilder conf = Redis.dataStore().database(3);
    assertEquals(new Integer(3), conf.database);
  }
  
  @Test
  public void testPasswordConfigured() {
    RedisDataStoreBuilder conf = Redis.dataStore().password("secret");
    assertEquals("secret", conf.password);
  }

  @Test
  public void testTlsConfigured() {
    RedisDataStoreBuilder conf = Redis.dataStore().tls(true);
    assertTrue(conf.tls);
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
