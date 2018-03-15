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
    store = new RedisFeatureStoreBuilder(URI.create("redis://localhost:6379"), 10).build();
  }
  
  @Test
  public void handlesUpsertRaceConditionAgainstExternalClient() {
    final Jedis otherClient = new Jedis("localhost");
    final Gson gson = new Gson();
    try {
      final FeatureFlag feature1 = new FeatureFlagBuilder("foo").version(1).build();
      FeatureFlag finalVer = new FeatureFlagBuilder(feature1).version(10).build();
      
      Map<String, FeatureFlag> flags = singletonMap(feature1.getKey(), feature1);
      Map<VersionedDataKind<?>, Map<String, ? extends VersionedData>> allData = new HashMap<>();
      allData.put(FEATURES, flags);
      store.init(allData);
      
      RedisFeatureStore.UpdateListener concurrentModHook = new RedisFeatureStore.UpdateListener() {
        int tries = 0;
        FeatureFlag intermediateVer = feature1;
        
        @Override
        public void aboutToUpdate(String baseKey, String itemKey) {
          if (tries < 3) {
            tries++;
            intermediateVer = new FeatureFlagBuilder(intermediateVer)
                .version(intermediateVer.getVersion() + 1).build();
            otherClient.hset(baseKey, "foo", gson.toJson(intermediateVer));
          }
        }
      };
      store.setUpdateListener(concurrentModHook);
      
      store.upsert(FEATURES, finalVer);
      FeatureFlag result = store.get(FEATURES, feature1.getKey());
      Assert.assertEquals(finalVer.getVersion(), result.getVersion());
    } finally {
      otherClient.close();
    }
  }
}
