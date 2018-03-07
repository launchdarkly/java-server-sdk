package com.launchdarkly.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;
import static java.util.Collections.singletonList;

public class FeatureFlagTest {

  private FeatureStore featureStore;

  @Before
  public void before() {
    featureStore = new InMemoryFeatureStore();
  }

  @Test
  public void testPrereqDoesNotExist() throws EvaluationException {
    String keyA = "keyA";
    String keyB = "keyB";
    FeatureFlag f1 = newFlagWithPrereq(keyA, keyB);

    featureStore.upsert(FEATURES, f1);
    LDUser user = new LDUser.Builder("userKey").build();
    FeatureFlag.EvalResult actual = f1.evaluate(user, featureStore, EventFactory.DEFAULT);

    Assert.assertNull(actual.getResult().getValue());
    Assert.assertNotNull(actual.getPrerequisiteEvents());
    Assert.assertEquals(0, actual.getPrerequisiteEvents().size());
  }

  @Test
  public void testPrereqCollectsEventsForPrereqs() throws EvaluationException {
    String keyA = "keyA";
    String keyB = "keyB";
    String keyC = "keyC";
    FeatureFlag flagA = newFlagWithPrereq(keyA, keyB);
    FeatureFlag flagB = newFlagWithPrereq(keyB, keyC);
    FeatureFlag flagC = newFlagOff(keyC);

    featureStore.upsert(FEATURES, flagA);
    featureStore.upsert(FEATURES, flagB);
    featureStore.upsert(FEATURES, flagC);

    LDUser user = new LDUser.Builder("userKey").build();

    FeatureFlag.EvalResult flagAResult = flagA.evaluate(user, featureStore, EventFactory.DEFAULT);
    Assert.assertNotNull(flagAResult);
    Assert.assertNull(flagAResult.getResult().getValue());
    Assert.assertEquals(2, flagAResult.getPrerequisiteEvents().size());

    FeatureFlag.EvalResult flagBResult = flagB.evaluate(user, featureStore, EventFactory.DEFAULT);
    Assert.assertNotNull(flagBResult);
    Assert.assertNull(flagBResult.getResult().getValue());
    Assert.assertEquals(1, flagBResult.getPrerequisiteEvents().size());

    FeatureFlag.EvalResult flagCResult = flagC.evaluate(user, featureStore, EventFactory.DEFAULT);
    Assert.assertNotNull(flagCResult);
    Assert.assertNull(null, flagCResult.getResult().getValue());
    Assert.assertEquals(0, flagCResult.getPrerequisiteEvents().size());
  }

  @Test
  public void testSegmentMatchClauseRetrievesSegmentFromStore() throws Exception {
    Segment segment = new Segment.Builder("segkey")
        .included(Arrays.asList("foo"))
        .version(1)
        .build();
    featureStore.upsert(SEGMENTS, segment);
    
    FeatureFlag flag = segmentMatchBooleanFlag("segkey");
    LDUser user = new LDUser.Builder("foo").build();
    
    FeatureFlag.EvalResult result = flag.evaluate(user, featureStore, EventFactory.DEFAULT);
    Assert.assertNotNull(result.getResult());
    Assert.assertEquals(new JsonPrimitive(true), result.getResult().getValue());
  }

  @Test
  public void testSegmentMatchClauseFallsThroughIfSegmentNotFound() throws Exception {
    FeatureFlag flag = segmentMatchBooleanFlag("segkey");
    LDUser user = new LDUser.Builder("foo").build();
    
    FeatureFlag.EvalResult result = flag.evaluate(user, featureStore, EventFactory.DEFAULT);
    Assert.assertNotNull(result.getResult());
    Assert.assertEquals(new JsonPrimitive(false), result.getResult().getValue());
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
  
  private FeatureFlag segmentMatchBooleanFlag(String segmentKey) {
    Clause clause = new Clause("", Operator.segmentMatch, Arrays.asList(new JsonPrimitive(segmentKey)), false);
    Rule rule = new Rule(Arrays.asList(clause), 1, null);
    return new FeatureFlagBuilder("key")
        .variations(Arrays.<JsonElement>asList(new JsonPrimitive(false), new JsonPrimitive(true)))
        .fallthrough(new VariationOrRollout(0, null))
        .on(true)
        .rules(Arrays.asList(rule))
        .build();
  }
}
