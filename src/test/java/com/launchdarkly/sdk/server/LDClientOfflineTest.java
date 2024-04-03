package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
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
public class LDClientOfflineTest extends BaseTest {
  private static final LDContext user = LDContext.create("user");
  
  @Test
  public void offlineClientHasNullDataSource() throws IOException {
    LDConfig config = baseConfig()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(ComponentsImpl.NullDataSource.class, client.dataSource.getClass());
    }
  }

  @Test
  public void offlineClientHasNoOpEventProcessor() throws IOException {
    LDConfig config = baseConfig()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(NoOpEventProcessor.class, client.eventProcessor.getClass());
    }
  }
  
  @Test
  public void offlineClientIsInitialized() throws IOException {
    LDConfig config = baseConfig()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.isInitialized());
      
      assertEquals(DataSourceStatusProvider.State.VALID, client.getDataSourceStatusProvider().getStatus().getState());
    }
  }
  
  @Test
  public void offlineClientReturnsDefaultValue() throws IOException {
    LDConfig config = baseConfig()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals("x", client.stringVariation("key", user, "x"));
    }
  }
  
  @Test
  public void offlineClientGetsFlagsStateFromDataStore() throws IOException {
    DataStore testDataStore = initedDataStore();
    LDConfig config = baseConfig()
        .offline(true)
        .dataStore(specificComponent(testDataStore))
        .build();
    upsertFlag(testDataStore, flagWithValue("key", LDValue.of(true)));
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      FeatureFlagsState state = client.allFlagsState(user);
      assertTrue(state.isValid());
      assertEquals(ImmutableMap.<String, LDValue>of("key", LDValue.of(true)), state.toValuesMap());
    }
  }
}