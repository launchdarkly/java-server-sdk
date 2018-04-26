package com.launchdarkly.client;

import java.net.URI;

import org.junit.Before;

public class RedisFeatureStoreTest extends FeatureStoreTestBase<RedisFeatureStore> {

  @Before
  public void setup() {
    store = new RedisFeatureStoreBuilder(URI.create("redis://localhost:6379")).build();
  }
}
