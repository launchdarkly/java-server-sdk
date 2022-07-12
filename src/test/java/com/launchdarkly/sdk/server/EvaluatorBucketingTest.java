package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@SuppressWarnings("javadoc")
public class EvaluatorBucketingTest {
  private Integer noSeed = null;

  @Test
  public void variationIndexIsReturnedForBucket() {
    LDUser user = new LDUser.Builder("userkey").build();
    String flagKey = "flagkey";
    String salt = "salt";
    
    // First verify that with our test inputs, the bucket value will be greater than zero and less than 100000,
    // so we can construct a rollout whose second bucket just barely contains that value
    int bucketValue = (int)(EvaluatorBucketing.bucketUser(noSeed, user, flagKey, UserAttribute.KEY, salt) * 100000);
    assertThat(bucketValue, greaterThanOrEqualTo(1));
    assertThat(bucketValue, lessThan(100000));
    
    int badVariationA = 0, matchedVariation = 1, badVariationB = 2;
    List<WeightedVariation> variations = Arrays.asList(
        new WeightedVariation(badVariationA, bucketValue, true), // end of bucket range is not inclusive, so it will *not* match the target value
        new WeightedVariation(matchedVariation, 1, true), // size of this bucket is 1, so it only matches that specific value
        new WeightedVariation(badVariationB, 100000 - (bucketValue + 1), true));
    Rollout rollout = new Rollout(variations, null, RolloutKind.rollout);
    
    assertVariationIndexFromRollout(matchedVariation, rollout, user, flagKey, salt);
  }

  @Test
  public void usingSeedIsDifferentThanSalt() {
    LDUser user = new LDUser.Builder("userkey").build();
    String flagKey = "flagkey";
    String salt = "salt";
    Integer seed = 123;
    
    float bucketValue1 = EvaluatorBucketing.bucketUser(noSeed, user, flagKey, UserAttribute.KEY, salt);
    float bucketValue2 = EvaluatorBucketing.bucketUser(seed, user, flagKey, UserAttribute.KEY, salt);
    assert(bucketValue1 != bucketValue2);
  }

  @Test
  public void differentSeedsProduceDifferentAssignment() {
    LDUser user = new LDUser.Builder("userkey").build();
    String flagKey = "flagkey";
    String salt = "salt";
    Integer seed1 = 123;
    Integer seed2 = 456;
    
    float bucketValue1 = EvaluatorBucketing.bucketUser(seed1, user, flagKey, UserAttribute.KEY, salt);
    float bucketValue2 = EvaluatorBucketing.bucketUser(seed2, user, flagKey, UserAttribute.KEY, salt);
    assert(bucketValue1 != bucketValue2);
  }

  @Test
  public void flagKeyAndSaltDoNotMatterWhenSeedIsUsed() {
    LDUser user = new LDUser.Builder("userkey").build();
    String flagKey1 = "flagkey";
    String flagKey2 = "flagkey2";
    String salt1 = "salt";
    String salt2 = "salt2";
    Integer seed = 123;
    
    float bucketValue1 = EvaluatorBucketing.bucketUser(seed, user, flagKey1, UserAttribute.KEY, salt1);
    float bucketValue2 = EvaluatorBucketing.bucketUser(seed, user, flagKey2, UserAttribute.KEY, salt2);
    assert(bucketValue1 == bucketValue2);
  }

  @Test
  public void lastBucketIsUsedIfBucketValueEqualsTotalWeight() {
    LDUser user = new LDUser.Builder("userkey").build();
    String flagKey = "flagkey";
    String salt = "salt";

    // We'll construct a list of variations that stops right at the target bucket value
    int bucketValue = (int)(EvaluatorBucketing.bucketUser(noSeed, user, flagKey, UserAttribute.KEY, salt) * 100000);
    
    List<WeightedVariation> variations = Arrays.asList(new WeightedVariation(0, bucketValue, true));
    Rollout rollout = new Rollout(variations, null, RolloutKind.rollout);
    
    assertVariationIndexFromRollout(0, rollout, user, flagKey, salt);
  }

  @Test
  public void canBucketByIntAttributeSameAsString() {
    LDUser user = new LDUser.Builder("key")
        .custom("stringattr", "33333")
        .custom("intattr", 33333)
        .build();
    float resultForString = EvaluatorBucketing.bucketUser(noSeed, user, "key", UserAttribute.forName("stringattr"), "salt");
    float resultForInt = EvaluatorBucketing.bucketUser(noSeed, user, "key", UserAttribute.forName("intattr"), "salt");
    assertEquals(resultForString, resultForInt, Float.MIN_VALUE);
  }

  @Test
  public void cannotBucketByFloatAttribute() {
    LDUser user = new LDUser.Builder("key")
        .custom("floatattr", 33.5f)
        .build();
    float result = EvaluatorBucketing.bucketUser(noSeed, user, "key", UserAttribute.forName("floatattr"), "salt");
    assertEquals(0f, result, Float.MIN_VALUE);
  }

  @Test
  public void cannotBucketByBooleanAttribute() {
    LDUser user = new LDUser.Builder("key")
        .custom("boolattr", true)
        .build();
    float result = EvaluatorBucketing.bucketUser(noSeed, user, "key", UserAttribute.forName("boolattr"), "salt");
    assertEquals(0f, result, Float.MIN_VALUE);
  }

  @Test
  public void userSecondaryKeyAffectsBucketValue() {
    LDUser user1 = new LDUser.Builder("key").build();
    LDUser user2 = new LDUser.Builder("key").secondary("other").build();
    float result1 = EvaluatorBucketing.bucketUser(noSeed, user1, "flagkey", UserAttribute.KEY, "salt");
    float result2 = EvaluatorBucketing.bucketUser(noSeed, user2, "flagkey", UserAttribute.KEY, "salt");
    assertNotEquals(result1, result2);
  }

  private static void assertVariationIndexFromRollout(
      int expectedVariation,
      Rollout rollout,
      LDUser user,
      String flagKey,
      String salt
      ) {    
    FeatureFlag flag1 = ModelBuilders.flagBuilder(flagKey)
        .on(true)
        .generatedVariations(3)
        .fallthrough(rollout)
        .salt(salt)
        .build();
    EvalResult result1 = BASE_EVALUATOR.evaluate(flag1, user, expectNoPrerequisiteEvals());
    assertThat(result1.getReason(), equalTo(EvaluationReason.fallthrough()));
    assertThat(result1.getVariationIndex(), equalTo(expectedVariation));
    
    // Make sure we consistently apply the rollout regardless of whether it's in a rule or a fallthrough
    FeatureFlag flag2 = ModelBuilders.flagBuilder(flagKey)
        .on(true)
        .generatedVariations(3)
        .rules(ModelBuilders.ruleBuilder()
            .rollout(rollout)
            .clauses(ModelBuilders.clause(UserAttribute.KEY, Operator.in, LDValue.of(user.getKey())))
            .build())
        .salt(salt)
        .build();
    EvalResult result2 = BASE_EVALUATOR.evaluate(flag2, user, expectNoPrerequisiteEvals());
    assertThat(result2.getReason().getKind(), equalTo(EvaluationReason.Kind.RULE_MATCH));
    assertThat(result2.getVariationIndex(), equalTo(expectedVariation));
  }
}
