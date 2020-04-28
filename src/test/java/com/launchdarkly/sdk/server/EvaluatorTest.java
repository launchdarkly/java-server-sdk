package com.launchdarkly.sdk.server;

import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.interfaces.Event;

import org.junit.Test;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.EvaluationDetail.fromValue;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.evaluatorBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.prerequisite;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.target;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@SuppressWarnings("javadoc")
public class EvaluatorTest {

  private static LDUser BASE_USER = new LDUser.Builder("x").build();
  
  @Test
  public void flagReturnsOffVariationIfFlagIsOff() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(false)
        .offVariation(1)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("off"), 1, EvaluationReason.off()), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsNullIfFlagIsOffAndOffVariationIsUnspecified() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(false)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.ofNull(), NO_VARIATION, EvaluationReason.off()), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagReturnsErrorIfFlagIsOffAndOffVariationIsTooHigh() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(false)
        .offVariation(999)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsErrorIfFlagIsOffAndOffVariationIsNegative() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(false)
        .offVariation(-1)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagReturnsFallthroughIfFlagIsOnAndThereAreNoRules() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("fall"), 0, EvaluationReason.fallthrough()), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasTooHighVariation() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(fallthroughVariation(999))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasNegativeVariation() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(fallthroughVariation(-1))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsErrorIfFallthroughHasNeitherVariationNorRollout() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(new DataModel.VariationOrRollout(null, null))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagReturnsErrorIfFallthroughHasEmptyRolloutVariationList() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(true)
        .offVariation(1)
        .fallthrough(new DataModel.VariationOrRollout(null, ModelBuilders.emptyRollout()))
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(EvaluationDetail.error(EvaluationReason.ErrorKind.MALFORMED_FLAG, null), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagReturnsOffVariationIfPrerequisiteIsNotFound() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator e = evaluatorBuilder().withNonexistentFlag("feature1").build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(fromValue(LDValue.of("off"), 1, expectedReason), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }

  @Test
  public void flagReturnsOffVariationAndEventIfPrerequisiteIsOff() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    DataModel.FeatureFlag f1 = flagBuilder("feature1")
        .on(false)
        .offVariation(1)
        // note that even though it returns the desired variation, it is still off and therefore not a match
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    Evaluator e = evaluatorBuilder().withStoredFlags(f1).build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(fromValue(LDValue.of("off"), 1, expectedReason), result.getDetails());
    
    assertEquals(1, Iterables.size(result.getPrerequisiteEvents()));
    Event.FeatureRequest event = Iterables.get(result.getPrerequisiteEvents(), 0);
    assertEquals(f1.getKey(), event.getKey());
    assertEquals(LDValue.of("go"), event.getValue());
    assertEquals(f1.getVersion(), event.getVersion());
    assertEquals(f0.getKey(), event.getPrereqOf());
  }

  @Test
  public void flagReturnsOffVariationAndEventIfPrerequisiteIsNotMet() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    DataModel.FeatureFlag f1 = flagBuilder("feature1")
        .on(true)
        .fallthrough(fallthroughVariation(0))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    Evaluator e = evaluatorBuilder().withStoredFlags(f1).build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(fromValue(LDValue.of("off"), 1, expectedReason), result.getDetails());
    
    assertEquals(1, Iterables.size(result.getPrerequisiteEvents()));
    Event.FeatureRequest event = Iterables.get(result.getPrerequisiteEvents(), 0);
    assertEquals(f1.getKey(), event.getKey());
    assertEquals(LDValue.of("nogo"), event.getValue());
    assertEquals(f1.getVersion(), event.getVersion());
    assertEquals(f0.getKey(), event.getPrereqOf());
  }

  @Test
  public void prerequisiteFailedReasonInstanceIsReusedForSamePrerequisite() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    Evaluator e = evaluatorBuilder().withNonexistentFlag("feature1").build();
    Evaluator.EvalResult result0 = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    Evaluator.EvalResult result1 = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    EvaluationReason expectedReason = EvaluationReason.prerequisiteFailed("feature1");
    assertEquals(expectedReason, result0.getDetails().getReason());
    assertSame(result0.getDetails().getReason(), result1.getDetails().getReason());
  }

  @Test
  public void flagReturnsFallthroughVariationAndEventIfPrerequisiteIsMetAndThereAreNoRules() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    DataModel.FeatureFlag f1 = flagBuilder("feature1")
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    Evaluator e = evaluatorBuilder().withStoredFlags(f1).build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("fall"), 0, EvaluationReason.fallthrough()), result.getDetails());

    assertEquals(1, Iterables.size(result.getPrerequisiteEvents()));
    Event.FeatureRequest event = Iterables.get(result.getPrerequisiteEvents(), 0);
    assertEquals(f1.getKey(), event.getKey());
    assertEquals(LDValue.of("go"), event.getValue());
    assertEquals(f1.getVersion(), event.getVersion());
    assertEquals(f0.getKey(), event.getPrereqOf());
  }

  @Test
  public void multipleLevelsOfPrerequisitesProduceMultipleEvents() throws Exception {
    DataModel.FeatureFlag f0 = flagBuilder("feature0")
        .on(true)
        .prerequisites(prerequisite("feature1", 1))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .version(1)
        .build();
    DataModel.FeatureFlag f1 = flagBuilder("feature1")
        .on(true)
        .prerequisites(prerequisite("feature2", 1))
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(2)
        .build();
    DataModel.FeatureFlag f2 = flagBuilder("feature2")
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(LDValue.of("nogo"), LDValue.of("go"))
        .version(3)
        .build();
    Evaluator e = evaluatorBuilder().withStoredFlags(f1, f2).build();
    Evaluator.EvalResult result = e.evaluate(f0, BASE_USER, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("fall"), 0, EvaluationReason.fallthrough()), result.getDetails());
    assertEquals(2, Iterables.size(result.getPrerequisiteEvents()));
    
    Event.FeatureRequest event0 = Iterables.get(result.getPrerequisiteEvents(), 0);
    assertEquals(f2.getKey(), event0.getKey());
    assertEquals(LDValue.of("go"), event0.getValue());
    assertEquals(f2.getVersion(), event0.getVersion());
    assertEquals(f1.getKey(), event0.getPrereqOf());

    Event.FeatureRequest event1 = Iterables.get(result.getPrerequisiteEvents(), 1);
    assertEquals(f1.getKey(), event1.getKey());
    assertEquals(LDValue.of("go"), event1.getValue());
    assertEquals(f1.getVersion(), event1.getVersion());
    assertEquals(f0.getKey(), event1.getPrereqOf());
  }
  
  @Test
  public void flagMatchesUserFromTargets() throws Exception {
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(true)
        .targets(target(2, "whoever", "userkey"))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("on"), 2, EvaluationReason.targetMatch()), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
  
  @Test
  public void flagMatchesUserFromRules() {
    DataModel.Clause clause0 = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("wrongkey"));
    DataModel.Clause clause1 = clause(UserAttribute.KEY, DataModel.Operator.in, LDValue.of("userkey"));
    DataModel.Rule rule0 = ruleBuilder().id("ruleid0").clauses(clause0).variation(2).build();
    DataModel.Rule rule1 = ruleBuilder().id("ruleid1").clauses(clause1).variation(2).build();
    DataModel.FeatureFlag f = featureFlagWithRules("feature", rule0, rule1);
    LDUser user = new LDUser.Builder("userkey").build();
    Evaluator.EvalResult result = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT);
    
    assertEquals(fromValue(LDValue.of("on"), 2, EvaluationReason.ruleMatch(1, "ruleid1")), result.getDetails());
    assertThat(result.getPrerequisiteEvents(), emptyIterable());
  }
 
  private DataModel.FeatureFlag featureFlagWithRules(String flagKey, DataModel.Rule... rules) {
    return flagBuilder(flagKey)
        .on(true)
        .rules(rules)
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(LDValue.of("fall"), LDValue.of("off"), LDValue.of("on"))
        .build();
  }
}
