package com.launchdarkly.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

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
}
