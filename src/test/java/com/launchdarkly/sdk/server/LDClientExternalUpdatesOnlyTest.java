package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.DefaultEventProcessor;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataStore;

import org.junit.Test;

import java.io.IOException;

import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.TestUtil.specificDataStore;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
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