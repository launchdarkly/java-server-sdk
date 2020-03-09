package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import static com.launchdarkly.client.EvaluationDetail.fromValue;
import static com.launchdarkly.client.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.client.EvaluatorTestUtil.evaluatorBuilder;
import static com.launchdarkly.client.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.client.ModelBuilders.clause;
import static com.launchdarkly.client.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.client.ModelBuilders.flagBuilder;
import static com.launchdarkly.client.ModelBuilders.ruleBuilder;
import static com.launchdarkly.client.ModelBuilders.segmentBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("javadoc")
public class EvaluatorClauseTest {
  @Test
  public void clauseCanMatchBuiltInAttribute() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.NAME, DataModel.Operator.in, LDValue.of("Bob"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(LDValue.of(true), BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseCanMatchCustomAttribute() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.forName("legs"), DataModel.Operator.in, LDValue.of(4));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").custom("legs", 4).build();
    
    assertEquals(LDValue.of(true), BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseReturnsFalseForMissingAttribute() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.forName("legs"), DataModel.Operator.in, LDValue.of(4));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(LDValue.of(false), BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseCanBeNegated() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.NAME, DataModel.Operator.in, true, LDValue.of("Bob"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(LDValue.of(false), BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseWithUnsupportedOperatorStringIsUnmarshalledWithNullOperator() throws Exception {
    // This just verifies that GSON will give us a null in this case instead of throwing an exception,
    // so we fail as gracefully as possible if a new operator type has been added in the application
    // and the SDK hasn't been upgraded yet.
    String badClauseJson = "{\"attribute\":\"name\",\"operator\":\"doesSomethingUnsupported\",\"values\":[\"x\"]}";
    Gson gson = new Gson();
    DataModel.Clause clause = gson.fromJson(badClauseJson, DataModel.Clause.class);
    assertNotNull(clause);
    
    JsonElement json = gson.toJsonTree(clause);
    String expectedJson = "{\"attribute\":\"name\",\"values\":[\"x\"],\"negate\":false}";
    assertEquals(gson.fromJson(expectedJson, JsonElement.class), json);
  }
  
  @Test
  public void clauseWithNullOperatorDoesNotMatch() throws Exception {
    DataModel.Clause badClause = clause(UserAttribute.NAME, null, LDValue.of("Bob"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", badClause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(LDValue.of(false), BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT).getDetails().getValue());
  }
  
  @Test
  public void clauseWithNullOperatorDoesNotStopSubsequentRuleFromMatching() throws Exception {
    DataModel.Clause badClause = clause(UserAttribute.NAME, null, LDValue.of("Bob"));
    DataModel.Rule badRule = ruleBuilder().id("rule1").clauses(badClause).variation(1).build();
    DataModel.Clause goodClause = clause(UserAttribute.NAME, DataModel.Operator.in, LDValue.of("Bob"));
    DataModel.Rule goodRule = ruleBuilder().id("rule2").clauses(goodClause).variation(1).build();
    DataModel.FeatureFlag f = flagBuilder("feature")
        .on(true)
        .rules(badRule, goodRule)
        .fallthrough(fallthroughVariation(0))
        .offVariation(0)
        .variations(LDValue.of(false), LDValue.of(true))
        .build();
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    EvaluationDetail<LDValue> details = BASE_EVALUATOR.evaluate(f, user, EventFactory.DEFAULT).getDetails();
    assertEquals(fromValue(LDValue.of(true), 1, EvaluationReason.ruleMatch(1, "rule2")), details);
  }
  
  @Test
  public void testSegmentMatchClauseRetrievesSegmentFromStore() throws Exception {
    DataModel.Segment segment = segmentBuilder("segkey")
        .included("foo")
        .version(1)
        .build();
    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    
    DataModel.FeatureFlag flag = segmentMatchBooleanFlag("segkey");
    LDUser user = new LDUser.Builder("foo").build();
    
    Evaluator.EvalResult result = e.evaluate(flag, user, EventFactory.DEFAULT);
    assertEquals(LDValue.of(true), result.getDetails().getValue());
  }

  @Test
  public void testSegmentMatchClauseFallsThroughIfSegmentNotFound() throws Exception {
    DataModel.FeatureFlag flag = segmentMatchBooleanFlag("segkey");
    LDUser user = new LDUser.Builder("foo").build();
    
    Evaluator e = evaluatorBuilder().withNonexistentSegment("segkey").build();
    Evaluator.EvalResult result = e.evaluate(flag, user, EventFactory.DEFAULT);
    assertEquals(LDValue.of(false), result.getDetails().getValue());
  }
  
  private DataModel.FeatureFlag segmentMatchBooleanFlag(String segmentKey) {
    DataModel.Clause clause = clause(null, DataModel.Operator.segmentMatch, LDValue.of(segmentKey));
    return booleanFlagWithClauses("flag", clause);
  }
}
