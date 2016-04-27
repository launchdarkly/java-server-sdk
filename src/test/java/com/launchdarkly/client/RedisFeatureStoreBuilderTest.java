package com.launchdarkly.client;

import org.junit.Test;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RedisFeatureStoreBuilderTest {

    @Test
    public void testDefaultValues() throws URISyntaxException {
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder("http", "host", 1234, 1);
        assertEquals(Protocol.DEFAULT_TIMEOUT, conf.connectTimeout);
        assertEquals(Protocol.DEFAULT_TIMEOUT, conf.socketTimeout);
        assertEquals(false, conf.refreshStaleValues);
        assertEquals(false, conf.asyncRefresh);
        assertNull(conf.poolConfig);
    }

    @Test
    public void testMandatoryFields() throws URISyntaxException {
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder("http", "host", 1234, 1);
        assertEquals(new URI("http://host:1234"), conf.uri);
        assertEquals(1, conf.cacheTimeSecs);
    }

    @Test
    public void testMandatoryFieldsWithAlternateConstructor() throws URISyntaxException {
        URI expectedURI = new URI("http://host:1234");
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder(expectedURI, 1);
        assertEquals(expectedURI, conf.uri);
        assertEquals(1, conf.cacheTimeSecs);
    }

    @Test
    public void testRefreshStaleValues() throws URISyntaxException {
        URI expectedURI = new URI("http://host:1234");
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder(expectedURI, 1).refreshStaleValues(true);
        assertEquals(true, conf.refreshStaleValues);
    }

    @Test
    public void testAsyncRefresh() throws URISyntaxException {
        URI expectedURI = new URI("http://host:1234");
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder(expectedURI, 1).asyncRefresh(true);
        assertEquals(true, conf.asyncRefresh);
    }

    @Test
    public void testPrefixConfigured() throws URISyntaxException {
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder("http", "host", 1234, 1).prefix("prefix");
        assertEquals("prefix", conf.prefix);
    }

    @Test
    public void testConnectTimeoutConfigured() throws URISyntaxException {
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder("http", "host", 1234, 1).connectTimeout(1, TimeUnit.SECONDS);
        assertEquals(1000, conf.connectTimeout);
    }

    @Test
    public void testSocketTimeoutConfigured() throws URISyntaxException {
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder("http", "host", 1234, 1).socketTimeout(1, TimeUnit.SECONDS);
        assertEquals(1000, conf.socketTimeout);
    }

    @Test
    public void testCacheTimeConfiguredInSeconds() throws URISyntaxException {
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder("http", "host", 1234, 1).cacheTime(2000, TimeUnit.MILLISECONDS);
        assertEquals(2, conf.cacheTimeSecs);
    }

    @Test
    public void testPoolConfigConfigured() throws URISyntaxException {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        RedisFeatureStoreBuilder conf = new RedisFeatureStoreBuilder("http", "host", 1234, 1).poolConfig(poolConfig);
        assertEquals(poolConfig, conf.poolConfig);
    }
}
