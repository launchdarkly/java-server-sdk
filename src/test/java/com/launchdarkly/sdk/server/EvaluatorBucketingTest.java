package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.launchdarkly.sdk.server.EvaluatorBucketing.computeBucketValue;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EvaluatorBucketingTest {
  private Integer noSeed = null;

  @Test
  public void variationIndexIsReturnedForBucket() {
    LDContext context = LDContext.create("userkey");
    String flagKey = "flagkey";
    String salt = "salt";
    
    // First verify that with our test inputs, the bucket value will be greater than zero and less than 100000,
    // so we can construct a rollout whose second bucket just barely contains that value
    int bucketValue = (int)(computeBucketValue(false, noSeed, context, null, flagKey, null, salt) * 100000);
    assertThat(bucketValue, greaterThanOrEqualTo(1));
    assertThat(bucketValue, lessThan(100000));
    
    int badVariationA = 0, matchedVariation = 1, badVariationB = 2;
    List<WeightedVariation> variations = Arrays.asList(
        new WeightedVariation(badVariationA, bucketValue, true), // end of bucket range is not inclusive, so it will *not* match the target value
        new WeightedVariation(matchedVariation, 1, true), // size of this bucket is 1, so it only matches that specific value
        new WeightedVariation(badVariationB, 100000 - (bucketValue + 1), true));
    Rollout rollout = new Rollout(null, variations, null, RolloutKind.rollout, null);
    
    assertVariationIndexFromRollout(matchedVariation, rollout, context, flagKey, salt);
  }

  @Test
  public void usingSeedIsDifferentThanSalt() {
    LDContext context = LDContext.create("userkey");
    String flagKey = "flagkey";
    String salt = "salt";
    Integer seed = 123;
    
    float bucketValue1 = computeBucketValue(false, noSeed, context, null, flagKey, null, salt);
    float bucketValue2 = computeBucketValue(true, seed, context, null, flagKey, null, salt);
    assert(bucketValue1 != bucketValue2);
  }

  @Test
  public void differentSeedsProduceDifferentAssignment() {
    LDContext context = LDContext.create("userkey");
    String flagKey = "flagkey";
    String salt = "salt";
    Integer seed1 = 123;
    Integer seed2 = 456;
    
    float bucketValue1 = computeBucketValue(true, seed1, context, null, flagKey, null, salt);
    float bucketValue2 = computeBucketValue(true, seed2, context, null, flagKey, null, salt);
    assert(bucketValue1 != bucketValue2);
  }

  @Test
  public void flagKeyAndSaltDoNotMatterWhenSeedIsUsed() {
    LDContext context = LDContext.create("userkey");
    String flagKey1 = "flagkey";
    String flagKey2 = "flagkey2";
    String salt1 = "salt";
    String salt2 = "salt2";
    Integer seed = 123;
    
    float bucketValue1 = computeBucketValue(true, seed, context, null, flagKey1, null, salt1);
    float bucketValue2 = computeBucketValue(true, seed, context, null, flagKey2, null, salt2);
    assert(bucketValue1 == bucketValue2);
  }

  @Test
  public void lastBucketIsUsedIfBucketValueEqualsTotalWeight() {
    LDContext context = LDContext.create("userkey");
    String flagKey = "flagkey";
    String salt = "salt";

    // We'll construct a list of variations that stops right at the target bucket value
    int bucketValue = (int)(computeBucketValue(false, noSeed, context, null, flagKey, null, salt) * 100000);
    
    List<WeightedVariation> variations = Arrays.asList(new WeightedVariation(0, bucketValue, true));
    Rollout rollout = new Rollout(null, variations, null, RolloutKind.rollout, null);
    
    assertVariationIndexFromRollout(0, rollout, context, flagKey, salt);
  }

  @Test
  public void canBucketByIntAttributeSameAsString() {
    LDContext context = LDContext.builder("key")
        .set("stringattr", "33333")
        .set("intattr", 33333)
        .build();
    float resultForString = computeBucketValue(false, noSeed, context, null, "key", AttributeRef.fromLiteral("stringattr"), "salt");
    float resultForInt = computeBucketValue(false, noSeed, context, null, "key", AttributeRef.fromLiteral("intattr"), "salt");
    assertEquals(resultForString, resultForInt, Float.MIN_VALUE);
  }

  @Test
  public void cannotBucketByFloatAttribute() {
    LDContext context = LDContext.builder("key")
        .set("floatattr", 33.5f)
        .build();
    float result = computeBucketValue(false, noSeed, context, null, "key", AttributeRef.fromLiteral("floatattr"), "salt");
    assertEquals(0f, result, Float.MIN_VALUE);
  }

  @Test
  public void cannotBucketByBooleanAttribute() {
    LDContext context = LDContext.builder("key")
        .set("boolattr", true)
        .build();
    float result = computeBucketValue(false, noSeed, context, null, "key", AttributeRef.fromLiteral("boolattr"), "salt");
    assertEquals(0f, result, Float.MIN_VALUE);
  }

  private static void assertVariationIndexFromRollout(
      int expectedVariation,
      Rollout rollout,
      LDContext context,
      String flagKey,
      String salt
      ) {    
    FeatureFlag flag1 = ModelBuilders.flagBuilder(flagKey)
        .on(true)
        .generatedVariations(3)
        .fallthrough(rollout)
        .salt(salt)
        .build();
    EvalResult result1 = BASE_EVALUATOR.evaluate(flag1, context, expectNoPrerequisiteEvals());
    assertThat(result1.getReason(), equalTo(EvaluationReason.fallthrough()));
    assertThat(result1.getVariationIndex(), equalTo(expectedVariation));
    
    // Make sure we consistently apply the rollout regardless of whether it's in a rule or a fallthrough
    FeatureFlag flag2 = ModelBuilders.flagBuilder(flagKey)
        .on(true)
        .generatedVariations(3)
        .rules(ModelBuilders.ruleBuilder()
            .rollout(rollout)
            .clauses(clauseMatchingContext(context))
            .build())
        .salt(salt)
        .build();
    EvalResult result2 = BASE_EVALUATOR.evaluate(flag2, context, expectNoPrerequisiteEvals());
    assertThat(result2.getReason().getKind(), equalTo(EvaluationReason.Kind.RULE_MATCH));
    assertThat(result2.getVariationIndex(), equalTo(expectedVariation));
  }
}
