package com.launchdarkly.sdk.server.subsystems;

import com.launchdarkly.sdk.server.Components;

/**
 * Interface for a factory that creates some implementation of {@link DataSource}.
 * @see Components
 * @since 4.11.0
 */
public interface DataSourceFactory {
  /**
   * Creates an implementation instance.
   * <p>
   * The new {@code DataSource} should not attempt to make any connections until
   * {@link DataSource#start()} is called.
   * 
   * @param context allows access to the client configuration
   * @param dataSourceUpdates the component pushes data into the SDK via this interface
   * @return an {@link DataSource}
   */
  public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates);
}
