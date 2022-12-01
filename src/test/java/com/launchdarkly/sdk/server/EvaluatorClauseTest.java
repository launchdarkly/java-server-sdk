package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Segment;

import org.junit.Test;

import static com.launchdarkly.sdk.EvaluationDetail.fromValue;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static com.launchdarkly.sdk.server.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingSegment;
import static com.launchdarkly.sdk.server.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.negateClause;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestUtil.TEST_GSON_INSTANCE;
import static com.launchdarkly.testhelpers.JsonAssertions.assertJsonEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("javadoc")
public class EvaluatorClauseTest extends EvaluatorTestBase {
  private static void assertMatch(Evaluator eval, FeatureFlag flag, LDContext context, boolean expectMatch) {
    assertEquals(LDValue.of(expectMatch), eval.evaluate(flag, context, expectNoPrerequisiteEvals()).getValue());
  }
  
  private static Segment makeSegmentThatMatchesUser(String segmentKey, String userKey) {
    return segmentBuilder(segmentKey).included(userKey).build();
  }
  
  @Test
  public void clauseCanMatchBuiltInAttribute() throws Exception {
    Clause clause = clause("name", Operator.in, LDValue.of("Bob"));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").name("Bob").build();
  
    assertMatch(BASE_EVALUATOR, f, context, true);
  }
  
  @Test
  public void clauseCanMatchCustomAttribute() throws Exception {
    Clause clause = clause("legs", Operator.in, LDValue.of(4));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").set("legs", 4).build();
    
    assertMatch(BASE_EVALUATOR, f, context, true);
  }
  
  @Test
  public void clauseReturnsFalseForMissingAttribute() throws Exception {
    Clause clause = clause("legs", Operator.in, LDValue.of(4));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").name("Bob").build();
    
    assertMatch(BASE_EVALUATOR, f, context, false);
  }

  @Test
  public void clauseMatchesContextValueToAnyOfMultipleValues() throws Exception {
    Clause clause = clause("name", Operator.in, LDValue.of("Bob"), LDValue.of("Carol"));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").name("Carol").build();
    
    assertMatch(BASE_EVALUATOR, f, context, true);
  }

  @Test
  public void clauseMatchesContextValueToAnyOfMultipleValuesWithNonEqualityOperator() throws Exception {
    // We check this separately because of the special preprocessing logic for equality matches.
    Clause clause = clause("name", Operator.contains, LDValue.of("Bob"), LDValue.of("Carol"));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").name("Caroline").build();
    
    assertMatch(BASE_EVALUATOR, f, context, true);
  }

  @Test
  public void clauseMatchesArrayOfContextValuesToClauseValue() throws Exception {
    Clause clause = clause("alias", Operator.in, LDValue.of("Maurice"));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").set("alias",
        LDValue.buildArray().add("Space Cowboy").add("Maurice").build()).build();
    
