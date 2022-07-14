package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.subsystems.DataStore;

import org.junit.Test;

import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class InMemoryDataStoreTest extends DataStoreTestBase {

  @Override
  protected DataStore makeStore() {
    return new InMemoryDataStore();
  }
  
  @Test
  public void cacheStatsAreNull() {
    assertNull(makeStore().getCacheStats());
  }
}
