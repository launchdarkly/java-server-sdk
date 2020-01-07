package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.DataStore;

@SuppressWarnings("javadoc")
public class InMemoryDataStoreTest extends DataStoreTestBase {

  @Override
  protected DataStore makeStore() {
    return new InMemoryDataStore();
  }
}
