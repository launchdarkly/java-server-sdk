package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.FeatureStoreFactory;
import com.launchdarkly.client.integrations.CacheMonitor;
import com.launchdarkly.client.integrations.PersistentDataStoreBuilder;
import com.launchdarkly.client.integrations.PersistentDataStoreBuilder.StaleValuesPolicy;

import java.util.concurrent.TimeUnit;

/**
 * Interface for a factory that creates some implementation of a persistent data store.
 * <p>
 * Note that currently this interface contains methods that are duplicates of the methods in
 * {@link PersistentDataStoreBuilder}. This is necessary to preserve backward compatibility with the
 * implementation of persistent data stores in earlier versions of the SDK. The new recommended usage
 * is described in {@link com.launchdarkly.client.Components#persistentDataStore}, and starting in
 * version 5.0 these redundant methods will be removed.
 * 
 * @see com.launchdarkly.client.Components
 * @since 4.11.0
 */
public interface PersistentDataStoreFactory extends FeatureStoreFactory {
  /**
   * Called internally from {@link PersistentDataStoreBuilder}.
   * 
   * @param cacheTime the cache TTL in whatever units you wish
   * @param cacheTimeUnit the time unit
   * @deprecated Calling this method directly on this component is deprecated. See {@link com.launchdarkly.client.Components#persistentDataStore}
   * for the new usage. 
   */
  @Deprecated
  void cacheTime(long cacheTime, TimeUnit cacheTimeUnit);
  
  /**
   * Called internally from {@link PersistentDataStoreBuilder}.
   * 
   * @param staleValuesPolicy a {@link StaleValuesPolicy} constant
   * @deprecated Calling this method directly on this component is deprecated. See {@link com.launchdarkly.client.Components#persistentDataStore}
   * for the new usage. 
   */
  @Deprecated
  void staleValuesPolicy(StaleValuesPolicy staleValuesPolicy);
  
  /**
   * Called internally from {@link PersistentDataStoreBuilder}.
   * 
   * @param cacheMonitor an instance of {@link CacheMonitor}
   * @deprecated Calling this method directly on this component is deprecated. See {@link com.launchdarkly.client.Components#persistentDataStore}
   * for the new usage. 
   */
  @Deprecated
  void cacheMonitor(CacheMonitor cacheMonitor);
}
