package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.DataStoreFactory;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

/**
 * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> for configuring the Redis-based persistent data store.
 *
 * Obtain an instance of this class by calling {@link Components#redisDataStore()} or {@link Components#redisDataStore(URI)}.
 * Builder calls can be chained, for example:
 *
 * <pre><code>
 * DataeStore store = Components.redisDataStore()
 *      .database(1)
 *      .caching(DataStoreCacheConfig.enabled().ttlSeconds(60))
 *      .build();
 * </code></pre>
 */
public final class RedisDataStoreBuilder implements DataStoreFactory {
  /**
   * The default value for the Redis URI: {@code redis://localhost:6379}
   * @since 4.0.0
   */
  public static final URI DEFAULT_URI = URI.create("redis://localhost:6379");
  
  /**
   * The default value for {@link #prefix(String)}.
   * @since 4.0.0
   */
  public static final String DEFAULT_PREFIX = "launchdarkly";
  
  final URI uri;
  String prefix = DEFAULT_PREFIX;
  int connectTimeout = Protocol.DEFAULT_TIMEOUT;
  int socketTimeout = Protocol.DEFAULT_TIMEOUT;
  Integer database = null;
  String password = null;
  boolean tls = false;
  DataStoreCacheConfig caching = DataStoreCacheConfig.DEFAULT;
  JedisPoolConfig poolConfig = null;

  // These constructors are called only from Components
  RedisDataStoreBuilder() {
    this.uri = DEFAULT_URI;
  }
  
  RedisDataStoreBuilder(URI uri) {
    this.uri = uri;
  }
  
  /**
   * Specifies the database number to use.
   * <p>
   * The database number can also be specified in the Redis URI, in the form {@code redis://host:port/NUMBER}. Any
   * non-null value that you set with {@link #database(Integer)} will override the URI.
   * 
   * @param database the database number, or null to fall back to the URI or the default
   * @return the builder
   * 
   * @since 4.7.0
   */
  public RedisDataStoreBuilder database(Integer database) {
    this.database = database;
    return this;
  }
  
  /**
   * Specifies a password that will be sent to Redis in an AUTH command.
   * <p>
   * It is also possible to include a password in the Redis URI, in the form {@code redis://:PASSWORD@host:port}. Any
   * password that you set with {@link #password(String)} will override the URI.
   * 
   * @param password the password
   * @return the builder
   * 
   * @since 4.7.0
   */
  public RedisDataStoreBuilder password(String password) {
    this.password = password;
    return this;
  }
  
  /**
   * Optionally enables TLS for secure connections to Redis.
   * <p>
   * This is equivalent to specifying a Redis URI that begins with {@code rediss:} rather than {@code redis:}.
   * <p>
   * Note that not all Redis server distributions support TLS.
   * 
   * @param tls true to enable TLS
   * @return the builder
   * 
   * @since 4.7.0
   */
  public RedisDataStoreBuilder tls(boolean tls) {
    this.tls = tls;
    return this;
  }
  
  /**
   * Specifies whether local caching should be enabled and if so, sets the cache properties. Local
   * caching is enabled by default; see {@link DataStoreCacheConfig#DEFAULT}. To disable it, pass
   * {@link DataStoreCacheConfig#disabled()} to this method.
   * 
   * @param caching a {@link DataStoreCacheConfig} object specifying caching parameters
   * @return the builder
   * 
   * @since 4.6.0
   */
  public RedisDataStoreBuilder caching(DataStoreCacheConfig caching) {
    this.caching = caching;
    return this;
  }
  
  /**
   * Optionally configures the namespace prefix for all keys stored in Redis.
   *
   * @param prefix the namespace prefix
   * @return the builder
   */
  public RedisDataStoreBuilder prefix(String prefix) {
    this.prefix = prefix;
    return this;
  }

  /**
   * Optional override if you wish to specify your own configuration to the underlying Jedis pool.
   *
   * @param poolConfig the Jedis pool configuration.
   * @return the builder
   */
  public RedisDataStoreBuilder poolConfig(JedisPoolConfig poolConfig) {
    this.poolConfig = poolConfig;
    return this;
  }

  /**
   * Optional override which sets the connection timeout for the underlying Jedis pool which otherwise defaults to
   * {@link redis.clients.jedis.Protocol#DEFAULT_TIMEOUT}
   *
   * @param connectTimeout the timeout
   * @param timeUnit the time unit for the timeout
   * @return the builder
   */
  public RedisDataStoreBuilder connectTimeout(int connectTimeout, TimeUnit timeUnit) {
    this.connectTimeout = (int) timeUnit.toMillis(connectTimeout);
    return this;
  }

  /**
   * Optional override which sets the connection timeout for the underlying Jedis pool which otherwise defaults to
   * {@link redis.clients.jedis.Protocol#DEFAULT_TIMEOUT}
   *
   * @param socketTimeout the socket timeout
   * @param timeUnit the time unit for the timeout
   * @return the builder
   */
  public RedisDataStoreBuilder socketTimeout(int socketTimeout, TimeUnit timeUnit) {
    this.socketTimeout = (int) timeUnit.toMillis(socketTimeout);
    return this;
  }

  /**
   * Build a {@link RedisDataStore} based on the currently configured builder object.
   * @return the {@link RedisDataStore} configured by this builder.
   */
  public RedisDataStore build() {
    return new RedisDataStore(this);
  }
  
  /**
   * Synonym for {@link #build()}.
   * @return the {@link RedisDataStore} configured by this builder.
   * @since 4.0.0
   */
  public RedisDataStore createDataStore() {
    return build();
  }
}
