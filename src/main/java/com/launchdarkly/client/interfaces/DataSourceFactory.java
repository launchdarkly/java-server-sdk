package com.launchdarkly.client.interfaces;

import com.launchdarkly.client.Components;

/**
 * Interface for a factory that creates some implementation of {@link DataSource}.
 * @see Components
 * @since 4.11.0
 */
public interface DataSourceFactory {
  /**
   * Creates an implementation instance.
   * 
   * @param context allows access to the client configuration
   * @param dataStore the {@link DataStore} to use for storing the latest flag state
   * @return an {@link DataSource}
   */
  public DataSource createDataSource(ClientContext context, DataStore dataStore);
}
