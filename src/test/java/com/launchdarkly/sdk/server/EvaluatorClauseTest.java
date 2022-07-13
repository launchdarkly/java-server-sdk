package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;

import org.junit.Test;

import static com.launchdarkly.sdk.EvaluationDetail.fromValue;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.evaluatorBuilder;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static com.launchdarkly.sdk.server.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingSegment;
import static com.launchdarkly.sdk.server.ModelBuilders.fallthroughVariation;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestUtil.TEST_GSON_INSTANCE;
import static com.launchdarkly.testhelpers.JsonAssertions.assertJsonEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("javadoc")
public class EvaluatorClauseTest {
  private static void assertMatch(Evaluator eval, DataModel.FeatureFlag flag, LDUser user, boolean expectMatch) {
    assertEquals(LDValue.of(expectMatch), eval.evaluate(flag, user, expectNoPrerequisiteEvals()).getValue());
  }
  
  private static DataModel.Segment makeSegmentThatMatchesUser(String segmentKey, String userKey) {
    return segmentBuilder(segmentKey).included(userKey).build();
  }
  
  @Test
  public void clauseCanMatchBuiltInAttribute() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.NAME, DataModel.Operator.in, LDValue.of("Bob"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
  
    assertMatch(BASE_EVALUATOR, f, user, true);
  }
  
