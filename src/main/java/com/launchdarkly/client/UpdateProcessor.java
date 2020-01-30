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

  /**
   * Tells the component to shut down and release any resources it is using.
   * @throws IOException if there is an error while closing
   */
  void close() throws IOException;

  /**
   * An implementation of {@link UpdateProcessor} that does nothing.
   * 
   * @deprecated Use {@link Components#externalUpdatesOnly()} instead of referring to this implementation class directly. 
   */
  // This was exposed because everything in an interface is public. The SDK itself no longer refers to this class;
  // instead it uses Components.NullUpdateProcessor.
  @Deprecated
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
