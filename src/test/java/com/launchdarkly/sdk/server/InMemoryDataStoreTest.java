package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.InMemoryDataStore;
import com.launchdarkly.sdk.server.interfaces.DataStore;

@SuppressWarnings("javadoc")
public class InMemoryDataStoreTest extends DataStoreTestBase {

  @Override
  protected DataStore makeStore() {
    return new InMemoryDataStore();
  }
}
