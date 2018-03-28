package com.launchdarkly.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import static com.google.common.util.concurrent.Futures.immediateFuture;

interface UpdateProcessor extends Closeable {

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

  static class NullUpdateProcessor implements UpdateProcessor {
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
