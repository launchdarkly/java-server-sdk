package com.launchdarkly.client;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * This class exposes advanced configuration options for the {@link com.launchdarkly.client.RedisFeatureStore}.
 *
 * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> that helps construct {@link com.launchdarkly.client.RedisFeatureStore} objects.
 * {@link RedisFeatureStoreBuilder} calls can be chained, enabling the following pattern:
 *
 * <pre>
 * RedisFeatureStoreBuilder builder = new RedisFeatureStoreBuilder("host", 443, 60)
 *      .refreshStaleValues(true)
 *      .asyncRefresh(true)
 *      .socketTimeout(200)
 *      .build()
 * </pre>
 */
public class RedisFeatureStoreBuilder {
    private static final Logger logger = LoggerFactory.getLogger(RedisFeatureStoreBuilder.class);
    protected boolean refreshStaleValues = false;
    protected boolean asyncRefresh = false;
    protected URI uri;
    protected String prefix;
    protected int connectTimeout = Protocol.DEFAULT_TIMEOUT;
    protected int socketTimeout = Protocol.DEFAULT_TIMEOUT;
    protected long cacheTimeSecs;
    protected JedisPoolConfig poolConfig;

    /**
     * The constructor accepts the mandatory fields that must be specified at a minimum to construct a {@link com.launchdarkly.client.RedisFeatureStore}.
     *
     * @param uri the uri of the Redis resource to connect to.
     * @param cacheTimeSecs the cache time in seconds. See {@link RedisFeatureStoreBuilder#cacheTime(long, TimeUnit)} for more information.
     */
    public RedisFeatureStoreBuilder(URI uri, long cacheTimeSecs) {
        this.uri = uri;
        this.cacheTimeSecs = cacheTimeSecs;
    }

    /**
     * The constructor accepts the mandatory fields that must be specified at a minimum to construct a {@link com.launchdarkly.client.RedisFeatureStore}.
     *
     * @param scheme the URI scheme to use
     * @param host the hostname to connect to
     * @param port the port to connect to
     * @param cacheTimeSecs the cache time in seconds. See {@link RedisFeatureStoreBuilder#cacheTime(long, TimeUnit)} for more information.
     * @throws URISyntaxException
     */
    public RedisFeatureStoreBuilder(String scheme, String host, int port, long cacheTimeSecs) throws URISyntaxException {
        this.uri = new URIBuilder().setScheme(scheme).setHost(host).setPort(port).build();
        this.cacheTimeSecs = cacheTimeSecs;
    }

    /**
     * Optionally set the {@link RedisFeatureStore} local cache to refresh stale values instead of evicting them (the default behaviour).
     *
     * When enabled; the cache refreshes stale values instead of completely evicting them. This mode returns the previously cached, stale values if
     * anything goes wrong during the refresh phase (for example a connection timeout). If there was no previously cached value then the store will
     * return null (resulting in the default value being returned). This is useful if you prefer the most recently cached feature rule set to be returned
     * for evaluation over the default value when updates go wrong.
     *
     * When disabled; results in a behaviour which evicts stale values from the local cache and retrieves the latest value from Redis. If the updated value
     * can not be returned for whatever reason then a null is returned (resulting in the default value being returned).
     *
     * This property has no effect if the cache time is set to 0. See {@link RedisFeatureStoreBuilder#cacheTime(long, TimeUnit)} for details.
     *
     * See: <a href="https://github.com/google/guava/wiki/CachesExplained#refresh">CacheBuilder</a> for more specific information on cache semantics.
     *
     * @param enabled turns on lazy refresh of cached values.
     * @return the builder
     */
    public RedisFeatureStoreBuilder refreshStaleValues(boolean enabled) {
        this.refreshStaleValues = enabled;
        return this;
    }

    /**
     * Optionally make cache refresh mode asynchronous. This setting only works if {@link RedisFeatureStoreBuilder#refreshStaleValues(boolean)} has been enabled
     * and has no effect otherwise.
     *
     * Upon hitting a stale value in the local cache; the refresh of the value will be asynchronous which will return the previously cached value in a
     * non-blocking fashion to threads requesting the stale key. This internally will utilize a {@link java.util.concurrent.Executor} to asynchronously
     * refresh the stale value upon the first read request for the stale value in the cache.
     *
     * If there was no previously cached value then the feature store returns null (resulting in the default value being returned). Any exception
     * encountered during the asynchronous reload will simply keep the previously cached value instead.
     *
     * This setting is ideal to enable when you desire high performance reads and can accept returning stale values for the period of the async refresh. For
     * example configuring this feature store with a very low cache time and enabling this feature would see great performance benefit by decoupling calls
     * from network I/O.
     *
     * This property has no effect if the cache time is set to 0. See {@link RedisFeatureStoreBuilder#cacheTime(long, TimeUnit)} for details.
     *
     * @param enabled turns on asychronous refreshes on.
     * @return the builder
     */
    public RedisFeatureStoreBuilder asyncRefresh(boolean enabled) {
        this.asyncRefresh = enabled;
        return this;
    }

    /**
     * Optionally configures the namespace prefix for all keys stored in Redis.
     *
     * @param prefix
     * @return the builder
     */
    public RedisFeatureStoreBuilder prefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    /**
     * A mandatory field which configures the amount of time the store should internally cache the value before being marked invalid.
     *
     * The eviction strategy of stale values is determined by the configuration picked. See {@link RedisFeatureStoreBuilder#refreshStaleValues(boolean)} for
     * more information on stale value updating strategies.
     *
     * If this value is set to 0 then it effectively disables local caching altogether.
     *
     * @param cacheTime the time value to cache for
     * @param timeUnit the time unit for the time value. This is used to convert your time value to seconds.
     * @return the builder
     */
    public RedisFeatureStoreBuilder cacheTime(long cacheTime, TimeUnit timeUnit) {
        this.cacheTimeSecs = timeUnit.toSeconds(cacheTime);
        return this;
    }

    /**
     * Optional override if you wish to specify your own configuration to the underlying Jedis pool.
     *
     * @param poolConfig the Jedis pool configuration.
     * @return the builder
     */
    public RedisFeatureStoreBuilder poolConfig(JedisPoolConfig poolConfig) {
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
    public RedisFeatureStoreBuilder connectTimeout(int connectTimeout, TimeUnit timeUnit) {
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
    public RedisFeatureStoreBuilder socketTimeout(int socketTimeout, TimeUnit timeUnit) {
        this.socketTimeout = (int) timeUnit.toMillis(socketTimeout);
        return this;
    }

    /**
     * Build a {@link RedisFeatureStore} based on the currently configured builder object.
     *
     * @return the {@link RedisFeatureStore} configured by this builder.
     */
    public RedisFeatureStore build() {
        logger.info("Creating RedisFeatureStore with uri: " + uri + " and prefix: " + prefix);
        return new RedisFeatureStore(this);
    }
}
