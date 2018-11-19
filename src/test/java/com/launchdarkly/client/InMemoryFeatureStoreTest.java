package com.launchdarkly.client;

public class InMemoryFeatureStoreTest extends FeatureStoreTestBase<InMemoryFeatureStore> {

  public InMemoryFeatureStoreTest(boolean cached) {
    super(cached);
  }
  
  @Override
  protected InMemoryFeatureStore makeStore() {
    return new InMemoryFeatureStore();
  }
}
