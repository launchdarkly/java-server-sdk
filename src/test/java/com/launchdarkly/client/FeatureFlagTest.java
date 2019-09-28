package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.launchdarkly.client.value.LDValue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.client.EvaluationDetail.fromJsonValue;
import static com.launchdarkly.client.TestUtil.booleanFlagWithClauses;
import static com.launchdarkly.client.TestUtil.fallthroughVariation;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    
    assertEquals(fromJsonValue(LDValue.of("off"), 1, EvaluationReason.off()), result.getDetails());
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
    
    assertEquals(fromJsonValue(LDValue.ofNull(), null, EvaluationReason.off()), result.getDetails());
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
    
    assertEquals(fromJsonValue(LDValue.of("fall"), 0, EvaluationReason.fallthrough()), result.getDetails());
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
    assertEquals(fromJsonValue(LDValue.of("off"), 1, expectedReason), result.getDetails());
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
    assertEquals(fromJsonValue(LDValue.of("off"), 1, expectedReason), result.getDetails());
    
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
    assertEquals(fromJsonValue(LDValue.of("off"), 1, expectedReason), result.getDetails());
    
    assertEquals(1, result.getPrerequisiteEvents().size());
    Event.FeatureRequest event = result.getPrerequisiteEvents().get(0);
    assertEquals(f1.getKey(), event.key);
    assertEquals(LDValue.of("nogo"), event.value);
    assertEquals(f1.getVersion(), event.version.intValue());
    assertEquals(f0.getKey(), event.prereqOf);
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
    
    assertEquals(fromJsonValue(LDValue.of("fall"), 0, EvaluationReason.fallthrough()), result.getDetails());
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
    
    assertEquals(fromJsonValue(LDValue.of("fall"), 0, EvaluationReason.fallthrough()), result.getDetails());
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
        .targets(Arrays.asList(new Target(Arrays.asList("whoever", "userkey"), 2)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    LDUser user = new LDUser.Builder("userkey").build();
    FeatureFlag.EvalResult result = f.evaluate(user, featureStore, EventFactory.DEFAULT);
    
    assertEquals(fromJsonValue(LDValue.of("on"), 2, EvaluationReason.targetMatch()), result.getDetails());
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
    
    assertEquals(fromJsonValue(LDValue.of("on"), 2, EvaluationReason.ruleMatch(1, "ruleid1")), result.getDetails());
    assertEquals(0, result.getPrerequisiteEvents().size());
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
    assertEquals(fromJsonValue(LDValue.of(true), 1, EvaluationReason.ruleMatch(1, "rule2")), details);
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
}
