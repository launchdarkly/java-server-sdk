package com.launchdarkly.sdk.server.subsystems;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

/**
 * Interface for an object that receives updates to feature flags, user segments, and anything
 * else that might come from LaunchDarkly, and passes them to a {@link DataStore}.
 * <p>
 * The standard implementations are:
 * <ul>
 * <li> {@link com.launchdarkly.sdk.server.Components#streamingDataSource()} (the default), which
 * maintains a streaming connection to LaunchDarkly;
 * <li> {@link com.launchdarkly.sdk.server.Components#pollingDataSource()}, which polls for
 * updates at regular intervals;
 * <li> {@link com.launchdarkly.sdk.server.Components#externalUpdatesOnly()}, which does nothing
 * (on the assumption that another process will update the data store);
 * <li> {@link com.launchdarkly.sdk.server.integrations.FileData}, which reads flag data from
 * the filesystem.
 * </ul>
 * 
 * @since 5.0.0
 */
public interface DataSource extends Closeable {
  /**
   * Starts the client.
   * @return {@link Future}'s completion status indicates the client has been initialized.
   */
  Future<Void> start();

  /**
   * Returns true once the client has been initialized and will never return false again.
   * @return true if the client has been initialized
   */
  boolean isInitialized();

  /**
   * Tells the component to shut down and release any resources it is using.
   * @throws IOException if there is an error while closing
   */
  void close() throws IOException;
}
