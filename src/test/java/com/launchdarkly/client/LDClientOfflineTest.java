package com.launchdarkly.client;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.launchdarkly.client.interfaces.DataStore;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static com.launchdarkly.client.DataModel.DataKinds.FEATURES;
import static com.launchdarkly.client.ModelBuilders.flagWithValue;
import static com.launchdarkly.client.TestUtil.initedDataStore;
import static com.launchdarkly.client.TestUtil.jbool;
import static com.launchdarkly.client.TestUtil.specificDataStore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientOfflineTest {
  private static final LDUser user = new LDUser("user");
  
  @Test
  public void offlineClientHasNullDataSource() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(Components.NullDataSource.class, client.dataSource.getClass());
    }
  }

  @Test
  public void offlineClientHasNullEventProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(Components.NullEventProcessor.class, client.eventProcessor.getClass());
    }
  }
  
  @Test
  public void offlineClientIsInitialized() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.initialized());
    }
  }
  
  @Test
  public void offlineClientReturnsDefaultValue() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertEquals("x", client.stringVariation("key", user, "x"));
    }
  }
  
  @Test
  public void offlineClientGetsAllFlagsFromDataStore() throws IOException {
    DataStore testDataStore = initedDataStore();
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .dataStore(specificDataStore(testDataStore))
        .build();
    testDataStore.upsert(FEATURES, flagWithValue("key", LDValue.of(true)));
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      Map<String, JsonElement> allFlags = client.allFlags(user);
      assertEquals(ImmutableMap.<String, JsonElement>of("key", jbool(true)), allFlags);
    }
  }

  @Test
  public void offlineClientGetsFlagsStateFromDataStore() throws IOException {
    DataStore testDataStore = initedDataStore();
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .dataStore(specificDataStore(testDataStore))
        .build();
    testDataStore.upsert(FEATURES, flagWithValue("key", LDValue.of(true)));
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      FeatureFlagsState state = client.allFlagsState(user);
      assertTrue(state.isValid());
      assertEquals(ImmutableMap.<String, JsonElement>of("key", jbool(true)), state.toValuesMap());
    }
  }
  
  @Test
  public void testSecureModeHash() throws IOException {
    LDConfig config = new LDConfig.Builder()
            .offline(true)
            .build();
    try (LDClientInterface client = new LDClient("secret", config)) {
      LDUser user = new LDUser.Builder("Message").build();
      assertEquals("aa747c502a898200f9e4fa21bac68136f886a0e27aec70ba06daf2e2a5cb5597", client.secureModeHash(user));
    }
  }
}