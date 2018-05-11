package com.launchdarkly.client;

import com.google.gson.Gson;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static java.util.Collections.singletonMap;

import redis.clients.jedis.Jedis;

public class RedisFeatureStoreTest extends FeatureStoreTestBase<RedisFeatureStore> {

  @Before
  public void setup() {
    store = new RedisFeatureStoreBuilder(URI.create("redis://localhost:6379")).build();
  }
  
  @Test
  public void handlesUpsertRaceConditionAgainstExternalClientWithLowerVersion() {
    final Jedis otherClient = new Jedis("localhost");
    try {
      final FeatureFlag flag = new FeatureFlagBuilder("foo").version(1).build();
      initStoreWithSingleFeature(store, flag);
      
      store.setUpdateListener(makeConcurrentModifier(otherClient, flag, 2, 4));
      
      FeatureFlag myVer = new FeatureFlagBuilder(flag).version(10).build();
      store.upsert(FEATURES, myVer);
      FeatureFlag result = store.get(FEATURES, feature1.getKey());
      Assert.assertEquals(myVer.getVersion(), result.getVersion());
    } finally {
      otherClient.close();
    }
  }
  
  @Test
  public void handlesUpsertRaceConditionAgainstExternalClientWithHigherVersion() {
    final Jedis otherClient = new Jedis("localhost");
    try {
      final FeatureFlag flag = new FeatureFlagBuilder("foo").version(1).build();
      initStoreWithSingleFeature(store, flag);
      
      store.setUpdateListener(makeConcurrentModifier(otherClient, flag, 3, 3));
      
      FeatureFlag myVer = new FeatureFlagBuilder(flag).version(2).build();
      store.upsert(FEATURES, myVer);
      FeatureFlag result = store.get(FEATURES, feature1.getKey());
      Assert.assertEquals(3, result.getVersion());
    } finally {
      otherClient.close();
    }
  }
  
  private void initStoreWithSingleFeature(RedisFeatureStore store, FeatureFlag flag) {
    Map<String, FeatureFlag> flags = singletonMap(flag.getKey(), flag);
    Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData = new HashMap<>();
    allData.put(FEATURES, flags);
    store.init(allData);
  }
  
  private RedisFeatureStore.UpdateListener makeConcurrentModifier(final Jedis otherClient, final FeatureFlag flag,
    final int startVersion, final int endVersion) {
    final Gson gson = new Gson();
    return new RedisFeatureStore.UpdateListener() {
      int versionCounter = startVersion;
      @Override
      public void aboutToUpdate(String baseKey, String itemKey) {
        if (versionCounter <= endVersion) {
          FeatureFlag newVer = new FeatureFlagBuilder(flag).version(versionCounter).build();
          versionCounter++;
          otherClient.hset(baseKey, flag.getKey(), gson.toJson(newVer));
        }
      }
    };
  }
}
