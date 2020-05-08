package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.Components;

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
   * @param dataSourceUpdates the component pushes data into the SDK via this interface
   * @return an {@link DataSource}
   */
  public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates);
}