    assertMatch(BASE_EVALUATOR, f, context, true);
  }

  @Test
  public void clauseFindsNoMatchInArrayOfContextValues() throws Exception {
    Clause clause = clause("alias", Operator.in, LDValue.of("Ma"));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").set("alias",
        LDValue.buildArray().add("Mary").add("May").build()).build();
    
    assertMatch(BASE_EVALUATOR, f, context, false);
  }

  @Test
  public void matchFailsIfContextValueIsAnArrayOfArrays() throws Exception {
    LDValue arrayValue = LDValue.buildArray().add("thing").build();
    LDValue arrayOfArrays = LDValue.buildArray().add(arrayValue).build();
    Clause clause = clause("data", Operator.in, arrayOfArrays);
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").set("data", arrayOfArrays).build();
    
    assertMatch(BASE_EVALUATOR, f, context, false);
  }

  @Test
  public void matchFailsIfContextValueIsAnObject() throws Exception {
    LDValue objectValue = LDValue.buildObject().put("thing", LDValue.of(true)).build();
    Clause clause = clause("data", Operator.in, objectValue);
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").set("data", objectValue).build();
    
    assertMatch(BASE_EVALUATOR, f, context, false);
  }

  @Test
  public void matchFailsIfContextValueIsAnArrayOfObjects() throws Exception {
    LDValue objectValue = LDValue.buildObject().put("thing", LDValue.of(true)).build();
    LDValue arrayOfObjects = LDValue.buildArray().add(objectValue).build();
    Clause clause = clause("data", Operator.in, arrayOfObjects);
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").set("data", arrayOfObjects).build();
    
    assertMatch(BASE_EVALUATOR, f, context, false);
  }

  @Test
  public void clauseReturnsFalseForNullOperator() throws Exception {
    Clause clause = clause("key", null, LDValue.of("key"));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.create("key");
    
    assertMatch(BASE_EVALUATOR, f, context, false);
  }
  
  @Test
  public void clauseCanBeNegatedToReturnFalse() throws Exception {
    Clause clause = negateClause(clause("key", Operator.in, LDValue.of("key")));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").name("Bob").build();
    
    assertMatch(BASE_EVALUATOR, f, context, false);
  }

  @Test
  public void clauseCanBeNegatedToReturnTrue() throws Exception {
    Clause clause = negateClause(clause("key", Operator.in, LDValue.of("other")));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key").name("Bob").build();
    
    assertMatch(BASE_EVALUATOR, f, context, true);
  }

  @Test
  public void clauseWithUnsupportedOperatorStringIsUnmarshalledWithNullOperator() throws Exception {
    // This just verifies that GSON will give us a null in this case instead of throwing an exception,
    // so we fail as gracefully as possible if a new operator type has been added in the application
    // and the SDK hasn't been upgraded yet.
    String badClauseJson = "{\"attribute\":\"name\",\"operator\":\"doesSomethingUnsupported\",\"values\":[\"x\"]}";
    Clause clause = TEST_GSON_INSTANCE.fromJson(badClauseJson, DataModel.Clause.class);
    assertNotNull(clause);
    
    String json = TEST_GSON_INSTANCE.toJson(clause);
    String expectedJson = "{\"attribute\":\"name\",\"values\":[\"x\"],\"negate\":false}";
    assertJsonEquals(expectedJson, json);
  }
  
  @Test
  public void clauseWithNullOperatorDoesNotMatch() throws Exception {
    Clause badClause = clause("name", null, LDValue.of("Bob"));
    FeatureFlag f = booleanFlagWithClauses("flag", badClause);
    LDContext context = LDContext.builder("key").name("Bob").build();
    
    assertMatch(BASE_EVALUATOR, f, context, false);
  }
  
  @Test
  public void clauseWithNullOperatorDoesNotStopSubsequentRuleFromMatching() throws Exception {
    Clause badClause = clause("name", null, LDValue.of("Bob"));
    Rule badRule = ruleBuilder().id("rule1").clauses(badClause).variation(1).build();
    Clause goodClause = clause("name", Operator.in, LDValue.of("Bob"));
    Rule goodRule = ruleBuilder().id("rule2").clauses(goodClause).variation(1).build();
    FeatureFlag f = flagBuilder("feature")
        .on(true)
        .rules(badRule, goodRule)
        .fallthrough(fallthroughVariation(0))
        .offVariation(0)
        .variations(LDValue.of(false), LDValue.of(true))
        .build();
    LDContext context = LDContext.builder("key").name("Bob").build();
    
    EvaluationDetail<LDValue> details = BASE_EVALUATOR.evaluate(f, context, expectNoPrerequisiteEvals()).getAnyType();
    assertEquals(fromValue(LDValue.of(true), 1, EvaluationReason.ruleMatch(1, "rule2")), details);
  }

  @Test
  public void clauseCanGetValueWithAttributeReference() throws Exception {
    Clause clause = clause(null, AttributeRef.fromPath("/address/city"), Operator.in, LDValue.of("Oakland"));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.builder("key")
        .set("address", LDValue.parse("{\"city\":\"Oakland\",\"state\":\"CA\"}"))
        .build();
    
    assertMatch(BASE_EVALUATOR, f, context, true);
  }
  
  @Test
  public void clauseMatchUsesContextKind() throws Exception {
    Clause clause = clause(ContextKind.of("company"), "name", Operator.in, LDValue.of("Catco"));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context1 = LDContext.builder("cc").kind("company").name("Catco").build();
    LDContext context2 = LDContext.builder("l").name("Lucy").build();
    LDContext context3 = LDContext.createMulti(context1, context2);

    assertMatch(BASE_EVALUATOR, f, context1, true);
    assertMatch(BASE_EVALUATOR, f, context2, false);
    assertMatch(BASE_EVALUATOR, f, context3, true);
  }

  @Test
  public void clauseMatchByKindAttribute() throws Exception {
    Clause clause = clause(null, "kind", Operator.startsWith, LDValue.of("a"));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context1 = LDContext.create("key");
    LDContext context2 = LDContext.create(ContextKind.of("ab"), "key");
    LDContext context3 = LDContext.createMulti(
        LDContext.create(ContextKind.of("cd"), "key"),
        LDContext.create(ContextKind.of("ab"), "key"));
    
    assertMatch(BASE_EVALUATOR, f, context1, false);
    assertMatch(BASE_EVALUATOR, f, context2, true);
    assertMatch(BASE_EVALUATOR, f, context3, true);
  }
  
  @Test
  public void clauseReturnsMalformedFlagErrorForAttributeNotSpecified() {
    Clause clause = clause(null, (AttributeRef)null, Operator.in, LDValue.of(4));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.create("key");

    EvalResult result = BASE_EVALUATOR.evaluate(f, context, expectNoPrerequisiteEvals());
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }

  @Test
  public void clauseReturnsMalformedFlagErrorForMalformedAttributeReference() {
    Clause clause = clause(null, AttributeRef.fromPath("///"), Operator.in, LDValue.of(4));
    FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.create("key");

    EvalResult result = BASE_EVALUATOR.evaluate(f, context, expectNoPrerequisiteEvals());
    assertEquals(EvalResult.error(EvaluationReason.ErrorKind.MALFORMED_FLAG), result);
  }
  
  @Test
  public void testSegmentMatchClauseRetrievesSegmentFromStore() throws Exception {
    String segmentKey = "segkey";
    Clause clause = clauseMatchingSegment(segmentKey);
    FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    Segment segment = makeSegmentThatMatchesUser(segmentKey, "foo");
    LDContext context = LDContext.create("foo");
    
    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    assertMatch(e, flag, context, true);
  }

  @Test
  public void testSegmentMatchClauseFallsThroughIfSegmentNotFound() throws Exception {
    String segmentKey = "segkey";
    Clause clause = clauseMatchingSegment(segmentKey);
    FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    LDContext context = LDContext.create("foo");
    
    Evaluator e = evaluatorBuilder().withNonexistentSegment(segmentKey).build();
    assertMatch(e, flag, context, false);
  }

  @Test
  public void testSegmentMatchClauseIgnoresNonStringValues() throws Exception {
    String segmentKey = "segkey";
    Clause clause = clause(null, (AttributeRef)null, Operator.segmentMatch,
        LDValue.of(123), LDValue.of(segmentKey));
    FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    Segment segment = makeSegmentThatMatchesUser(segmentKey, "foo");
    LDContext context = LDContext.create("foo");

    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    assertMatch(e, flag, context, true);
  }
}
