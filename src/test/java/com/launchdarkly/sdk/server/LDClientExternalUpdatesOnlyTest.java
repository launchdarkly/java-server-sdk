package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.subsystems.DataStore;

import org.junit.Test;

import java.io.IOException;

import static com.launchdarkly.sdk.server.ModelBuilders.flagWithValue;
import static com.launchdarkly.sdk.server.TestComponents.initedDataStore;
import static com.launchdarkly.sdk.server.TestComponents.specificComponent;
import static com.launchdarkly.sdk.server.TestUtil.upsertFlag;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientExternalUpdatesOnlyTest extends BaseTest {
  @Test
  public void externalUpdatesOnlyClientHasNullDataSource() throws Exception {
    LDConfig config = baseConfig()
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(ComponentsImpl.NullDataSource.class, client.dataSource.getClass());
    }
  }

  @Test
  public void externalUpdatesOnlyClientHasDefaultEventProcessor() throws Exception {
    LDConfig config = baseConfig()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.sendEvents())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(DefaultEventProcessorWrapper.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void externalUpdatesOnlyClientIsInitialized() throws Exception {
    LDConfig config = baseConfig()
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertTrue(client.isInitialized());
      
      assertEquals(DataSourceStatusProvider.State.VALID, client.getDataSourceStatusProvider().getStatus().getState());
    }
  }

  @Test
  public void externalUpdatesOnlyClientGetsFlagFromDataStore() throws IOException {
    DataStore testDataStore = initedDataStore();
    LDConfig config = baseConfig()
        .dataSource(Components.externalUpdatesOnly())
        .dataStore(specificComponent(testDataStore))
        .build();
    DataModel.FeatureFlag flag = flagWithValue("key", LDValue.of(true));
    upsertFlag(testDataStore, flag);
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.boolVariation("key", LDContext.create("user"), false));
    }
  }
}