package com.launchdarkly.client;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class FeatureRepTest {

  private final Variation.TargetRule targetUserOn = new Variation.TargetRule("key", Collections.singletonList("targetOn@test.com"));

  private final Variation.TargetRule targetUserOff = new Variation.TargetRule("key", Collections.singletonList("targetOff@test.com"));

  private final Variation<Boolean> trueVariation = new Variation.Builder<Boolean>(true, 80).target(targetUserOn).build();

  private final Variation<Boolean> falseVariation = new Variation.Builder<Boolean>(false, 80).target(targetUserOff).build();

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

}