  @Test
  public void clauseCanMatchCustomAttribute() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.forName("legs"), DataModel.Operator.in, LDValue.of(4));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").custom("legs", 4).build();
    
    assertMatch(BASE_EVALUATOR, f, user, true);
  }
  
  @Test
  public void clauseReturnsFalseForMissingAttribute() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.forName("legs"), DataModel.Operator.in, LDValue.of(4));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertMatch(BASE_EVALUATOR, f, user, false);
  }

  @Test
  public void clauseMatchesUserValueToAnyOfMultipleValues() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.NAME, DataModel.Operator.in, LDValue.of("Bob"), LDValue.of("Carol"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Carol").build();
    
    assertMatch(BASE_EVALUATOR, f, user, true);
  }

  @Test
  public void clauseMatchesUserValueToAnyOfMultipleValuesWithNonEqualityOperator() throws Exception {
    // We check this separately because of the special preprocessing logic for equality matches.
    DataModel.Clause clause = clause(UserAttribute.NAME, DataModel.Operator.contains, LDValue.of("Bob"), LDValue.of("Carol"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Caroline").build();
    
    assertMatch(BASE_EVALUATOR, f, user, true);
  }

  @Test
  public void clauseMatchesArrayOfUserValuesToClauseValue() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.forName("alias"), DataModel.Operator.in, LDValue.of("Maurice"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").custom("alias",
        LDValue.buildArray().add("Space Cowboy").add("Maurice").build()).build();
    
    assertMatch(BASE_EVALUATOR, f, user, true);
  }

  @Test
  public void clauseFindsNoMatchInArrayOfUserValues() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.forName("alias"), DataModel.Operator.in, LDValue.of("Ma"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").custom("alias",
        LDValue.buildArray().add("Mary").add("May").build()).build();
    
    assertMatch(BASE_EVALUATOR, f, user, false);
  }

  @Test
  public void userValueMustNotBeAnArrayOfArrays() throws Exception {
    LDValue arrayValue = LDValue.buildArray().add("thing").build();
    LDValue arrayOfArrays = LDValue.buildArray().add(arrayValue).build();
    DataModel.Clause clause = clause(UserAttribute.forName("data"), DataModel.Operator.in, arrayOfArrays);
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").custom("data", arrayOfArrays).build();
    
    assertMatch(BASE_EVALUATOR, f, user, false);
  }

  @Test
  public void userValueMustNotBeAnObject() throws Exception {
    LDValue objectValue = LDValue.buildObject().put("thing", LDValue.of(true)).build();
    DataModel.Clause clause = clause(UserAttribute.forName("data"), DataModel.Operator.in, objectValue);
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").custom("data", objectValue).build();
    
    assertMatch(BASE_EVALUATOR, f, user, false);
  }

  @Test
  public void userValueMustNotBeAnArrayOfObjects() throws Exception {
    LDValue objectValue = LDValue.buildObject().put("thing", LDValue.of(true)).build();
    LDValue arrayOfObjects = LDValue.buildArray().add(objectValue).build();
    DataModel.Clause clause = clause(UserAttribute.forName("data"), DataModel.Operator.in, arrayOfObjects);
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").custom("data", arrayOfObjects).build();
    
    assertMatch(BASE_EVALUATOR, f, user, false);
  }

  @Test
  public void clauseReturnsFalseForNullOperator() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.KEY, null, LDValue.of("key"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser("key");
    
    assertMatch(BASE_EVALUATOR, f, user, false);
  }
  
  @Test
  public void clauseCanBeNegatedToReturnFalse() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, true, LDValue.of("key"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertMatch(BASE_EVALUATOR, f, user, false);
  }

  @Test
  public void clauseCanBeNegatedToReturnTrue() throws Exception {
    DataModel.Clause clause = clause(UserAttribute.KEY, DataModel.Operator.in, true, LDValue.of("other"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertMatch(BASE_EVALUATOR, f, user, true);
  }

  @Test
  public void clauseWithUnsupportedOperatorStringIsUnmarshalledWithNullOperator() throws Exception {
    // This just verifies that GSON will give us a null in this case instead of throwing an exception,
    // so we fail as gracefully as possible if a new operator type has been added in the application
    // and the SDK hasn't been upgraded yet.
    String badClauseJson = "{\"attribute\":\"name\",\"operator\":\"doesSomethingUnsupported\",\"values\":[\"x\"]}";
    DataModel.Clause clause = TEST_GSON_INSTANCE.fromJson(badClauseJson, DataModel.Clause.class);
    assertNotNull(clause);
    
    String json = TEST_GSON_INSTANCE.toJson(clause);
    String expectedJson = "{\"attribute\":\"name\",\"values\":[\"x\"],\"negate\":false}";
    assertJsonEquals(expectedJson, json);
  }
  
  @Test
  public void clauseWithNullOperatorDoesNotMatch() throws Exception {
    DataModel.Clause badClause = clause(UserAttribute.NAME, null, LDValue.of("Bob"));
    DataModel.FeatureFlag f = booleanFlagWithClauses("flag", badClause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertMatch(BASE_EVALUATOR, f, user, false);
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
    
    EvaluationDetail<LDValue> details = BASE_EVALUATOR.evaluate(f, user, expectNoPrerequisiteEvals()).getAnyType();
    assertEquals(fromValue(LDValue.of(true), 1, EvaluationReason.ruleMatch(1, "rule2")), details);
  }
  
  @Test
  public void testSegmentMatchClauseRetrievesSegmentFromStore() throws Exception {
    String segmentKey = "segkey";
    DataModel.Clause clause = clauseMatchingSegment(segmentKey);
    DataModel.FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    DataModel.Segment segment = makeSegmentThatMatchesUser(segmentKey, "foo");
    LDUser user = new LDUser.Builder("foo").build();
    
    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    assertMatch(e, flag, user, true);
  }

  @Test
  public void testSegmentMatchClauseFallsThroughIfSegmentNotFound() throws Exception {
    String segmentKey = "segkey";
    DataModel.Clause clause = clauseMatchingSegment(segmentKey);
    DataModel.FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    LDUser user = new LDUser.Builder("foo").build();
    
    Evaluator e = evaluatorBuilder().withNonexistentSegment(segmentKey).build();
    assertMatch(e, flag, user, false);
  }

  @Test
  public void testSegmentMatchClauseIgnoresNonStringValues() throws Exception {
    String segmentKey = "segkey";
    DataModel.Clause clause = clause(null, null, DataModel.Operator.segmentMatch, false,
        LDValue.of(123), LDValue.of(segmentKey));
    DataModel.FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    DataModel.Segment segment = makeSegmentThatMatchesUser(segmentKey, "foo");
    LDUser user = new LDUser.Builder("foo").build();

    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    assertMatch(e, flag, user, true);
  }
}
