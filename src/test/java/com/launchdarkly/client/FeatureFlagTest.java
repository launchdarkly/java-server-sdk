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

    FeatureFlag.EvalResult actual = f.evaluate(user, featureStore);

    Assert.assertNull(actual.getValue());
    Assert.assertNotNull(actual.getPrerequisiteEvents());
    Assert.assertEquals(0, actual.getPrerequisiteEvents().size());
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
    Assert.assertNull(f1.evaluate(user, featureStore).getValue());
    Assert.assertNull(f2.evaluate(user, featureStore).getValue());
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
    Assert.assertNull(f1.evaluate(user, featureStore).getValue());
    Assert.assertNull(f2.evaluate(user, featureStore).getValue());
    Assert.assertNull(f3.evaluate(user, featureStore).getValue());
  }

  @Test
  public void testPrereqDoesNotExist() {
    String keyA = "keyA";
    String keyB = "keyB";
    FeatureFlag f1 = newFlagWithPrereq(keyA, keyB);

    featureStore.upsert(f1.getKey(), f1);
    LDUser user = new LDUser.Builder("userKey").build();
    FeatureFlag.EvalResult actual = f1.evaluate(user, featureStore);

    Assert.assertNull(actual.getValue());
    Assert.assertNotNull(actual.getPrerequisiteEvents());
    Assert.assertEquals(0, actual.getPrerequisiteEvents().size());
  }

  @Test
  public void testPrereqCollectsEventsForPrereqs() {
    String keyA = "keyA";
    String keyB = "keyB";
    String keyC = "keyC";
    FeatureFlag flagA = newFlagWithPrereq(keyA, keyB);
    FeatureFlag flagB = newFlagWithPrereq(keyB, keyC);
    FeatureFlag flagC = newFlagOff(keyC);

    featureStore.upsert(flagA.getKey(), flagA);
    featureStore.upsert(flagB.getKey(), flagB);
    featureStore.upsert(flagC.getKey(), flagC);

    LDUser user = new LDUser.Builder("userKey").build();

    FeatureFlag.EvalResult flagAResult = flagA.evaluate(user, featureStore);
    Assert.assertNotNull(flagAResult);
    Assert.assertNull(flagAResult.getValue());
    Assert.assertEquals(2, flagAResult.getPrerequisiteEvents().size());

    FeatureFlag.EvalResult flagBResult = flagB.evaluate(user, featureStore);
    Assert.assertNotNull(flagBResult);
    Assert.assertNull(flagBResult.getValue());
    Assert.assertEquals(1, flagBResult.getPrerequisiteEvents().size());

    FeatureFlag.EvalResult flagCResult = flagC.evaluate(user, featureStore);
    Assert.assertNotNull(flagCResult);
    Assert.assertEquals(new JsonPrimitive(0), flagCResult.getValue());
    Assert.assertEquals(0, flagCResult.getPrerequisiteEvents().size());
  }

  private FeatureFlag newFlagWithPrereq(String featureKey, String prereqKey) {
    return new FeatureFlagBuilder(featureKey)
        .prerequisites(singletonList(new Prerequisite(prereqKey, 0)))
        .variations(Arrays.<JsonElement>asList(new JsonPrimitive(0), new JsonPrimitive(1)))
        .fallthrough(new VariationOrRollout(0, null))
        .on(true)
        .build();
  }

  private FeatureFlag newFlagOff(String featureKey) {
    return new FeatureFlagBuilder(featureKey)
        .variations(Arrays.<JsonElement>asList(new JsonPrimitive(0), new JsonPrimitive(1)))
        .fallthrough(new VariationOrRollout(0, null))
        .on(false)
        .build();
  }
}
