package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.client.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.client.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.client.ModelBuilders.flagBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EvaluatorRuleTest {
  @Test
  public void ruleWithTooHighVariationReturnsMalformedFlagError() {
    FlagModel.Clause clause = new FlagModel.Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    FlagModel.Rule rule = new FlagModel.Rule("ruleid", Arrays.asList(clause), 999, null);
    FlagModel.FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void ruleWithNegativeVariationReturnsMalformedFlagError() {
    FlagModel.Clause clause = new FlagModel.Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    FlagModel.Rule rule = new FlagModel.Rule("ruleid", Arrays.asList(clause), -1, null);
    FlagModel.FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void ruleWithNoVariationOrRolloutReturnsMalformedFlagError() {
    FlagModel.Clause clause = new FlagModel.Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    FlagModel.Rule rule = new FlagModel.Rule("ruleid", Arrays.asList(clause), null, null);
    FlagModel.FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void ruleWithRolloutWithEmptyVariationsListReturnsMalformedFlagError() {
    FlagModel.Clause clause = new FlagModel.Clause("key", Operator.in, Arrays.asList(LDValue.of("userkey")), false);
    FlagModel.Rule rule = new FlagModel.Rule("ruleid", Arrays.asList(clause), null,
        new FlagModel.Rollout(ImmutableList.<FlagModel.WeightedVariation>of(), null));
    FlagModel.FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  private FlagModel.FeatureFlag featureFlagWithRules(String flagKey, FlagModel.Rule... rules) {
    return flagBuilder(flagKey)
        .on(true)
        .rules(rules)
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
  }
}
