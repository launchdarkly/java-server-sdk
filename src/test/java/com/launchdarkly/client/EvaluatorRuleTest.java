package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import static com.launchdarkly.client.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.client.ModelBuilders.clause;
import static com.launchdarkly.client.ModelBuilders.emptyRollout;
import static com.launchdarkly.client.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.client.ModelBuilders.flagBuilder;
import static com.launchdarkly.client.ModelBuilders.ruleBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class EvaluatorRuleTest {
  @Test
  public void ruleMatchReasonInstanceIsReusedForSameRule() {
    FlagModel.Clause clause0 = clause("key", Operator.in, LDValue.of("wrongkey"));
    FlagModel.Clause clause1 = clause("key", Operator.in, LDValue.of("userkey"));
    FlagModel.Rule rule0 = ruleBuilder().id("ruleid0").clauses(clause0).variation(2).build();
    FlagModel.Rule rule1 = ruleBuilder().id("ruleid1").clauses(clause1).variation(2).build();
    FlagModel.FeatureFlag f = featureFlagWithRules("feature", rule0, rule1);
    LDUser user = new LDUser.Builder("userkey").build();
    LDUser otherUser = new LDUser.Builder("wrongkey").build();

    Evaluator.EvalResult sameResult0 = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    Evaluator.EvalResult sameResult1 = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    Evaluator.EvalResult otherResult = BASE_EVALUATOR.evaluate(f, otherUser, EventFactory.DEFAULT);

    assertEquals(EvaluationReason.ruleMatch(1, "ruleid1"), sameResult0.getDetails().getReason());
    assertSame(sameResult0.getDetails().getReason(), sameResult1.getDetails().getReason());

    assertEquals(EvaluationReason.ruleMatch(0, "ruleid0"), otherResult.getDetails().getReason());
  }
  
  @Test
  public void ruleWithTooHighVariationReturnsMalformedFlagError() {
    FlagModel.Clause clause = clause("key", Operator.in, LDValue.of("userkey"));
    FlagModel.Rule rule = ruleBuilder().id("ruleid").clauses(clause).variation(999).build();
    FlagModel.FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void ruleWithNegativeVariationReturnsMalformedFlagError() {
    FlagModel.Clause clause = clause("key", Operator.in, LDValue.of("userkey"));
    FlagModel.Rule rule = ruleBuilder().id("ruleid").clauses(clause).variation(-1).build();
    FlagModel.FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void ruleWithNoVariationOrRolloutReturnsMalformedFlagError() {
    FlagModel.Clause clause = clause("key", Operator.in, LDValue.of("userkey"));
    FlagModel.Rule rule = ruleBuilder().id("ruleid").clauses(clause).build();
    FlagModel.FeatureFlag f = featureFlagWithRules("feature", rule);
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void ruleWithRolloutWithEmptyVariationsListReturnsMalformedFlagError() {
    FlagModel.Clause clause = clause("key", Operator.in, LDValue.of("userkey"));
    FlagModel.Rule rule = ruleBuilder().id("ruleid").clauses(clause).rollout(emptyRollout()).build();
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
