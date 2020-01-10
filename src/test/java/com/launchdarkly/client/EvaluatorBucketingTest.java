package com.launchdarkly.client;

import com.launchdarkly.client.DataModel.Rollout;
import com.launchdarkly.client.DataModel.VariationOrRollout;
import com.launchdarkly.client.DataModel.WeightedVariation;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EvaluatorBucketingTest {
  @Test
  public void variationIndexIsReturnedForBucket() {
    LDUser user = new LDUser.Builder("userkey").build();
    String flagKey = "flagkey";
    String salt = "salt";
    
    // First verify that with our test inputs, the bucket value will be greater than zero and less than 100000,
    // so we can construct a rollout whose second bucket just barely contains that value
    int bucketValue = (int)(EvaluatorBucketing.bucketUser(user, flagKey, "key", salt) * 100000);
    assertThat(bucketValue, greaterThanOrEqualTo(1));
    assertThat(bucketValue, Matchers.lessThan(100000));
    
    int badVariationA = 0, matchedVariation = 1, badVariationB = 2;
    List<WeightedVariation> variations = Arrays.asList(
        new WeightedVariation(badVariationA, bucketValue), // end of bucket range is not inclusive, so it will *not* match the target value
        new WeightedVariation(matchedVariation, 1), // size of this bucket is 1, so it only matches that specific value
        new WeightedVariation(badVariationB, 100000 - (bucketValue + 1)));
    VariationOrRollout vr = new VariationOrRollout(null, new Rollout(variations, null));
    
    Integer resultVariation = EvaluatorBucketing.variationIndexForUser(vr, user, flagKey, salt);
    assertEquals(Integer.valueOf(matchedVariation), resultVariation);
  }

  @Test
  public void lastBucketIsUsedIfBucketValueEqualsTotalWeight() {
    LDUser user = new LDUser.Builder("userkey").build();
    String flagKey = "flagkey";
    String salt = "salt";

    // We'll construct a list of variations that stops right at the target bucket value
    int bucketValue = (int)(EvaluatorBucketing.bucketUser(user, flagKey, "key", salt) * 100000);
    
    List<WeightedVariation> variations = Arrays.asList(new WeightedVariation(0, bucketValue));
    VariationOrRollout vr = new VariationOrRollout(null, new Rollout(variations, null));
    
    Integer resultVariation = EvaluatorBucketing.variationIndexForUser(vr, user, flagKey, salt);
    assertEquals(Integer.valueOf(0), resultVariation);
  }

  @Test
  public void canBucketByIntAttributeSameAsString() {
    LDUser user = new LDUser.Builder("key")
        .custom("stringattr", "33333")
        .custom("intattr", 33333)
        .build();
    float resultForString = EvaluatorBucketing.bucketUser(user, "key", "stringattr", "salt");
    float resultForInt = EvaluatorBucketing.bucketUser(user, "key", "intattr", "salt");
    assertEquals(resultForString, resultForInt, Float.MIN_VALUE);
  }

  @Test
  public void cannotBucketByFloatAttribute() {
    LDUser user = new LDUser.Builder("key")
        .custom("floatattr", 33.5f)
        .build();
    float result = EvaluatorBucketing.bucketUser(user, "key", "floatattr", "salt");
    assertEquals(0f, result, Float.MIN_VALUE);
  }

  @Test
  public void cannotBucketByBooleanAttribute() {
    LDUser user = new LDUser.Builder("key")
        .custom("boolattr", true)
        .build();
    float result = EvaluatorBucketing.bucketUser(user, "key", "boolattr", "salt");
    assertEquals(0f, result, Float.MIN_VALUE);
  }
}
