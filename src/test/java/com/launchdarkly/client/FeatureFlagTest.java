package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.launchdarkly.client.value.LDValue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.client.EvaluationDetail.fromValue;
import static com.launchdarkly.client.TestUtil.TEST_GSON_INSTANCE;
import static com.launchdarkly.client.TestUtil.booleanFlagWithClauses;
import static com.launchdarkly.client.TestUtil.fallthroughVariation;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class FeatureFlagTest {

  private static LDUser BASE_USER = new LDUser.Builder("x").build();
  
  private FeatureStore featureStore;

  @Before
  public void before() {
    featureStore = new InMemoryFeatureStore();
  }

  @Test
  public void flagReturnsOffVariationIfFlagIsOff() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(false)
        .offVariation(1)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("off"), 1, EvaluationReason.off()), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void flagReturnsNullIfFlagIsOffAndOffVariationIsUnspecified() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(false)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.ofNull(), null, EvaluationReason.off()), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void flagReturnsErrorIfFlagIsOffAndOffVariationIsTooHigh() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(false)
        .offVariation(999)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void flagReturnsErrorIfFlagIsOffAndOffVariationIsNegative() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(false)
        .offVariation(-1)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void flagReturnsFallthroughIfFlagIsOnAndThereAreNoRules() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("fall"), 0, EvaluationReason.fallthrough()), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasTooHighVariation() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(fallthroughVariation(999))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasNegativeVariation() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(fallthroughVariation(-1))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasNeitherVariationNorRollout() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(new VariationOrRollout(null, null))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void flagReturnsErrorIfFallthroughHasEmptyRolloutVariationList() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(new VariationOrRollout(null,
            new VariationOrRollout.Rollout(ImmutableList.<VariationOrRollout.WeightedVariation>of(), null)))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void flagReturnsOffVariationIfPrerequisiteIsNotFound() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result = f0.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(fromValue(LDValue.of("off"), 1, expectedReason), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void flagReturnsOffVariationAndEventIfPrerequisiteIsOff() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    FeatureFlag f1 = new FeatureFlagBuilder("feature1")
        .on(false)
        .offVariation(1)
        // note that even though it returns the desired variation, it is still off and therefore not a match
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    featureStore.upsert(FEATURES, f1);        
    FeatureFlag.EvalResult result = f0.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(fromValue(LDValue.of("off"), 1, expectedReason), result.getDetails());
    
    assertEquals(1, result.getPrerequisiteEvents().size());
    Event.FeatureRequest event = result.getPrerequisiteEvents().get(0);
    assertEquals(f1.getKey(), event.key);
    assertEquals(LDValue.of("go"), event.value);
    assertEquals(f1.getVersion(), event.version.intValue());
    assertEquals(f0.getKey(), event.prereqOf);
  }

  @Test
  public void flagReturnsOffVariationAndEventIfPrerequisiteIsNotMet() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    FeatureFlag f1 = new FeatureFlagBuilder("feature1")
        .on(true)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    featureStore.upsert(FEATURES, f1);        
    FeatureFlag.EvalResult result = f0.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(fromValue(LDValue.of("off"), 1, expectedReason), result.getDetails());
    
    assertEquals(1, result.getPrerequisiteEvents().size());
    Event.FeatureRequest event = result.getPrerequisiteEvents().get(0);
    assertEquals(f1.getKey(), event.key);
    assertEquals(LDValue.of("nogo"), event.value);
    assertEquals(f1.getVersion(), event.version.intValue());
    assertEquals(f0.getKey(), event.prereqOf);
  }

  @Test
  public void prerequisiteFailedReasonInstanceIsReusedForSamePrerequisite() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    FeatureFlag.EvalResult result0 = f0.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    FeatureFlag.EvalResult result1 = f0.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(expectedReason, result0.getDetails().getReason());
    assertSame(result0.getDetails().getReason(), result1.getDetails().getReason());
  }

  @Test
  public void flagReturnsFallthroughVariationAndEventIfPrerequisiteIsMetAndThereAreNoRules() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    FeatureFlag f1 = new FeatureFlagBuilder("feature1")
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    featureStore.upsert(FEATURES, f1);        
    FeatureFlag.EvalResult result = f0.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("fall"), 0, EvaluationReason.fallthrough()), result.getDetails());
    assertEquals(1, result.getPrerequisiteEvents().size());
    
    Event.FeatureRequest event = result.getPrerequisiteEvents().get(0);
    assertEquals(f1.getKey(), event.key);
    assertEquals(LDValue.of("go"), event.value);
    assertEquals(f1.getVersion(), event.version.intValue());
    assertEquals(f0.getKey(), event.prereqOf);
  }

  @Test
  public void multipleLevelsOfPrerequisitesProduceMultipleEvents() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    FeatureFlag f1 = new FeatureFlagBuilder("feature1")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature2", 1)))
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    FeatureFlag f2 = new FeatureFlagBuilder("feature2")
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(3)
        .build();
    featureStore.upsert(FEATURES, f1);        
    featureStore.upsert(FEATURES, f2);        
    FeatureFlag.EvalResult result = f0.evaluate(BASE_USER, featureStore, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("fall"), 0, EvaluationReason.fallthrough()), result.getDetails());
    assertEquals(2, result.getPrerequisiteEvents().size());
    
    Event.FeatureRequest event0 = result.getPrerequisiteEvents().get(0);
    assertEquals(f2.getKey(), event0.key);
    assertEquals(LDValue.of("go"), event0.value);
    assertEquals(f2.getVersion(), event0.version.intValue());
    assertEquals(f1.getKey(), event0.prereqOf);

    Event.FeatureRequest event1 = result.getPrerequisiteEvents().get(1);
    assertEquals(f1.getKey(), event1.key);
    assertEquals(LDValue.of("go"), event1.value);
    assertEquals(f1.getVersion(), event1.version.intValue());
    assertEquals(f0.getKey(), event1.prereqOf);
  }
  
  @Test
  public void flagMatchesUserFromTargets() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(true)
        .targets(Arrays.asList(new Target(ImmutableSet.of("whoever", "userkey"), 2)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    LDUser user = new LDUser.Builder("userkey").build();
    FeatureFlag.EvalResult result = f.evaluate(user, featureStore, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("on"), 2, EvaluationReason.targetMatch()), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void flagMatchesUserFromRules() {
    Clause clause0 = new Clause("key", Operator.in, Arrays.asList(LDValue.of("wrongkey")), false);
    Clause clause1 = new Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    Rule rule0 = new Rule("ruleid0", Arrays.asList(clause0), 2, null);
    Rule rule1 = new Rule("ruleid1", Arrays.asList(clause1), 2, null);
    FeatureFlag f = featureFlagWithRules("feature", rule0, rule1);
    LDUser user = new LDUser.Builder("userkey").build();
    FeatureFlag.EvalResult result = f.evaluate(user, featureStore, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("on"), 2, EvaluationReason.ruleMatch(1, "ruleid1")), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void ruleMatchReasonInstanceIsReusedForSameRule() {
    Clause clause0 = new Clause("key", Operator.in, Arrays.asList(LDValue.of("wrongkey")), false);
    Clause clause1 = new Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    Rule rule0 = new Rule("ruleid0", Arrays.asList(clause0), 2, null);
    Rule rule1 = new Rule("ruleid1", Arrays.asList(clause1), 2, null);
    FeatureFlag f = featureFlagWithRules("feature", rule0, rule1);
    LDUser user = new LDUser.Builder("userkey").build();
    LDUser otherUser = new LDUser.Builder("wrongkey").build();

    FeatureFlag.EvalResult sameResult0 = f.evaluate(user, featureStore, EventFactory.DEFAULT);
    FeatureFlag.EvalResult sameResult1 = f.evaluate(user, featureStore, EventFactory.DEFAULT);
    FeatureFlag.EvalResult otherResult = f.evaluate(otherUser, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationReason.ruleMatch(1, "ruleid1"), sameResult0.getDetails().getReason());
    assertSame(sameResult0.getDetails().getReason(), sameResult1.getDetails().getReason());
    
    assertEquals(EvaluationReason.ruleMatch(0, "ruleid0"), otherResult.getDetails().getReason());
  }
  
  @Test
  public void ruleWithTooHighVariationReturnsMalformedFlagError() {
    Clause clause = new Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    Rule rule = new Rule("ruleid", Arrays.asList(clause), 999, null);
    FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    FeatureFlag.EvalResult result = f.evaluate(user, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void ruleWithNegativeVariationReturnsMalformedFlagError() {
    Clause clause = new Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    Rule rule = new Rule("ruleid", Arrays.asList(clause), -1, null);
    FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    FeatureFlag.EvalResult result = f.evaluate(user, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void ruleWithNoVariationOrRolloutReturnsMalformedFlagError() {
    Clause clause = new Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    Rule rule = new Rule("ruleid", Arrays.asList(clause), null, null);
    FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    FeatureFlag.EvalResult result = f.evaluate(user, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void ruleWithRolloutWithEmptyVariationsListReturnsMalformedFlagError() {
    Clause clause = new Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    Rule rule = new Rule("ruleid", Arrays.asList(clause), null,
        new VariationOrRollout.Rollout(ImmutableList.<VariationOrRollout.WeightedVariation>of(), null));
    FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    FeatureFlag.EvalResult result = f.evaluate(user, featureStore, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void clauseCanMatchBuiltInAttribute() throws Exception {
    Clause clause = new Clause("name", Operator.in, Arrays.asList(LDValue.of("Bob")), false);
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(LDValue.of(true), f.evaluate(user, featureStore, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseCanMatchCustomAttribute() throws Exception {
    Clause clause = new Clause("legs", Operator.in, Arrays.asList(LDValue.of(4)), false);
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").custom("legs", 4).build();
    
    assertEquals(LDValue.of(true), f.evaluate(user, featureStore, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseReturnsFalseForMissingAttribute() throws Exception {
    Clause clause = new Clause("legs", Operator.in, Arrays.asList(LDValue.of(4)), false);
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(LDValue.of(false), f.evaluate(user, featureStore, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseCanBeNegated() throws Exception {
    Clause clause = new Clause("name", Operator.in, Arrays.asList(LDValue.of("Bob")), true);
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(LDValue.of(false), f.evaluate(user, featureStore, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseWithUnsupportedOperatorStringIsUnmarshalledWithNullOperator() throws Exception {
    // This just verifies that GSON will give us a null in this case instead of throwing an exception,
    // so we fail as gracefully as possible if a new operator type has been added in the application
    // and the SDK hasn't been upgraded yet.
    String badClauseJson = "{\"attribute\":\"name\",\"operator\":\"doesSomethingUnsupported\",\"values\":[\"x\"]}";
    Gson gson = new Gson();
    Clause clause = gson.fromJson(badClauseJson, Clause.class);
    assertNotNull(clause);
    
    JsonElement json = gson.toJsonTree(clause);
    String expectedJson = "{\"attribute\":\"name\",\"values\":[\"x\"],\"negate\":false}";
    assertEquals(gson.fromJson(expectedJson, JsonElement.class), json);
  }
  
  @Test
  public void clauseWithNullOperatorDoesNotMatch() throws Exception {
    Clause badClause = new Clause("name", null, Arrays.asList(LDValue.of("Bob")), false);
    FeatureFlag f = booleanFlagWithClauses("flag", badClause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(LDValue.of(false), f.evaluate(user, featureStore, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseWithNullOperatorDoesNotStopSubsequentRuleFromMatching() throws Exception {
    Clause badClause = new Clause("name", null, Arrays.asList(LDValue.of("Bob")), false);
    Rule badRule = new Rule("rule1", Arrays.asList(badClause), 1, null);
    Clause goodClause = new Clause("name", Operator.in, Arrays.asList(LDValue.of("Bob")), false);
    Rule goodRule = new Rule("rule2", Arrays.asList(goodClause), 1, null);
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(true)
        .rules(Arrays.asList(badRule, goodRule))
        .fallthrough(fallthroughVariation(0))
        .offVariation(0)
        .variations(LDValue.of(false), LDValue.of(true))
        .build();
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    EvaluationDetail<LDValue> details = f.evaluate(user, featureStore, EventFactory.DEFAULT).getDetails();
    assertEquals(fromValue(LDValue.of(true), 1, EvaluationReason.ruleMatch(1, "rule2")), details);
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
    assertEquals(LDValue.of(true), result.getDetails().getValue());
  }

  @Test
  public void testSegmentMatchClauseFallsThroughIfSegmentNotFound() throws Exception {
    FeatureFlag flag = segmentMatchBooleanFlag("segkey");
    LDUser user = new LDUser.Builder("foo").build();
    
    FeatureFlag.EvalResult result = flag.evaluate(user, featureStore, EventFactory.DEFAULT);
    assertEquals(LDValue.of(false), result.getDetails().getValue());
  }

  @Test
  public void flagIsDeserializedWithAllProperties() {
    String json = flagWithAllPropertiesJson().toJsonString();
    FeatureFlag flag0 = TEST_GSON_INSTANCE.fromJson(json, FeatureFlag.class);
    assertFlagHasAllProperties(flag0);
    
    FeatureFlag flag1 = TEST_GSON_INSTANCE.fromJson(TEST_GSON_INSTANCE.toJson(flag0), FeatureFlag.class);
    assertFlagHasAllProperties(flag1);
  }
  
  @Test
  public void flagIsDeserializedWithMinimalProperties() {
    String json = LDValue.buildObject().put("key", "flag-key").put("version", 99).build().toJsonString();
    FeatureFlag flag = TEST_GSON_INSTANCE.fromJson(json, FeatureFlag.class);
    assertEquals("flag-key", flag.getKey());
    assertEquals(99, flag.getVersion());
    assertFalse(flag.isOn());
    assertNull(flag.getSalt());    
    assertNull(flag.getTargets());
    assertNull(flag.getRules());    
    assertNull(flag.getFallthrough());
    assertNull(flag.getOffVariation());
    assertNull(flag.getVariations());
    assertFalse(flag.isClientSide());
    assertFalse(flag.isTrackEvents());
    assertFalse(flag.isTrackEventsFallthrough());
    assertNull(flag.getDebugEventsUntilDate());
  }
  
  private FeatureFlag featureFlagWithRules(String flagKey, Rule... rules) {
    return new FeatureFlagBuilder(flagKey)
        .on(true)
        .rules(Arrays.asList(rules))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
  }
  
  private FeatureFlag segmentMatchBooleanFlag(String segmentKey) {
    Clause clause = new Clause("", Operator.segmentMatch, Arrays.asList(LDValue.of(segmentKey)), false);
    return booleanFlagWithClauses("flag", clause);
  }
  
  private LDValue flagWithAllPropertiesJson() {
    return LDValue.buildObject()
        .put("key", "flag-key")
        .put("version", 99)
        .put("on", true)
        .put("prerequisites", LDValue.buildArray()
            .build())
        .put("salt", "123")
        .put("targets", LDValue.buildArray()
            .add(LDValue.buildObject()
                .put("variation", 1)
                .put("values", LDValue.buildArray().add("key1").add("key2").build())
                .build())
            .build())
        .put("rules", LDValue.buildArray()
            .add(LDValue.buildObject()
                .put("id", "id0")
                .put("trackEvents", true)
                .put("variation", 2)
                .put("clauses", LDValue.buildArray()
                    .add(LDValue.buildObject()
                        .put("attribute", "name")
                        .put("op", "in")
                        .put("values", LDValue.buildArray().add("Lucy").build())
                        .put("negate", true)
                        .build())
                    .build())
                .build())
            .add(LDValue.buildObject()
                .put("id", "id1")
                .put("rollout", LDValue.buildObject()
                    .put("variations", LDValue.buildArray()
                        .add(LDValue.buildObject()
                            .put("variation", 2)
                            .put("weight", 100000)
                            .build())
                        .build())
                    .put("bucketBy", "email")
                    .build())
                .build())
            .build())
        .put("fallthrough", LDValue.buildObject()
            .put("variation", 1)
            .build())
        .put("offVariation", 2)
        .put("variations", LDValue.buildArray().add("a").add("b").add("c").build())
        .put("clientSide", true)
        .put("trackEvents", true)
        .put("trackEventsFallthrough", true)
        .put("debugEventsUntilDate", 1000)
        .build();    
  }

  private void assertFlagHasAllProperties(FeatureFlag flag) {
    assertEquals("flag-key", flag.getKey());
    assertEquals(99, flag.getVersion());
    assertTrue(flag.isOn());
    assertEquals("123", flag.getSalt());
    
    assertNotNull(flag.getTargets());
    assertEquals(1, flag.getTargets().size());
    Target t0 = flag.getTargets().get(0);
    assertEquals(1, t0.getVariation());
    assertEquals(ImmutableSet.of("key1", "key2"), t0.getValues());
    
    assertNotNull(flag.getRules());
    assertEquals(2, flag.getRules().size());
    Rule r0 = flag.getRules().get(0);
    assertEquals("id0", r0.getId());
    assertTrue(r0.isTrackEvents());
    assertEquals(new Integer(2), r0.getVariation());
    assertNull(r0.getRollout());
  
    assertNotNull(r0.getClauses());
    Clause c0 = r0.getClauses().get(0);
    assertEquals("name", c0.getAttribute());
    assertEquals(Operator.in, c0.getOp());
    assertEquals(ImmutableList.of(LDValue.of("Lucy")), c0.getValues());
    assertTrue(c0.isNegate());
    
    Rule r1 = flag.getRules().get(1);
    assertEquals("id1", r1.getId());
    assertFalse(r1.isTrackEvents());
    assertNull(r1.getVariation());
    assertNotNull(r1.getRollout());
    assertNotNull(r1.getRollout().getVariations());
    assertEquals(1, r1.getRollout().getVariations().size());
    assertEquals(2, r1.getRollout().getVariations().get(0).getVariation());
    assertEquals(100000, r1.getRollout().getVariations().get(0).getWeight());
    assertEquals("email", r1.getRollout().getBucketBy());
    
    assertNotNull(flag.getFallthrough());
    assertEquals(new Integer(1), flag.getFallthrough().getVariation());
    assertNull(flag.getFallthrough().getRollout());
    assertEquals(new Integer(2), flag.getOffVariation());
    assertEquals(ImmutableList.of(LDValue.of("a"), LDValue.of("b"), LDValue.of("c")), flag.getVariations());
    assertTrue(flag.isClientSide());
    assertTrue(flag.isTrackEvents());
    assertTrue(flag.isTrackEventsFallthrough());
    assertEquals(new Long(1000), flag.getDebugEventsUntilDate());  
  }
}
