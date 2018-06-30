package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static com.launchdarkly.client.TestUtil.flagWithValue;
import static com.launchdarkly.client.TestUtil.initedFeatureStore;
import static com.launchdarkly.client.TestUtil.jbool;
import static com.launchdarkly.client.TestUtil.specificFeatureStore;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LDClientOfflineTest {
  @Test
  public void offlineClientHasNullUpdateProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(UpdateProcessor.NullUpdateProcessor.class, client.updateProcessor.getClass());
    }
  }

  @Test
  public void offlineClientHasNullEventProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(EventProcessor.NullEventProcessor.class, client.eventProcessor.getClass());
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
      assertEquals("x", client.stringVariation("key", new LDUser("user"), "x"));
    }
  }
  
  @Test
  public void offlineClientGetsAllFlagsFromFeatureStore() throws IOException {
    FeatureStore testFeatureStore = initedFeatureStore();
    LDConfig config = new LDConfig.Builder()
        .offline(true)
        .featureStoreFactory(specificFeatureStore(testFeatureStore))
        .build();
    FeatureFlag flag = flagWithValue("key", jbool(true));
    testFeatureStore.upsert(FEATURES, flag);
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      Map<String, JsonElement> allFlags = client.allFlags(new LDUser("user"));
      assertNotNull(allFlags);
      assertEquals(1, allFlags.size());
      assertEquals(new JsonPrimitive(true), allFlags.get("key"));      
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