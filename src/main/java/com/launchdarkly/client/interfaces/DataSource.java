package com.launchdarkly.client.interfaces;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

/**
 * Interface for an object that receives updates to feature flags, user segments, and anything
 * else that might come from LaunchDarkly, and passes them to a {@link DataStore}.
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
  boolean initialized();

  /**
   * Tells the component to shut down and release any resources it is using.
   * @throws IOException if there is an error while closing
   */
  void close() throws IOException;
}
