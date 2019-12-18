package com.launchdarkly.client;

public class InMemoryDataStoreTest extends DataStoreTestBase<InMemoryDataStore> {

  @Override
  protected InMemoryDataStore makeStore() {
    return new InMemoryDataStore();
  }
}
