package com.launchdarkly.sdk.server;

import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.VariationOrRollout;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;
import com.launchdarkly.sdk.server.EvaluatorTestUtil.PrereqEval;
import com.launchdarkly.sdk.server.EvaluatorTestUtil.PrereqRecorder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.FALLTHROUGH_VALUE;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.FALLTHROUGH_VARIATION;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.GREEN_VALUE;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.GREEN_VARIATION;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.MATCH_VALUE;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.MATCH_VARIATION;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.OFF_VALUE;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.OFF_VARIATION;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.RED_VALUE;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.RED_VARIATION;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.buildRedGreenFlag;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.buildThreeWayFlag;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingContext;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.prerequisite;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class EvaluatorTest extends EvaluatorTestBase {
  private static final LDContext BASE_USER = LDContext.create("x");

  private static Rollout buildRollout(boolean isExperiment, boolean untrackedVariations) {
    List<WeightedVariation> variations = new ArrayList<>();
    variations.add(new WeightedVariation(1, 50000, untrackedVariations));
    variations.add(new WeightedVariation(2, 50000, untrackedVariations));
    RolloutKind kind = isExperiment ? RolloutKind.experiment : RolloutKind.rollout;
    Integer seed = 123;
    Rollout rollout = new Rollout(null, variations, null, kind, seed);
    return rollout;
  }

  @Test
  public void flagReturnsOffVariationIfFlagIsOff() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(false)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.of(OFF_VALUE, OFF_VARIATION, EvaluationReason.off()), result);
  }

  @Test
  public void flagReturnsNullIfFlagIsOffAndOffVariationIsUnspecified() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(false)
        .offVariation(null)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.of(LDValue.ofNull(), NO_VARIATION, EvaluationReason.off()), result);
  }
  
  @Test
  public void flagReturnsErrorIfFlagIsOffAndOffVariationIsTooHigh() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(false)
        .offVariation(999)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }

  @Test
  public void flagReturnsErrorIfFlagIsOffAndOffVariationIsNegative() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(false)
        .offVariation(-1)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }
  
  @Test
  public void flagReturnsInExperimentForFallthroughWhenInExperimentVariation() throws Exception {
    Rollout rollout = buildRollout(true, false);
    VariationOrRollout vr = new VariationOrRollout(null, rollout);

    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthrough(vr)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
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
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
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
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assert(!result.getReason().isInExperiment());
  }

  @Test
  public void flagReturnsInExperimentForRuleMatchWhenInExperimentVariation() throws Exception {
    Rollout rollout = buildRollout(true, false);

    DataModel.Rule rule = ruleBuilder().id("ruleid0").clauses(clauseMatchingContext(BASE_USER))
        .rollout(rollout).build();

    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .rules(rule)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assert(result.getReason().isInExperiment());
  }

  @Test
  public void flagReturnsNotInExperimentForRuleMatchWhenNotInExperimentVariation() throws Exception {
    Rollout rollout = buildRollout(true, true);
    
    DataModel.Rule rule = ruleBuilder().id("ruleid0").clauses(clauseMatchingContext(BASE_USER))
        .rollout(rollout).build();

    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .rules(rule)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assert(!result.getReason().isInExperiment());
  }

  @Test
  public void flagReturnsNotInExperimentWhenContextKindIsNotFound() throws Exception {
    Rollout rollout = new Rollout(
        ContextKind.of("nonexistent"),
        Arrays.asList(
            new WeightedVariation(0, 1, false),
            new WeightedVariation(1, 99999, false)
            ),
        null,
        RolloutKind.experiment,
        null);
    
    DataModel.Rule rule = ruleBuilder().id("ruleid0").clauses(clauseMatchingContext(BASE_USER))
        .rollout(rollout).build();
    DataModel.FeatureFlag flagWithRule = buildThreeWayFlag("feature")
        .on(true)
        .rules(rule)
        .build();
    EvalResult result1 = BASE_EVALUATOR.evaluate(flagWithRule, BASE_USER, expectNoPrerequisiteEvals());
    assert(!result1.getReason().isInExperiment());

    DataModel.FeatureFlag flagWithFallthrough = buildThreeWayFlag("feature")
        .on(true)
        .fallthrough(rollout)
        .rules(rule)
        .build();
    EvalResult result2 = BASE_EVALUATOR.evaluate(flagWithFallthrough, BASE_USER, expectNoPrerequisiteEvals());
    assert(!result2.getReason().isInExperiment());

  }
  
  @Test
  public void flagReturnsFallthroughIfFlagIsOnAndThereAreNoRules() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.of(FALLTHROUGH_VALUE, FALLTHROUGH_VARIATION, EvaluationReason.fallthrough()), result);
  }

  @Test
  public void fallthroughResultHasForceReasonTrackingTrueIfTrackEventsFallthroughIstrue() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .trackEventsFallthrough(true)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(
        EvalResult.of(FALLTHROUGH_VALUE, FALLTHROUGH_VARIATION, EvaluationReason.fallthrough())
          .withForceReasonTracking(true),
        result);
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasTooHighVariation() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthroughVariation(999)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasNegativeVariation() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthroughVariation(-1)
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasNeitherVariationNorRollout() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthrough(new DataModel.VariationOrRollout(null, null))
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }
  
  @Test
  public void flagReturnsErrorIfFallthroughHasEmptyRolloutVariationList() throws Exception {
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .fallthrough(new DataModel.VariationOrRollout(null, ModelBuilders.emptyRollout()))
        .build();
    EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }
  
  @Test
  public void flagReturnsOffVariationIfPrerequisiteIsNotFound() throws Exception {
    DataModel.FeatureFlag f0 = buildThreeWayFlag("feature")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .build();
    Evaluator e = evaluatorBuilder().withNonexistentFlag("feature1").build();
    EvalResult result = e.evaluate(f0, BASE_USER, expectNoPrerequisiteEvals());
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(EvalResult.of(OFF_VALUE, OFF_VARIATION, expectedReason), result);
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
    PrereqRecorder recordPrereqs = new PrereqRecorder();
    EvalResult result = e.evaluate(f0, BASE_USER, recordPrereqs);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(EvalResult.of(OFF_VALUE, OFF_VARIATION, expectedReason), result);
    
    assertEquals(1, Iterables.size(recordPrereqs.evals));
    PrereqEval eval = recordPrereqs.evals.get(0);
    assertEquals(f1, eval.flag);
    assertEquals(f0, eval.prereqOfFlag);
    assertEquals(GREEN_VARIATION, eval.result.getVariationIndex());
    assertEquals(GREEN_VALUE, eval.result.getValue());
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
    PrereqRecorder recordPrereqs = new PrereqRecorder();
    EvalResult result = e.evaluate(f0, BASE_USER, recordPrereqs);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(EvalResult.of(OFF_VALUE, OFF_VARIATION, expectedReason), result);
    
    assertEquals(1, Iterables.size(recordPrereqs.evals));
    PrereqEval eval = recordPrereqs.evals.get(0);
    assertEquals(f1, eval.flag);
    assertEquals(f0, eval.prereqOfFlag);
    assertEquals(RED_VARIATION, eval.result.getVariationIndex());
    assertEquals(RED_VALUE, eval.result.getValue());
  }

  @Test
  public void prerequisiteFailedResultInstanceIsReusedForSamePrerequisite() throws Exception {
    DataModel.FeatureFlag f0 = buildThreeWayFlag("feature")
        .on(true)
        .prerequisites(prerequisite("feature1", GREEN_VARIATION))
        .build();
    Evaluator e = evaluatorBuilder().withNonexistentFlag("feature1").build();
    EvalResult result0 = e.evaluate(f0, BASE_USER, expectNoPrerequisiteEvals());
    EvalResult result1 = e.evaluate(f0, BASE_USER, expectNoPrerequisiteEvals());
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(expectedReason, result0.getReason());
    assertSame(result0, result1);
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
    assertNull(f0.getPrerequisites().get(0).preprocessed);
    
    Evaluator e = evaluatorBuilder().withNonexistentFlag("feature1").build();
    EvalResult result0 = e.evaluate(f0, BASE_USER, expectNoPrerequisiteEvals());
    EvalResult result1 = e.evaluate(f0, BASE_USER, expectNoPrerequisiteEvals());
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(expectedReason, result0.getReason());
    assertNotSame(result0.getReason(), result1.getReason()); // they were created individually
    assertEquals(result0.getReason(), result1.getReason()); // but they're equal
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
    PrereqRecorder recordPrereqs = new PrereqRecorder();
    EvalResult result = e.evaluate(f0, BASE_USER, recordPrereqs);
    
    assertEquals(EvalResult.of(FALLTHROUGH_VALUE, FALLTHROUGH_VARIATION, EvaluationReason.fallthrough()), result);

    assertEquals(1, Iterables.size(recordPrereqs.evals));
    PrereqEval eval = recordPrereqs.evals.get(0);
    assertEquals(f1, eval.flag);
    assertEquals(f0, eval.prereqOfFlag);
    assertEquals(GREEN_VARIATION, eval.result.getVariationIndex());
    assertEquals(GREEN_VALUE, eval.result.getValue());
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
    PrereqRecorder recordPrereqs = new PrereqRecorder();
    EvalResult result = e.evaluate(f0, BASE_USER, recordPrereqs);
    
    assertEquals(EvalResult.of(FALLTHROUGH_VALUE, FALLTHROUGH_VARIATION, EvaluationReason.fallthrough()), result);

    assertEquals(2, Iterables.size(recordPrereqs.evals));
    
    PrereqEval eval0 = recordPrereqs.evals.get(0);
    assertEquals(f2, eval0.flag);
    assertEquals(f1, eval0.prereqOfFlag);
    assertEquals(GREEN_VARIATION, eval0.result.getVariationIndex());
    assertEquals(GREEN_VALUE, eval0.result.getValue());

    PrereqEval eval1 = recordPrereqs.evals.get(1);
    assertEquals(f1, eval1.flag);
    assertEquals(f0, eval1.prereqOfFlag);
    assertEquals(GREEN_VARIATION, eval1.result.getVariationIndex());
    assertEquals(GREEN_VALUE, eval1.result.getValue());
  }
  
  @Test
  public void flagMatchesUserFromRules() {
    DataModel.Clause clause0 = clause("key", DataModel.Operator.in, LDValue.of("wrongkey"));
    DataModel.Clause clause1 = clause("key", DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule0 = ruleBuilder().id("ruleid0").clauses(clause0).variation(2).build();
    DataModel.Rule rule1 = ruleBuilder().id("ruleid1").clauses(clause1).variation(2).build();
    
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .rules(rule0, rule1)
        .build();
    
    LDContext user = LDContext.create("userkey");
    EvalResult result = BASE_EVALUATOR.evaluate(f, user, expectNoPrerequisiteEvals());
    
    assertEquals(EvalResult.of(MATCH_VALUE, MATCH_VARIATION, EvaluationReason.ruleMatch(1, "ruleid1")), result);
  }

  @Test
  public void ruleMatchReasonHasTrackReasonTrueIfRuleLevelTrackEventsIsTrue() {
    DataModel.Clause clause0 = clause("key", DataModel.Operator.in, LDValue.of("wrongkey"));
    DataModel.Clause clause1 = clause("key", DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule0 = ruleBuilder().id("ruleid0").clauses(clause0).variation(2).build();
    DataModel.Rule rule1 = ruleBuilder().id("ruleid1").clauses(clause1).variation(2)
        .trackEvents(true).build();
    
    DataModel.FeatureFlag f = buildThreeWayFlag("feature")
        .on(true)
        .rules(rule0, rule1)
        .build();
    
    LDContext user = LDContext.create("userkey");
    EvalResult result = BASE_EVALUATOR.evaluate(f, user, expectNoPrerequisiteEvals());
    
    assertEquals(
        EvalResult.of(MATCH_VALUE, MATCH_VARIATION, EvaluationReason.ruleMatch(1, "ruleid1"))
          .withForceReasonTracking(true),
        result);
  }
  
  @Test(expected=RuntimeException.class)
  public void canSimulateErrorUsingTestInstrumentationFlagKey() {
    // Other tests rely on the ability to simulate an exception in this way
    DataModel.FeatureFlag badFlag = flagBuilder(Evaluator.INVALID_FLAG_KEY_THAT_THROWS_EXCEPTION).build();
    BASE_EVALUATOR.evaluate(badFlag, BASE_USER, expectNoPrerequisiteEvals());
  }
}
