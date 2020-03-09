package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.io.IOException;

import static com.launchdarkly.client.ModelBuilders.flagWithValue;
import static com.launchdarkly.client.TestUtil.specificDataStore;
import static com.launchdarkly.client.TestUtil.upsertFlag;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientExternalUpdatesOnlyTest {
  @Test
  public void externalUpdatesOnlyClientHasNullDataSource() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(Components.NullDataSource.class, client.dataSource.getClass());
    }
  }

  @Test
  public void externalUpdatesOnlyClientHasDefaultEventProcessor() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(DefaultEventProcessor.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void externalUpdatesOnlyClientIsInitialized() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertTrue(client.initialized());
    }
  }

  @Test
  public void externalUpdatesOnlyClientGetsFlagFromDataStore() throws IOException {
    DataStore testDataStore = TestUtil.initedDataStore();
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .dataStore(specificDataStore(testDataStore))
        .build();
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(true));
    upsertFlag(testDataStore, flag);
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.boolVariation("key", new LDUser("user"), false));
    }
  }
}