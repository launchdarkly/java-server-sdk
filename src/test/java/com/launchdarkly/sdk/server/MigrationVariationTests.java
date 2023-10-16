package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.TestData;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class MigrationVariationTests extends BaseTest {

  @Parameterized.Parameters(name = "{0}" )
  public static Iterable<? extends Object> data() {
    return Arrays.asList(
        MigrationStage.OFF,
        MigrationStage.DUAL_WRITE,
        MigrationStage.SHADOW,
        MigrationStage.LIVE,
        MigrationStage.RAMP_DOWN,
        MigrationStage.COMPLETE
    );
  }

  private final TestData testData = TestData.dataSource();

  private final LDClientInterface client = new LDClient("SDK_KEY", baseConfig()
      .dataSource(testData)
      .build());

  @Parameterized.Parameter
  public MigrationStage stage;

  @Test
  public void itEvaluatesDefaultForMissingFlag() {
    MigrationVariation resStage = client.migrationVariation("key", LDContext.create("potato"), stage);
    Assert.assertEquals(stage, resStage.getStage());
  }

  @Test
  public void itDoesEvaluateDefaultForFlagWithInvalidStage() {
    final String flagKey = "test-flag";
    final LDContext context = LDContext.create("test-key");
    testData.update(testData.flag(flagKey).valueForAll(LDValue.of("potato")));
    MigrationVariation resStage = client.migrationVariation(flagKey, context, stage);
    Assert.assertEquals(stage, resStage.getStage());
  }

  @Test
  public void itEvaluatesCorrectValueForExistingFlag() {
    final String flagKey = "test-flag";
    final LDContext context = LDContext.create("test-key");
    testData.update(testData.flag(flagKey).valueForAll(LDValue.of(stage.toString())));
    // Get a stage that is not the stage we are testing.
    MigrationStage defaultStage = Arrays.stream(MigrationStage.values()).filter(item -> item != stage).findFirst().get();
    MigrationVariation resStage = client.migrationVariation(flagKey, context, defaultStage);
    Assert.assertEquals(stage, resStage.getStage());
  }
}
