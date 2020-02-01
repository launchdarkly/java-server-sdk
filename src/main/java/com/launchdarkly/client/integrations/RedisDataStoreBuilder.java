package com.launchdarkly.client.integrations;

import com.google.common.base.Joiner;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.interfaces.ClientContext;
import com.launchdarkly.client.interfaces.DiagnosticDescription;
import com.launchdarkly.client.interfaces.PersistentDataStore;
import com.launchdarkly.client.interfaces.PersistentDataStoreFactory;
import com.launchdarkly.client.value.LDValue;

import java.net.URI;
import java.time.Duration;

import static com.google.common.base.Preconditions.checkNotNull;

import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

/**
 * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> for configuring the Redis-based persistent data store.
 * <p>
 * Obtain an instance of this class by calling {@link Redis#dataStore()}. After calling its methods
 * to specify any desired custom settings, you can pass it directly into the SDK configuration with
 * {@link com.launchdarkly.client.LDConfig.Builder#dataStore(com.launchdarkly.client.interfaces.DataStoreFactory)}.
 * You do not need to call {@link #createPersistentDataStore(ClientContext)} yourself to build the actual data store; that
 * will be done by the SDK.
 * <p>
 * Builder calls can be chained, for example:
 *
 * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .dataStore(
   *             Components.persistentDataStore(
   *                 Redis.dataStore()
   *                     .url("redis://my-redis-host")
   *                     .database(1)
   *             ).cacheSeconds(15)
   *         )
   *         .build();
 * </code></pre>
 * 
 * @since 4.12.0
 */
public final class RedisDataStoreBuilder implements PersistentDataStoreFactory, DiagnosticDescription {
  /**
   * The default value for the Redis URI: {@code redis://localhost:6379}
   */
  public static final URI DEFAULT_URI = makeDefaultRedisURI();
  
  /**
   * The default value for {@link #prefix(String)}.
   */
  public static final String DEFAULT_PREFIX = "launchdarkly";
  
  URI uri = DEFAULT_URI;
  String prefix = DEFAULT_PREFIX;
  Duration connectTimeout = Duration.ofMillis(Protocol.DEFAULT_TIMEOUT);
  Duration socketTimeout = Duration.ofMillis(Protocol.DEFAULT_TIMEOUT);
  Integer database = null;
  String password = null;
  boolean tls = false;
  JedisPoolConfig poolConfig = null;

  private static URI makeDefaultRedisURI() {
    // This ungainly logic is a workaround for the overly aggressive behavior of the Shadow plugin, which
    // wants to transform any string literal starting with "redis" because the root package of Jedis is
    // "redis".
    return URI.create(Joiner.on("").join("r", "e", "d", "i", "s") + "://localhost:6379");
  }

  // These constructors are called only from Implementations
  RedisDataStoreBuilder() {
  }
  
  /**
   * Specifies the database number to use.
   * <p>
   * The database number can also be specified in the Redis URI, in the form {@code redis://host:port/NUMBER}. Any
   * non-null value that you set with {@link #database(Integer)} will override the URI.
   * 
   * @param database the database number, or null to fall back to the URI or the default
   * @return the builder
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
   */
  public RedisDataStoreBuilder tls(boolean tls) {
    this.tls = tls;
    return this;
  }
  
  /**
   * Specifies a Redis host URI other than {@link #DEFAULT_URI}.
   * 
   * @param redisUri the URI of the Redis host
   * @return the builder
   */
  public RedisDataStoreBuilder uri(URI redisUri) {
    this.uri = checkNotNull(redisUri);
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
   * {@link redis.clients.jedis.Protocol#DEFAULT_TIMEOUT} milliseconds.
   *
   * @param connectTimeout the timeout
   * @return the builder
   */
  public RedisDataStoreBuilder connectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout == null ? Duration.ofMillis(Protocol.DEFAULT_TIMEOUT) : connectTimeout;
    return this;
  }

  /**
   * Optional override which sets the connection timeout for the underlying Jedis pool which otherwise defaults to
   * {@link redis.clients.jedis.Protocol#DEFAULT_TIMEOUT} milliseconds.
   *
   * @param socketTimeout the socket timeout
   * @return the builder
   */
  public RedisDataStoreBuilder socketTimeout(Duration socketTimeout) {
    this.socketTimeout = socketTimeout == null ? Duration.ofMillis(Protocol.DEFAULT_TIMEOUT) : socketTimeout;
    return this;
  }

  /**
   * Called internally by the SDK to create the actual data store instance.
   * @return the data store configured by this builder
   */
  @Override
  public PersistentDataStore createPersistentDataStore(ClientContext context) {
    return new RedisDataStoreImpl(this);
  }

  @Override
  public LDValue describeConfiguration(LDConfig config) {
    return LDValue.of("Redis");
  }
}
