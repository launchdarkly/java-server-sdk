package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;
import com.launchdarkly.sdk.server.ModelBuilders.SegmentBuilder;

import org.junit.Test;

import static com.launchdarkly.sdk.server.EvaluatorBucketing.computeBucketValue;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static com.launchdarkly.sdk.server.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingContext;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingSegment;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.negateClause;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentRuleBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class EvaluatorSegmentMatchTest extends EvaluatorTestBase {
  private static final String SEGMENT_KEY = "segmentkey";
  private static final String ARBITRARY_SALT = "abcdef";
  private static final int maxWeight = 100000;
  
  @Test
  public void explicitIncludeUser() {
    LDContext c = LDContext.create("foo");
    Segment s = baseSegmentBuilder()
        .included(c.getKey())
        .build();
    
    assertTrue(segmentMatchesContext(s, c));
  }
  
  @Test
  public void explicitExcludeUser() {
    LDContext c = LDContext.create("foo");
    Segment s = baseSegmentBuilder()
        .excluded(c.getKey())
        .rules(segmentRuleBuilder().clauses(clauseMatchingContext(c)).build())
        .build();
    
    assertFalse(segmentMatchesContext(s, c));
  }
  
  @Test
  public void explicitIncludeHasPrecedence() {
    LDContext c = LDContext.create("foo");
    Segment s = baseSegmentBuilder()
        .included(c.getKey())
        .excluded(c.getKey())
        .build();
    
    assertTrue(segmentMatchesContext(s, c));
  }
  
  @Test
  public void includedKeyForContextKind() {
    ContextKind kind1 = ContextKind.of("kind1");
    String key = "foo";
    LDContext c1 = LDContext.create(key);
    LDContext c2 = LDContext.create(kind1, key);
    LDContext c3 = LDContext.createMulti(c1, c2);

    Segment s = baseSegmentBuilder()
        .includedContexts(kind1, key)
        .build();
    
    assertFalse(segmentMatchesContext(s, c1));
    assertTrue(segmentMatchesContext(s, c2));
    assertTrue(segmentMatchesContext(s, c3));
  }

  @Test
  public void excludedKeyForContextKind() {
    ContextKind kind1 = ContextKind.of("kind1");
    String key = "foo";
    LDContext c1 = LDContext.create(key);
    LDContext c2 = LDContext.create(kind1, key);
    LDContext c3 = LDContext.createMulti(c1, c2);

    Segment s = baseSegmentBuilder()
        .excludedContexts(kind1, key)
        .rules(
            segmentRuleBuilder().clauses(clauseMatchingContext(c1)).build(),
            segmentRuleBuilder().clauses(clauseMatchingContext(c2)).build(),
            segmentRuleBuilder().clauses(clauseMatchingContext(c3)).build()
            )
        .build();
    
    assertTrue(segmentMatchesContext(s, c1)); // rule matched, wasn't excluded
    assertFalse(segmentMatchesContext(s, c2)); // rule matched but was excluded
    assertFalse(segmentMatchesContext(s, c3)); // rule matched but was excluded
  }
  
  @Test
  public void matchingRuleWithFullRollout() {
    LDContext c = LDContext.create("foo");
    Clause clause = clauseMatchingContext(c);
    SegmentRule rule = segmentRuleBuilder().clauses(clause).weight(maxWeight).build();
    Segment s = baseSegmentBuilder()
        .rules(rule)
        .build();
    
    assertTrue(segmentMatchesContext(s, c));
  }

  @Test
  public void matchingRuleWithZeroRollout() {
    LDContext c = LDContext.create("foo");
    Clause clause = clauseMatchingContext(c);
    SegmentRule rule = segmentRuleBuilder().clauses(clause).weight(0).build();
    Segment s = baseSegmentBuilder()
        .rules(rule)
        .build();
    
    assertFalse(segmentMatchesContext(s, c));
  }

  @Test
  public void matchingRuleWithMultipleClauses() {
    Clause clause1 = clause("email", DataModel.Operator.in, LDValue.of("test@example.com"));
    Clause clause2 = clause("name", DataModel.Operator.in, LDValue.of("bob"));
    SegmentRule rule = segmentRuleBuilder().clauses(clause1, clause2).build();
    Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDContext c = LDContext.builder("foo").set("email", "test@example.com").name("bob").build();
    
    assertTrue(segmentMatchesContext(s, c));
  }

  @Test
  public void nonMatchingRuleWithMultipleClauses() {
    Clause clause1 = clause("email", DataModel.Operator.in, LDValue.of("test@example.com"));
    Clause clause2 = clause("name", DataModel.Operator.in, LDValue.of("bill"));
    SegmentRule rule = segmentRuleBuilder().clauses(clause1, clause2).build();
    Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDContext c = LDContext.builder("foo").set("email", "test@example.com").name("bob").build();
    
    assertFalse(segmentMatchesContext(s, c));
  }

  @Test
  public void rolloutUsesCorrectBucketValue() {
    LDContext c = LDContext.create("foo");
    testRolloutBucketing("foo", c, null, null);
  }
  
  @Test
  public void rolloutUsesContextKind() {
    LDContext c1 = LDContext.create(ContextKind.of("kind1"), "foo");
    LDContext c2 = LDContext.create(ContextKind.of("kind2"), "bar");
    LDContext multi = LDContext.createMulti(c1, c2);
    testRolloutBucketing("foo", multi, ContextKind.of("kind1"), null);
  }

  @Test
  public void rolloutUsesBucketBy() {
    LDContext c = LDContext.builder("xxx").set("attr1", LDValue.parse("{\"prop1\":\"foo\"}")).build();
    testRolloutBucketing("foo", c, null, AttributeRef.fromPath("/attr1/prop1"));
  }

  private void testRolloutBucketing(String bucketByValue, LDContext context, ContextKind contextKind, AttributeRef bucketBy) {
    float expectedBucketValue = computeBucketValue(false, null, LDContext.create(bucketByValue), null,
        SEGMENT_KEY, null, ARBITRARY_SALT);
    int bucketValueAsInt = (int)(expectedBucketValue * 100000);
    Clause clause = clauseMatchingContext(context);
    
    // When a segment rule has a weight, it matches only if the bucket value for the context (as an int
    // from 0 to 100000) is *less than* that weight. So, to roughly verify that the right bucket value
    // is being used, first we check that a rule with that value plus 1 is a match...
    Segment s1 = baseSegmentBuilder()
        .rules(segmentRuleBuilder().clauses(clause).weight(bucketValueAsInt + 1)
            .rolloutContextKind(contextKind).bucketBy(bucketBy).build())
        .build();
    assertTrue(segmentMatchesContext(s1, context));

    // ...and then, that a rule with that value minus 1 is not a match.
    Segment s2 = baseSegmentBuilder()
        .rules(segmentRuleBuilder().clauses(clause).weight(bucketValueAsInt - 1)
            .rolloutContextKind(contextKind).bucketBy(bucketBy).build())
        .build();
    assertFalse(segmentMatchesContext(s2, context));
  }
  
  @Test
  public void segmentReferencingSegment() {
    LDContext context = LDContext.create("foo");
    Segment segment0 = segmentBuilder("segmentkey0")
        .rules(segmentRuleBuilder().clauses(clauseMatchingSegment("segmentkey1")).build())
        .build();
    Segment segment1 = segmentBuilder("segmentkey1")
        .included(context.getKey())
        .build();
    FeatureFlag flag = booleanFlagWithClauses("flag", clauseMatchingSegment(segment0));
    
    Evaluator e = evaluatorBuilder().withStoredSegments(segment0, segment1).build();
    EvalResult result = e.evaluate(flag, context, expectNoPrerequisiteEvals());
    assertTrue(result.getValue().booleanValue());
  }
  
  @Test
  public void segmentCycleDetection() {
    for (int depth = 1; depth <= 4; depth++) {
      String[] segmentKeys = new String[depth];
      for (int i = 0; i < depth; i++) {
        segmentKeys[i] = "segmentkey" + i;
      }
      Segment[] segments = new Segment[depth];
      for (int i = 0; i < depth; i++) {
        segments[i] = segmentBuilder(segmentKeys[i])
            .rules(
                segmentRuleBuilder().clauses(
                    clauseMatchingSegment(segmentKeys[(i + 1) % depth])
                    ).build()
                )
            .build();
      }

      FeatureFlag flag = booleanFlagWithClauses("flag", clauseMatchingSegment(segments[0]));
      Evaluator e = evaluatorBuilder().withStoredSegments(segments).build();

      LDContext context = LDContext.create("foo");
      EvalResult result = e.evaluate(flag, context, expectNoPrerequisiteEvals());
      assertEquals(EvalResult.error(ErrorKind.MALFORMED_FLAG), result);
    }
  }

  @Test
  public void sameSegmentInMultipleSegmentRules() {
    LDContext context = LDContext.create("foo");
    Segment reusedSegment = baseSegmentBuilder()
            .rules(
                    segmentRuleBuilder().clauses(clauseMatchingContext(context)).build()
            )
            .build();
    DataModel.Rule rule0 = ruleBuilder().id("ruleid0").clauses(negateClause(clauseMatchingSegment(reusedSegment))).variation(0).build();
    DataModel.Rule rule1 = ruleBuilder().id("ruleid1").clauses(clauseMatchingSegment(reusedSegment)).variation(1).build();

    DataModel.FeatureFlag flag = flagBuilder("flag")
            .on(true)
            .rules(rule0,rule1)
            .fallthroughVariation(0)
            .offVariation(0)
            .variations(LDValue.of(false), LDValue.of(true)).build();

    Evaluator e = evaluatorBuilder().withStoredSegments(reusedSegment).build();
    EvalResult result = e.evaluate(flag, context, expectNoPrerequisiteEvals());
    assertEquals(EvaluationReason.ruleMatch(1, "ruleid1"), result.getReason());
    assertTrue(result.getValue().booleanValue());
  }
  private static SegmentBuilder baseSegmentBuilder() {
    return segmentBuilder(SEGMENT_KEY).version(1).salt(ARBITRARY_SALT);
  }
  
  private boolean segmentMatchesContext(Segment segment, LDContext context) {
    FeatureFlag flag = booleanFlagWithClauses("flag", clauseMatchingSegment(segment));
    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    return e.evaluate(flag, context, expectNoPrerequisiteEvals()).getValue().booleanValue();
  }
}