package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;
import com.launchdarkly.sdk.server.ModelBuilders.FlagBuilder;
import com.launchdarkly.sdk.server.ModelBuilders.RuleBuilder;

import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.sdk.server.EvaluatorBucketing.computeBucketValue;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingContext;
import static com.launchdarkly.sdk.server.ModelBuilders.emptyRollout;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class EvaluatorRuleTest {
  private static final LDContext BASE_USER = LDContext.create("userkey");
  private static final LDContext OTHER_USER = LDContext.create("otherkey");
  private static final int FALLTHROUGH_VARIATION = 0;
  private static final int MATCH_VARIATION = 1;
  
  private FlagBuilder buildBooleanFlagWithRules(String flagKey, DataModel.Rule... rules) {
    return flagBuilder(flagKey)
        .on(true)
        .rules(rules)
        .fallthroughVariation(FALLTHROUGH_VARIATION)
        .offVariation(FALLTHROUGH_VARIATION)
        .variations(LDValue.of(false), LDValue.of(true));
  }
  
  private RuleBuilder buildTestRule(String id, DataModel.Clause... clauses) {
    return ruleBuilder().id(id).clauses(clauses).variation(MATCH_VARIATION);
  }
  
  @Test
  public void ruleMatchResultInstanceIsReusedForSameRule() {
    Clause clause0 = clauseMatchingContext(OTHER_USER);
    Clause clause1 = clauseMatchingContext(BASE_USER);
    DataModel.Rule rule0 = buildTestRule("ruleid0", clause0).build();
    DataModel.Rule rule1 = buildTestRule("ruleid1", clause1).build();
    
    DataModel.FeatureFlag f =  buildBooleanFlagWithRules("feature", rule0, rule1).build();

    EvalResult sameResult0 = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    EvalResult sameResult1 = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    EvalResult otherResult = BASE_EVALUATOR.evaluate(f, OTHER_USER, expectNoPrerequisiteEvals());

    assertEquals(EvaluationReason.ruleMatch(1, "ruleid1"), sameResult0.getReason());
    assertSame(sameResult0, sameResult1);

    assertEquals(EvaluationReason.ruleMatch(0, "ruleid0"), otherResult.getReason());
  }
  
  @Test
  public void ruleMatchResultInstanceCanBeCreatedFromScratch() {
    // Normally we will always do the preprocessing step that creates the result instances ahead of time,
    // but if somehow we didn't, it should create them as needed
    DataModel.Clause clause = clause("key", DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule = buildTestRule("ruleid", clause).build();
    LDContext user = LDContext.create("userkey");
    
    DataModel.FeatureFlag f = buildBooleanFlagWithRules("feature", rule)
        .disablePreprocessing(true)
        .build();
    assertNull(f.getRules().get(0).preprocessed);
    
    EvalResult result1 = BASE_EVALUATOR.evaluate(f, user, expectNoPrerequisiteEvals());
    EvalResult result2 = BASE_EVALUATOR.evaluate(f, user, expectNoPrerequisiteEvals());

    assertEquals(EvaluationReason.ruleMatch(0, "ruleid"), result1.getReason());
    assertNotSame(result1, result2); // they were created individually
    assertEquals(result1, result2); // but they're equal
  }
  
  @Test
  public void ruleWithTooHighVariationReturnsMalformedFlagError() {
    Clause clause = clauseMatchingContext(BASE_USER);
    Rule rule = buildTestRule("ruleid", clause).variation(999).build();
    FeatureFlag f = buildBooleanFlagWithRules("feature", rule).build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }

  @Test
  public void ruleWithNegativeVariationReturnsMalformedFlagError() {
    Clause clause = clauseMatchingContext(BASE_USER);
    Rule rule = buildTestRule("ruleid", clause).variation(-1).build();
    FeatureFlag f = buildBooleanFlagWithRules("feature", rule).build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }
  
  @Test
  public void ruleWithNoVariationOrRolloutReturnsMalformedFlagError() {
    Clause clause = clauseMatchingContext(BASE_USER);
    Rule rule = buildTestRule("ruleid", clause).variation(null).build();
    FeatureFlag f = buildBooleanFlagWithRules("feature", rule).build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }

  @Test
  public void ruleWithRolloutWithEmptyVariationsListReturnsMalformedFlagError() {
    Clause clause = clauseMatchingContext(BASE_USER);
    Rule rule = buildTestRule("ruleid", clause).variation(null).rollout(emptyRollout()).build();
    FeatureFlag f = buildBooleanFlagWithRules("feature", rule).build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }

  @Test
  public void rolloutUsesCorrectBucketValue() {
    LDContext c = LDContext.create("foo");
    testRolloutBucketing("foo", c, null, null, RolloutKind.rollout);
  }
  
  @Test
  public void rolloutUsesContextKind() {
    LDContext c1 = LDContext.create(ContextKind.of("kind1"), "foo");
    LDContext c2 = LDContext.create(ContextKind.of("kind2"), "bar");
    LDContext multi = LDContext.createMulti(c1, c2);
    testRolloutBucketing("foo", multi, ContextKind.of("kind1"), null, RolloutKind.rollout);
  }

  @Test
  public void rolloutUsesBucketBy() {
    LDContext c = LDContext.builder("xxx").set("attr1", LDValue.parse("{\"prop1\":\"foo\"}")).build();
    testRolloutBucketing("foo", c, null, AttributeRef.fromPath("/attr1/prop1"), RolloutKind.rollout);
  }

  @Test
  public void experimentIgnoresBucketBy() {
    LDContext c = LDContext.builder("xxx").set("attr1", LDValue.parse("{\"prop1\":\"foo\"}")).build();
    testRolloutBucketing("xxx", c, null, AttributeRef.fromPath("/attr1/prop1"), RolloutKind.experiment);
  }

  private static void testRolloutBucketing(
      String bucketByValue,
      LDContext context,
      ContextKind contextKind,
      AttributeRef bucketBy,
      RolloutKind rolloutKind
      ) {
    String flagKey = "feature";
    String salt = "abc";
    float expectedBucketValue = computeBucketValue(false, null, LDContext.create(bucketByValue), null,
        flagKey, null, salt);
    int bucketValueAsInt = (int)(expectedBucketValue * 100000);
    Clause clause = clauseMatchingContext(context);
    
    // To roughly verify that the right bucket value is being used, we'll construct a rollout
    // where the target bucket is in a very small range around that value.
    Rollout rollout = new Rollout(
        contextKind,
        Arrays.asList(
          new WeightedVariation(0, bucketValueAsInt - 1, false),
          new WeightedVariation(1, 2, false),
          new WeightedVariation(2, 100000 - (bucketValueAsInt + 1), false)
        ),
        bucketBy,
        rolloutKind,
        null);
    FeatureFlag flag = flagBuilder(flagKey)
        .on(true)
        .variations(LDValue.of("no"), LDValue.of("yes"), LDValue.of("no"))
        .rules(ruleBuilder().id("rule").clauses(clause).rollout(rollout).build())
        .salt(salt)
        .build();
    
    EvalResult result = BASE_EVALUATOR.evaluate(flag, context, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of("yes"), result.getValue());
    assertEquals(1, result.getVariationIndex());
    assertEquals(EvaluationReason.Kind.RULE_MATCH, result.getReason().getKind());
  }  
}
