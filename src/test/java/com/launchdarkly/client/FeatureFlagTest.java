package com.launchdarkly.client;


import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static java.util.Collections.singletonList;

public class FeatureFlagTest {

  private FeatureStore featureStore;

  @Before
  public void before() {
    featureStore = new InMemoryFeatureStore();
  }

  @Test
  public void testPrereqSelfCycle() {
    String keyA = "keyA";
    FeatureFlag f = newFlagWithPrereq(keyA, keyA);

    featureStore.upsert(keyA, f);
    LDUser user = new LDUser.Builder("userKey").build();
    Assert.assertNull(f.evaluate(user, featureStore));
  }

  @Test
  public void testPrereqSimpleCycle() {
    String keyA = "keyA";
    String keyB = "keyB";
    FeatureFlag f1 = newFlagWithPrereq(keyA, keyB);
    FeatureFlag f2 = newFlagWithPrereq(keyB, keyA);

    featureStore.upsert(f1.getKey(), f1);
    featureStore.upsert(f2.getKey(), f2);
    LDUser user = new LDUser.Builder("userKey").build();
    Assert.assertNull(f1.evaluate(user, featureStore));
    Assert.assertNull(f2.evaluate(user, featureStore));
  }

  @Test
  public void testPrereqCycle() {
    String keyA = "keyA";
    String keyB = "keyB";
    String keyC = "keyC";
    FeatureFlag f1 = newFlagWithPrereq(keyA, keyB);
    FeatureFlag f2 = newFlagWithPrereq(keyB, keyC);
    FeatureFlag f3 = newFlagWithPrereq(keyC, keyA);

    featureStore.upsert(f1.getKey(), f1);
    featureStore.upsert(f2.getKey(), f2);
    featureStore.upsert(f3.getKey(), f3);
    LDUser user = new LDUser.Builder("userKey").build();
    Assert.assertNull(f1.evaluate(user, featureStore));
    Assert.assertNull(f2.evaluate(user, featureStore));
    Assert.assertNull(f3.evaluate(user, featureStore));
  }

  @Test
  public void testPrereqDoesNotExist() {
    String keyA = "keyA";
    String keyB = "keyB";
    FeatureFlag f1 = newFlagWithPrereq(keyA, keyB);

    featureStore.upsert(f1.getKey(), f1);
    LDUser user = new LDUser.Builder("userKey").build();
    Assert.assertNull(f1.evaluate(user, featureStore));
  }

  private FeatureFlag newFlagWithPrereq(String featureKey, String prereqKey) {
    return new FeatureFlagBuilder(featureKey)
        .prerequisites(singletonList(new Prerequisite(prereqKey, 0)))
        .variations(Arrays.<JsonElement>asList(new JsonPrimitive(0), new JsonPrimitive(1)))
        .build();
  }
}
