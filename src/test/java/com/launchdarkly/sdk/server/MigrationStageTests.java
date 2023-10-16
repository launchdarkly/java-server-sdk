package com.launchdarkly.sdk.server;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Enclosed.class)
public class MigrationStageTests {

  public static class BasicTests {
    @Test
    public void itHandlesWhenAStringIsNotAStage() {
      Assert.assertFalse(MigrationStage.isStage("potato"));
    }
  }

  @RunWith(Parameterized.class)
  public static class GivenEachStageTest {
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][]{
          {MigrationStage.OFF, "off"},
          {MigrationStage.DUAL_WRITE, "dualwrite"},
          {MigrationStage.SHADOW, "shadow"},
          {MigrationStage.LIVE, "live"},
          {MigrationStage.RAMP_DOWN, "rampdown"},
          {MigrationStage.COMPLETE, "complete"}
      });
    }

    @Parameterized.Parameter
    public MigrationStage stage;

    @Parameterized.Parameter(value = 1)
    public String stageString;

    @Test
    public void itCanConvertToAString() {
      Assert.assertEquals(stageString, stage.toString());
    }

    @Test
    public void itCanTestAValueIsAStage() {
      Assert.assertTrue(MigrationStage.isStage(stageString));
    }
  }
}
