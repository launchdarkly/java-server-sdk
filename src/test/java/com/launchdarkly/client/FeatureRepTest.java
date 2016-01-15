package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import org.glassfish.jersey.server.JSONP;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class FeatureRepTest {

  private final Variation.TargetRule targetUserOn = new Variation.TargetRule("key", Collections.<JsonPrimitive>singletonList(new JsonPrimitive("targetOn@test.com")));

  private final Variation.TargetRule targetGroupOn = new Variation.TargetRule("groups", Arrays.<JsonPrimitive>asList(new JsonPrimitive("google"), new JsonPrimitive("microsoft")));

  // GSON will deserialize numbers as decimals
  private final Variation.TargetRule targetFavoriteNumberOn = new Variation.TargetRule("favorite_number", Arrays.<JsonPrimitive>asList(new JsonPrimitive(42)));

  private final Variation.TargetRule targetLikesCatsOn = new Variation.TargetRule("likes_cats", Arrays.<JsonPrimitive>asList(new JsonPrimitive(true)));

  private final Variation.TargetRule targetUserOff = new Variation.TargetRule("key", Collections.<JsonPrimitive>singletonList(new JsonPrimitive("targetOff@test.com")));

  private final Variation.TargetRule targetGroupOff = new Variation.TargetRule("groups", Arrays.<JsonPrimitive>asList(new JsonPrimitive("oracle")));

  private final Variation.TargetRule targetFavoriteNumberOff = new Variation.TargetRule("favorite_number", Arrays.<JsonPrimitive>asList(new JsonPrimitive(33.0)));

  private final Variation.TargetRule targetLikesDogsOff = new Variation.TargetRule("likes_dogs", Arrays.<JsonPrimitive>asList(new JsonPrimitive(false)));

  private final Variation.TargetRule targetAnonymousOn = new Variation.TargetRule("anonymous", Collections.<JsonPrimitive>singletonList(new JsonPrimitive(true)));

  private final Variation<Boolean> trueVariation = new Variation.Builder<Boolean>(true, 80)
      .target(targetUserOn)
      .target(targetGroupOn)
      .target(targetAnonymousOn)
      .target(targetLikesCatsOn)
      .target(targetFavoriteNumberOn)
      .build();

  private final Variation<Boolean> falseVariation = new Variation.Builder<Boolean>(false, 20)
      .target(targetUserOff)
      .target(targetGroupOff)
      .target(targetFavoriteNumberOff)
      .target(targetLikesDogsOff)
      .build();

  private final FeatureRep<Boolean> simpleFlag = new FeatureRep.Builder<Boolean>("Sample flag", "sample.flag")
      .on(true)
      .salt("feefifofum")
      .variation(trueVariation)
      .variation(falseVariation)
      .build();

  private final FeatureRep<Boolean> disabledFlag = new FeatureRep.Builder<Boolean>("Sample flag", "sample.flag")
      .on(false)
      .salt("feefifofum")
      .variation(trueVariation)
      .variation(falseVariation)
      .build();

  private Variation<Boolean> userRuleVariation = new Variation.Builder<Boolean>(false, 20)
      .userTarget(targetUserOn)
      .build();

  private final FeatureRep<Boolean> userRuleFlag = new FeatureRep.Builder<Boolean>("User rule flag", "user.rule.flag")
      .on(true)
      .salt("feefifofum")
      .variation(trueVariation)
      .variation(userRuleVariation)
      .build();

  @Test
  public void testUserRuleFlagForTargetUserOff() {

    // The trueVariation tries to enable this rule, but the userVariation (with false value) has a userRule
    // that's able to override this. This doesn't represent a real LD response-- we'd never have feature reps
    // that sometimes contain user rules and sometimes contain embedded 'key' rules
    LDUser user = new LDUser.Builder("targetOn@test.com").build();
    Boolean b = userRuleFlag.evaluate(user);

    assertEquals(false, b);
  }

  @Test
  public void testFlagForTargetedUserOff() {
    LDUser user = new LDUser.Builder("targetOff@test.com").build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(false, b);
  }

  @Test
  public void testFlagForTargetedUserOn() {
    LDUser user = new LDUser.Builder("targetOn@test.com").build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(true, b);
  }

  @Test
  public void testFlagForTargetGroupOn() {
    LDUser user = new LDUser.Builder("targetOther@test.com")
        .custom("groups", Arrays.asList("google", "microsoft"))
        .build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(true, b);
  }

  @Test
  public void testFlagForTargetNumericTestOn() {
    LDUser user = new LDUser.Builder("targetOther@test.com")
        .custom("favorite_number", 42.0)
        .build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(true, b);
  }

  @Test
  public void testFlagForTargetNumericListTestOn() {
    LDUser user = new LDUser.Builder("targetOther@test.com")
        .customNumber("favorite_number", Arrays.<Number>asList(42, 32))
        .build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(true, b);
  }

  @Test
  public void testFlagForTargetBooleanTestOn() {
      LDUser user = new LDUser.Builder("targetOther@test.com")
              .custom("likes_cats", true)
              .build();

      Boolean b = simpleFlag.evaluate(user);

      assertEquals(true, b);
  }

  @Test
  public void testFlagForTargetGroupOff() {
    LDUser user = new LDUser.Builder("targetOther@test.com")
        .custom("groups", "oracle")
        .build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(false, b);
  }

  @Test
  public void testFlagForTargetNumericTestOff() {
    LDUser user = new LDUser.Builder("targetOther@test.com")
        .custom("favorite_number", 33.0)
        .build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(false, b);
  }

  @Test
  public void testFlagForTargetBooleanTestOff() {
      LDUser user = new LDUser.Builder("targetOther@test.com")
              .custom("likes_dogs", false)
              .build();

      Boolean b = simpleFlag.evaluate(user);

      assertEquals(false, b);
  }

  @Test
  public void testDisabledFlagAlwaysOff() {
    LDUser user = new LDUser("targetOn@test.com");

    Boolean b = disabledFlag.evaluate(user);

    assertEquals(null, b);
  }

  @Test
  public void testFlagWithCustomAttributeWorksWithLDUserDefaultCtor() {
    LDUser user = new LDUser("randomUser@test.com");

    Boolean b = simpleFlag.evaluate(user);

    assertNotNull(b);
  }

  @Test
  public void testFlagWithAnonymousOn() {
    LDUser user = new LDUser.Builder("targetOff@test.com").anonymous(true).build();

    Boolean b = simpleFlag.evaluate(user);
    assertEquals(true, b);
  }

}
