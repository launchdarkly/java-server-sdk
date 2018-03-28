package com.launchdarkly.client;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LDClientLddModeTest {
  @Test
  public void lddModeClientHasNullUpdateProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(UpdateProcessor.NullUpdateProcessor.class, client.updateProcessor.getClass());
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
  public void lddModeClientGetsFlagFromFeatureStore() throws IOException {
    TestFeatureStore testFeatureStore = new TestFeatureStore();
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .featureStore(testFeatureStore)
        .build();
    testFeatureStore.setFeatureTrue("key");
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.boolVariation("key", new LDUser("user"), false));
    }
  }
}