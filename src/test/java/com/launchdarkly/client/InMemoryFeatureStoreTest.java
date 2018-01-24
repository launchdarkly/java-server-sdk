package com.launchdarkly.client;

import org.junit.Before;

public class InMemoryFeatureStoreTest extends FeatureStoreTestBase<InMemoryFeatureStore> {

  @Before
  public void setup() {
    store = new InMemoryFeatureStore();
  }
}
