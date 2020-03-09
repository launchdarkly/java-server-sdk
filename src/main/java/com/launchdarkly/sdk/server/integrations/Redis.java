package com.launchdarkly.sdk.server.integrations;

/**
 * Integration between the LaunchDarkly SDK and Redis.
 * 
 * @since 4.12.0
 */
public abstract class Redis {
  /**
   * Returns a builder object for creating a Redis-backed data store.
   * <p>
   * This object can be modified with {@link RedisDataStoreBuilder} methods for any desired
   * custom Redis options. Then, pass it to {@link com.launchdarkly.sdk.server.Components#persistentDataStore(com.launchdarkly.sdk.server.interfaces.PersistentDataStoreFactory)}
   * and set any desired caching options. Finally, pass the result to
   * {@link com.launchdarkly.sdk.server.LDConfig.Builder#dataStore(com.launchdarkly.sdk.server.interfaces.DataStoreFactory)}.
   * For example:
   * 
   * <pre><code>
   *     LDConfig config = new LDConfig.Builder()
   *         .dataStore(
   *             Components.persistentDataStore(
   *                 Redis.dataStore().url("redis://my-redis-host")
   *             ).cacheSeconds(15)
   *         )
   *         .build();
   * </code></pre>
   * 
   * @return a data store configuration object
   */
  public static RedisDataStoreBuilder dataStore() {
    return new RedisDataStoreBuilder();
  }
  
  private Redis() {}
}
