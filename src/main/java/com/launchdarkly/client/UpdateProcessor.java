package com.launchdarkly.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import static com.google.common.util.concurrent.Futures.immediateFuture;

/**
 * Interface for an object that receives updates to feature flags, user segments, and anything
 * else that might come from LaunchDarkly, and passes them to a {@link FeatureStore}.
 * @since 4.0.0
 */
public interface UpdateProcessor extends Closeable {
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

  void close() throws IOException;

  static final class NullUpdateProcessor implements UpdateProcessor {
    @Override
    public Future<Void> start() {
      return immediateFuture(null);
    }

    @Override
    public boolean initialized() {
      return true;
    }

    @Override
    public void close() throws IOException {}
  }
}
