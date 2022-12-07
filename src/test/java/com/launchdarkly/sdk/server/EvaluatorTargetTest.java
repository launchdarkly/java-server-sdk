package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.ModelBuilders.FlagBuilder;

import org.junit.Test;

import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.target;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class EvaluatorTargetTest {
  private static final int FALLTHROUGH_VAR = 0, MATCH_VAR_1 = 1, MATCH_VAR_2 = 2;
  private static final LDValue[] VARIATIONS = new LDValue[] {
      LDValue.of("fallthrough"), LDValue.of("match1"), LDValue.of("match2")
  };
  private static final ContextKind CAT_KIND = ContextKind.of("cat");
  private static final ContextKind DOG_KIND = ContextKind.of("dog");

  @Test
  public void userTargetsOnly() throws Exception {
    FeatureFlag f = baseFlagBuilder()
        .targets(
            target(MATCH_VAR_1, "c"),
            target(MATCH_VAR_2, "b", "a")
            )
        .build(); 
    
    expectMatch(f, user("a"), MATCH_VAR_2);
    expectMatch(f, user("b"), MATCH_VAR_2);
    expectMatch(f, user("c"), MATCH_VAR_1);
    expectFallthrough(f, user("z"));
    
    // in a multi-kind context, these targets match only the key for the user kind 
    expectMatch(f, LDContext.createMulti(dog("b"), user("a")), MATCH_VAR_2);
    expectMatch(f, LDContext.createMulti(dog("a"), user("c")), MATCH_VAR_1);
    expectFallthrough(f, LDContext.createMulti(dog("b"), user("z")));
    expectFallthrough(f, LDContext.createMulti(dog("a"), cat("b")));
  }
  
  @Test
  public void userTargetsAndContextTargets() throws Exception {
    FeatureFlag f = baseFlagBuilder()
        .targets(
            target(MATCH_VAR_1, "c"),
            target(MATCH_VAR_2, "b", "a")
            )
        .contextTargets(
            target(DOG_KIND, MATCH_VAR_1, "a", "b"),
            target(DOG_KIND, MATCH_VAR_2, "c"),
            target(ContextKind.DEFAULT, MATCH_VAR_1),
            target(ContextKind.DEFAULT, MATCH_VAR_2)
            )
        .build(); 
    
    expectMatch(f, user("a"), MATCH_VAR_2);
    expectMatch(f, user("b"), MATCH_VAR_2);
    expectMatch(f, user("c"), MATCH_VAR_1);
    expectFallthrough(f, user("z"));
    
    expectMatch(f, LDContext.createMulti(dog("b"), user("a")), MATCH_VAR_1); // the "dog" target takes precedence due to ordering
    expectMatch(f, LDContext.createMulti(dog("z"), user("a")), MATCH_VAR_2); // "dog" targets don't match, continue to "user" targets
    expectFallthrough(f, LDContext.createMulti(dog("x"), user("z"))); // nothing matches
    expectMatch(f, LDContext.createMulti(dog("a"), cat("b")), MATCH_VAR_1);
  }

  private static FlagBuilder baseFlagBuilder() {
    return flagBuilder("feature").on(true).variations(VARIATIONS)
        .fallthroughVariation(FALLTHROUGH_VAR).offVariation(FALLTHROUGH_VAR);
  }
  
  private static void expectMatch(FeatureFlag f, LDContext c, int v) {
    EvalResult result = BASE_EVALUATOR.evaluate(f, c, expectNoPrerequisiteEvals());
    assertThat(result.getVariationIndex(), equalTo(v));
    assertThat(result.getValue(), equalTo(VARIATIONS[v]));
    assertThat(result.getReason(), equalTo(EvaluationReason.targetMatch()));
  }

  private static void expectFallthrough(FeatureFlag f, LDContext c) {
    EvalResult result = BASE_EVALUATOR.evaluate(f, c, expectNoPrerequisiteEvals());
    assertThat(result.getVariationIndex(), equalTo(FALLTHROUGH_VAR));
    assertThat(result.getValue(), equalTo(VARIATIONS[FALLTHROUGH_VAR]));
    assertThat(result.getReason(), equalTo(EvaluationReason.fallthrough()));    
  }

  private static LDContext user(String key) {
    return LDContext.create(key);
  }

  private static LDContext cat(String key) {
    return LDContext.create(CAT_KIND, key);
  }

  private static LDContext dog(String key) {
    return LDContext.create(DOG_KIND, key);
  }
}
