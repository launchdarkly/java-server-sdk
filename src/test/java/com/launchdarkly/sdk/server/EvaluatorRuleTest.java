package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.ModelBuilders.FlagBuilder;
import com.launchdarkly.sdk.server.ModelBuilders.RuleBuilder;

import org.junit.Test;

import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.emptyRollout;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class EvaluatorRuleTest {
  private static final int FALLTHROUGH_VARIATION = 0;
  private static final int MATCH_VARIATION = 1;
  
  private FlagBuilder buildBooleanFlagWithRules(String flagKey, DataModel.Rule... rules) {
    return flagBuilder(flagKey)
        .on(true)
        .rules(rules)
        .fallthroughVariation(FALLTHROUGH_VARIATION)
        .offVariation(FALLTHROUGH_VARIATION)
        .variations(LDValue.of(false), LDValue.of(true));
  }
  
  private RuleBuilder buildTestRule(String id, DataModel.Clause... clauses) {
    return ruleBuilder().id(id).clauses(clauses).variation(MATCH_VARIATION);
  }
  
  @Test
  public void ruleMatchReasonInstanceIsReusedForSameRule() {
    DataModel.Clause clause0 = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("wrongkey"));
    DataModel.Clause clause1 = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule0 = buildTestRule("ruleid0", clause0).build();
    DataModel.Rule rule1 = buildTestRule("ruleid1", clause1).build();
    
    DataModel.FeatureFlag f =  buildBooleanFlagWithRules("feature", rule0, rule1).build();
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
  public void ruleMatchReasonInstanceCanBeCreatedFromScratch() {
    // Normally we will always do the preprocessing step that creates the reason instances ahead of time,
    // but if somehow we didn't, it should create them as needed
    DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule = buildTestRule("ruleid", clause).build();
    LDUser user = new LDUser("userkey");
    
    DataModel.FeatureFlag f = buildBooleanFlagWithRules("feature", rule)
        .disablePreprocessing(true)
        .build();
    assertNull(f.getRules().get(0).getRuleMatchReason());
    
    Evaluator.EvalResult result1 = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    Evaluator.EvalResult result2 = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);

    assertEquals(EvaluationReason.ruleMatch(0, "ruleid"), result1.getDetails().getReason());
    assertNotSame(result1.getDetails().getReason(), result2.getDetails().getReason()); // they were created individually
    assertEquals(result1.getDetails().getReason(), result2.getDetails().getReason()); // but they're equal
  }
  
  @Test
  public void ruleWithTooHighVariationReturnsMalformedFlagError() {
    DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule = buildTestRule("ruleid", clause).variation(999).build();
    DataModel.FeatureFlag f = buildBooleanFlagWithRules("feature", rule).build();
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void ruleWithNegativeVariationReturnsMalformedFlagError() {
    DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule = buildTestRule("ruleid", clause).variation(-1).build();
    DataModel.FeatureFlag f = buildBooleanFlagWithRules("feature", rule).build();
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void ruleWithNoVariationOrRolloutReturnsMalformedFlagError() {
    DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule = buildTestRule("ruleid", clause).variation(null).build();
    DataModel.FeatureFlag f = buildBooleanFlagWithRules("feature", rule).build();
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void ruleWithRolloutWithEmptyVariationsListReturnsMalformedFlagError() {
    DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule = buildTestRule("ruleid", clause).variation(null).rollout(emptyRollout()).build();
    DataModel.FeatureFlag f = buildBooleanFlagWithRules("feature", rule).build();
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
}
