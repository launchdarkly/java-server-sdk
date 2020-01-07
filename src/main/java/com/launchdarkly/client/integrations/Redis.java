package com.launchdarkly.client.integrations;

/**
 * Integration between the LaunchDarkly SDK and Redis.
 * 
 * @since 4.11.0
 */
public abstract class Redis {
  /**
   * Returns a builder object for creating a Redis-backed data store.
   * <p>
   * This object can be modified with {@link RedisDataStoreBuilder} methods for any desired
   * custom settings, before including it in the SDK configuration with
   * {@link com.launchdarkly.client.LDConfig.Builder#dataStore(com.launchdarkly.client.interfaces.DataStoreFactory)}.
   * 
   * @return a data store configuration object
   */
  public static RedisDataStoreBuilder dataStore() {
    return new RedisDataStoreBuilder();
  }
  
  private Redis() {}
}
