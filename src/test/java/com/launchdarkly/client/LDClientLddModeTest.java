package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.io.IOException;

import static com.launchdarkly.client.DataModel.DataKinds.FEATURES;
import static com.launchdarkly.client.ModelBuilders.flagWithValue;
import static com.launchdarkly.client.TestUtil.initedDataStore;
import static com.launchdarkly.client.TestUtil.specificDataStore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientLddModeTest {
  @Test
  public void lddModeClientHasNullDataSource() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(Components.NullDataSource.class, client.dataSource.getClass());
    }
  }

  @Test
  public void lddModeClientHasDefaultEventProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(DefaultEventProcessor.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void lddModeClientIsInitialized() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.initialized());
    }
  }
  
  @Test
  public void lddModeClientGetsFlagFromDataStore() throws IOException {
    DataStore testDataStore = initedDataStore();
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .dataStore(specificDataStore(testDataStore))
        .build();
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(true));
    testDataStore.upsert(FEATURES, flag);
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.boolVariation("key", new LDUser("user"), false));
    }
  }
}