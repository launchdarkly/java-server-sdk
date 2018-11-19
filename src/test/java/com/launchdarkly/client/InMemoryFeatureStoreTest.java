package com.launchdarkly.client;

public class InMemoryFeatureStoreTest extends FeatureStoreTestBase<InMemoryFeatureStore> {

  @Override
  protected InMemoryFeatureStore makeStore() {
    return new InMemoryFeatureStore();
  }
}
