package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.io.IOException;

import static com.launchdarkly.client.TestUtil.flagWithValue;
import static com.launchdarkly.client.TestUtil.initedFeatureStore;
import static com.launchdarkly.client.TestUtil.specificFeatureStore;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDClientExternalUpdatesOnlyTest {
  @Test
  public void externalUpdatesOnlyClientHasNullUpdateProcessor() throws Exception {
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(Components.NullUpdateProcessor.class, client.updateProcessor.getClass());
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
  public void externalUpdatesOnlyClientGetsFlagFromFeatureStore() throws IOException {
    FeatureStore testFeatureStore = initedFeatureStore();
    LDConfig config = new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .dataStore(specificFeatureStore(testFeatureStore))
        .build();
    FeatureFlag flag = flagWithValue("key", LDValue.of(true));
    testFeatureStore.upsert(FEATURES, flag);
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.boolVariation("key", new LDUser("user"), false));
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void lddModeClientHasNullUpdateProcessor() throws IOException {
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(Components.NullUpdateProcessor.class, client.updateProcessor.getClass());
    }
  }

  @Test
  public void lddModeClientHasDefaultEventProcessor() throws IOException {
    @SuppressWarnings("deprecation")
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {    
      assertEquals(DefaultEventProcessor.class, client.eventProcessor.getClass());
    }
  }

  @Test
  public void lddModeClientIsInitialized() throws IOException {
    @SuppressWarnings("deprecation")
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .build();
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.initialized());
    }
  }
  
  @Test
  public void lddModeClientGetsFlagFromFeatureStore() throws IOException {
    FeatureStore testFeatureStore = initedFeatureStore();
    @SuppressWarnings("deprecation")
    LDConfig config = new LDConfig.Builder()
        .useLdd(true)
        .dataStore(specificFeatureStore(testFeatureStore))
        .build();
    FeatureFlag flag = flagWithValue("key", LDValue.of(true));
    testFeatureStore.upsert(FEATURES, flag);
    try (LDClient client = new LDClient("SDK_KEY", config)) {
      assertTrue(client.boolVariation("key", new LDUser("user"), false));
    }
  }
}