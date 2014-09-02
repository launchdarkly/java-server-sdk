package com.launchdarkly.client;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class FeatureRepTest {

  private final Variation.TargetRule targetUserOn = new Variation.TargetRule("key", Collections.singletonList("targetOn@test.com"));

  private final Variation.TargetRule targetGroupOn = new Variation.TargetRule("groups", Arrays.asList("google", "microsoft"));

  private final Variation.TargetRule targetUserOff = new Variation.TargetRule("key", Collections.singletonList("targetOff@test.com"));

  private final Variation.TargetRule targetGroupOff = new Variation.TargetRule("groups", Arrays.asList("oracle"));

  private final Variation<Boolean> trueVariation = new Variation.Builder<Boolean>(true, 80)
      .target(targetUserOn)
      .target(targetGroupOn)
      .build();

  private final Variation<Boolean> falseVariation = new Variation.Builder<Boolean>(false, 20)
      .target(targetUserOff)
      .target(targetGroupOff)
      .build();

  private final FeatureRep<Boolean> simpleFlag = new FeatureRep.Builder<Boolean>("Sample flag", "sample.flag")
      .on(true)
      .salt("feefifofum")
      .variation(trueVariation)
      .variation(falseVariation)
      .build();

  @Test
  public void testFlagForTargetedUserOff() {
    LDUser user = new LDUser.Builder("targetOff@test.com").build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(b, false);
  }

  @Test
  public void testFlagForTargetedUserOn() {
    LDUser user = new LDUser.Builder("targetOn@test.com").build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(b, true);
  }

  @Test
  public void testFlagForTargetGroupOn() {
    LDUser user = new LDUser.Builder("targetOther@test.com")
        .custom("groups", Arrays.asList("google", "microsoft"))
        .build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(b, true);
  }

  @Test
  public void testFlagForTargetGroupOff() {
    LDUser user = new LDUser.Builder("targetOther@test.com")
        .custom("groups", "oracle")
        .build();

    Boolean b = simpleFlag.evaluate(user);

    assertEquals(b, false);
  }

}
