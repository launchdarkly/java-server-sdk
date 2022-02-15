package com.launchdarkly.sdk.server;

import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.VariationOrRollout;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;
import com.launchdarkly.sdk.server.ModelBuilders.FlagBuilder;
import com.launchdarkly.sdk.server.interfaces.Event;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.EvaluationDetail.fromValue;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.prerequisite;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.target;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class EvaluatorTest extends EvaluatorTestBase {
  private static final LDUser BASE_USER = new LDUser.Builder("x").build();

  // These constants and flag builders define two kinds of flag: one with three variations-- allowing us to
  // distinguish between match, fallthrough, and off results--  and one with two.
  private static final int OFF_VARIATION = 0;
  private static final LDValue OFF_VALUE = LDValue.of("off");
  private static final int FALLTHROUGH_VARIATION = 1;
  private static final LDValue FALLTHROUGH_VALUE = LDValue.of("fall");
  private static final int MATCH_VARIATION = 2;
  private static final LDValue MATCH_VALUE = LDValue.of("match");
  private static final LDValue[] THREE_VARIATIONS = new LDValue[] { OFF_VALUE, FALLTHROUGH_VALUE, MATCH_VALUE };

  private static final int RED_VARIATION = 0;
  private static final LDValue RED_VALUE = LDValue.of("red");
  private static final int GREEN_VARIATION = 1;
  private static final LDValue GREEN_VALUE = LDValue.of("green");
  private static final LDValue[] RED_GREEN_VARIATIONS = new LDValue[] { RED_VALUE, GREEN_VALUE };
  
  private static FlagBuilder buildThreeWayFlag(String flagKey) {
    return flagBuilder(flagKey)
        .fallthroughVariation(FALLTHROUGH_VARIATION)
        .offVariation(OFF_VARIATION)
        .variations(THREE_VARIATIONS)
        .version(versionFromKey(flagKey));
  }
  
  private static FlagBuilder buildRedGreenFlag(String flagKey) {
    return flagBuilder(flagKey)
        .fallthroughVariation(GREEN_VARIATION)
        .offVariation(RED_VARIATION)
        .variations(RED_GREEN_VARIATIONS)
        .version(versionFromKey(flagKey));
  }

  private static Rollout buildRollout(boolean isExperiment, boolean untrackedVariations) {
    List<WeightedVariation> variations = new ArrayList<>();
    variations.add(new WeightedVariation(1, 50000, untrackedVariations));
    variations.add(new WeightedVariation(2, 50000, untrackedVariations));
    UserAttribute bucketBy = UserAttribute.KEY;
    RolloutKind kind = isExperiment ? RolloutKind.experiment : RolloutKind.rollout;
    Integer seed = 123;
    Rollout rollout = new Rollout(variations, bucketBy, kind, seed);
    return rollout;
  }
  
  private static int versionFromKey(String flagKey) {
    return Math.abs(flagKey.hashCode());
  }
  
  @Test
  public void evaluationReturnsErrorIfUserIsNull() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, null, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void evaluationReturnsErrorIfUserKeyIsNull() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, new LDUser(null), EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsOffVariationIfFlagIsOff() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(false)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(OFF_VALUE, OFF_VARIATION, EvaluationReason.off()), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsNullIfFlagIsOffAndOffVariationIsUnspecified() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(false)
        .offVariation(null)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.ofNull(), NO_VARIATION, EvaluationReason.off()), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagReturnsErrorIfFlagIsOffAndOffVariationIsTooHigh() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(false)
        .offVariation(999)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsErrorIfFlagIsOffAndOffVariationIsNegative() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(false)
        .offVariation(-1)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagReturnsInExperimentForFallthroughWhenInExperimentVariation() throws Exception {
    Rollout rollout = buildRollout(true, false);
    VariationOrRollout vr = new VariationOrRollout(null, rollout);

    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthrough(vr)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assert(result.getReason().isInExperiment());
  }

  @Test
  public void flagReturnsNotInExperimentForFallthroughWhenNotInExperimentVariation() throws Exception {
    Rollout rollout = buildRollout(true, true);
    VariationOrRollout vr = new VariationOrRollout(null, rollout);

    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthrough(vr)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assert(!result.getReason().isInExperiment());
  }

  @Test
  public void flagReturnsNotInExperimentForFallthrougWhenInExperimentVariationButNonExperimentRollout() throws Exception {
    Rollout rollout = buildRollout(false, false);
    VariationOrRollout vr = new VariationOrRollout(null, rollout);

    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthrough(vr)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assert(!result.getReason().isInExperiment());
  }

  @Test
  public void flagReturnsInExperimentForRuleMatchWhenInExperimentVariation() throws Exception {
    Rollout rollout = buildRollout(true, false);

    DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of(BASE_USER.getKey()));
    DataModel.Rule rule = ruleBuilder().id("ruleid0").clauses(clause).rollout(rollout).build();

    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .rules(rule)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assert(result.getReason().isInExperiment());
  }

  @Test
  public void flagReturnsNotInExperimentForRuleMatchWhenNotInExperimentVariation() throws Exception {
    Rollout rollout = buildRollout(true, true);
    
    DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule = ruleBuilder().id("ruleid0").clauses(clause).rollout(rollout).build();

    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .rules(rule)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assert(!result.getReason().isInExperiment());
  }

  @Test
  public void flagReturnsFallthroughIfFlagIsOnAndThereAreNoRules() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(FALLTHROUGH_VALUE, FALLTHROUGH_VARIATION, EvaluationReason.fallthrough()), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasTooHighVariation() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthroughVariation(999)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasNegativeVariation() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthroughVariation(-1)
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasNeitherVariationNorRollout() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthrough(new DataModel.VariationOrRollout(null, null))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagReturnsErrorIfFallthroughHasEmptyRolloutVariationList() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthrough(new DataModel.VariationOrRollout(null, ModelBuilders.emptyRollout()))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagReturnsOffVariationIfPrerequisiteIsNotFound() throws Exception {
    DataModel.FeatureFlag f0 = buildThreeWayFlag("feature")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .build();
    Evaluator e = evaluatorBuilder().withNonexistentFlag("feature1").build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(fromValue(OFF_VALUE, OFF_VARIATION, expectedReason), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsOffVariationAndEventIfPrerequisiteIsOff() throws Exception {
    DataModel.FeatureFlag f0 = buildThreeWayFlag("feature")
        .on(true)
        .prerequisites(prerequisite("feature1", GREEN_VARIATION))
        .build();
    DataModel.FeatureFlag f1 = buildRedGreenFlag("feature1")
        .on(false)
        .offVariation(GREEN_VARIATION)
        // note that even though it returns the desired variation, it is still off and therefore not a match
        .build();
    Evaluator e = evaluatorBuilder().withStoredFlags(f1).build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(fromValue(OFF_VALUE, OFF_VARIATION, expectedReason), result.getDetails());
    
    assertEquals(1, Iterables.size(result.getPrerequisiteEvents()));
    Event.FeatureRequest event = Iterables.get(result.getPrerequisiteEvents(), 0);
    assertEquals(f1.getKey(), event.getKey());
    assertEquals(GREEN_VARIATION, event.getVariation());
    assertEquals(GREEN_VALUE, event.getValue());
    assertEquals(f1.getVersion(), event.getVersion());
    assertEquals(f0.getKey(), event.getPrereqOf());
  }

  @Test
  public void flagReturnsOffVariationAndEventIfPrerequisiteIsNotMet() throws Exception {
    DataModel.FeatureFlag f0 = buildThreeWayFlag("feature")
        .on(true)
        .prerequisites(prerequisite("feature1", GREEN_VARIATION))
        .build();
    DataModel.FeatureFlag f1 = buildRedGreenFlag("feature1")
        .on(true)
        .fallthroughVariation(RED_VARIATION)
        .build();
    Evaluator e = evaluatorBuilder().withStoredFlags(f1).build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(fromValue(OFF_VALUE, OFF_VARIATION, expectedReason), result.getDetails());
    
    assertEquals(1, Iterables.size(result.getPrerequisiteEvents()));
    Event.FeatureRequest event = Iterables.get(result.getPrerequisiteEvents(), 0);
    assertEquals(f1.getKey(), event.getKey());
    assertEquals(RED_VARIATION, event.getVariation());
    assertEquals(RED_VALUE, event.getValue());
    assertEquals(f1.getVersion(), event.getVersion());
    assertEquals(f0.getKey(), event.getPrereqOf());
  }

  @Test
  public void prerequisiteFailedReasonInstanceIsReusedForSamePrerequisite() throws Exception {
    DataModel.FeatureFlag f0 = buildThreeWayFlag("feature")
        .on(true)
        .prerequisites(prerequisite("feature1", GREEN_VARIATION))
        .build();
    Evaluator e = evaluatorBuilder().withNonexistentFlag("feature1").build();
    Evaluator.EvalResult result0 = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    Evaluator.EvalResult result1 = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(expectedReason, result0.getDetails().getReason());
    assertSame(result0.getDetails().getReason(), result1.getDetails().getReason());
  }

  @Test
  public void prerequisiteFailedReasonInstanceCanBeCreatedFromScratch() throws Exception {
    // Normally we will always do the preprocessing step that creates the reason instances ahead of time,
    // but if somehow we didn't, it should create them as needed
    DataModel.FeatureFlag f0 = buildThreeWayFlag("feature")
        .on(true)
        .prerequisites(prerequisite("feature1", GREEN_VARIATION))
        .disablePreprocessing(true)
        .build();
    assertNull(f0.getPrerequisites().get(0).getPrerequisiteFailedReason());
    
    Evaluator e = evaluatorBuilder().withNonexistentFlag("feature1").build();
    Evaluator.EvalResult result0 = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    Evaluator.EvalResult result1 = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(expectedReason, result0.getDetails().getReason());
    assertNotSame(result0.getDetails().getReason(), result1.getDetails().getReason()); // they were created individually
    assertEquals(result0.getDetails().getReason(), result1.getDetails().getReason()); // but they're equal
  }

  @Test
  public void flagReturnsFallthroughVariationAndEventIfPrerequisiteIsMetAndThereAreNoRules() throws Exception {
    DataModel.FeatureFlag f0 = buildThreeWayFlag("feature")
        .on(true)
        .prerequisites(prerequisite("feature1", GREEN_VARIATION))
        .build();
    DataModel.FeatureFlag f1 = buildRedGreenFlag("feature1")
        .on(true)
        .fallthroughVariation(GREEN_VARIATION)
        .version(2)
        .build();
    Evaluator e = evaluatorBuilder().withStoredFlags(f1).build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(FALLTHROUGH_VALUE, FALLTHROUGH_VARIATION, EvaluationReason.fallthrough()), result.getDetails());

    assertEquals(1, Iterables.size(result.getPrerequisiteEvents()));
    Event.FeatureRequest event = Iterables.get(result.getPrerequisiteEvents(), 0);
    assertEquals(f1.getKey(), event.getKey());
    assertEquals(GREEN_VARIATION, event.getVariation());
    assertEquals(GREEN_VALUE, event.getValue());
    assertEquals(f1.getVersion(), event.getVersion());
    assertEquals(f0.getKey(), event.getPrereqOf());
  }

  @Test
  public void multipleLevelsOfPrerequisitesProduceMultipleEvents() throws Exception {
    DataModel.FeatureFlag f0 = buildThreeWayFlag("feature")
        .on(true)
        .prerequisites(prerequisite("feature1", GREEN_VARIATION))
        .build();
    DataModel.FeatureFlag f1 = buildRedGreenFlag("feature1")
        .on(true)
        .prerequisites(prerequisite("feature2", GREEN_VARIATION))
        .fallthroughVariation(GREEN_VARIATION)
        .build();
    DataModel.FeatureFlag f2 = buildRedGreenFlag("feature2")
        .on(true)
        .fallthroughVariation(GREEN_VARIATION)
        .build();
    Evaluator e = evaluatorBuilder().withStoredFlags(f1, f2).build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(FALLTHROUGH_VALUE, FALLTHROUGH_VARIATION, EvaluationReason.fallthrough()), result.getDetails());
    assertEquals(2, Iterables.size(result.getPrerequisiteEvents()));
    
    Event.FeatureRequest event0 = Iterables.get(result.getPrerequisiteEvents(), 0);
    assertEquals(f2.getKey(), event0.getKey());
    assertEquals(GREEN_VARIATION, event0.getVariation());
    assertEquals(GREEN_VALUE, event0.getValue());
    assertEquals(f2.getVersion(), event0.getVersion());
    assertEquals(f1.getKey(), event0.getPrereqOf());

    Event.FeatureRequest event1 = Iterables.get(result.getPrerequisiteEvents(), 1);
    assertEquals(f1.getKey(), event1.getKey());
    assertEquals(GREEN_VARIATION, event1.getVariation());
    assertEquals(GREEN_VALUE, event1.getValue());
    assertEquals(f1.getVersion(), event1.getVersion());
    assertEquals(f0.getKey(), event1.getPrereqOf());
  }
  
  @Test
  public void flagMatchesUserFromTargets() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .targets(target(2, "whoever", "userkey"))
        .build();
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(fromValue(MATCH_VALUE, MATCH_VARIATION, EvaluationReason.targetMatch()), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagMatchesUserFromRules() {
    DataModel.Clause clause0 = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("wrongkey"));
    DataModel.Clause clause1 = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule0 = ruleBuilder().id("ruleid0").clauses(clause0).variation(2).build();
    DataModel.Rule rule1 = ruleBuilder().id("ruleid1").clauses(clause1).variation(2).build();
    
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .rules(rule0, rule1)
        .build();
    
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(fromValue(MATCH_VALUE, MATCH_VARIATION, EvaluationReason.ruleMatch(1, "ruleid1")), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test(expected=RuntimeException.class)
  public void canSimulateErrorUsingTestInstrumentationFlagKey() {
    // Other tests rely on the ability to simulate an exception in this way
    DataModel.FeatureFlag badFlag = flagBuilder(Evaluator.INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION).build();
    BASE_EVALUATOR.evaluate(badFlag, BASE_USER, EventFactory.DEFAULT);
  }
}
