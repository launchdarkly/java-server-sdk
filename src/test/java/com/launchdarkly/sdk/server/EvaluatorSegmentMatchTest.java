package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
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
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentRuleBuilder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class EvaluatorSegmentMatchTest extends EvaluatorTestBase {
  private static final String SEGMENT_KEY = "segmentkey";
  private static final String ARBITRARY_SALT = "abcdef";
  private static final int maxWeight = 100000;
  
  @Test
  public void explicitIncludeUser() {
    Segment s = baseSegmentBuilder()
        .included("foo")
        .build();
    LDContext c = LDContext.create("foo");
    
    assertTrue(segmentMatchesContext(s, c));
  }
  
  @Test
  public void explicitExcludeUser() {
    Segment s = baseSegmentBuilder()
        .excluded("foo")
        .build();
    LDContext c = LDContext.create("foo");
    
    assertFalse(segmentMatchesContext(s, c));
  }
  
  @Test
  public void explicitIncludeHasPrecedence() {
    Segment s = baseSegmentBuilder()
        .included("foo")
        .excluded("foo")
        .build();
    LDContext c = LDContext.create("foo");
    
    assertTrue(segmentMatchesContext(s, c));
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
    Clause clause1 = clause(UserAttribute.EMAIL, DataModel.Operator.in, LDValue.of("test@example.com"));
    Clause clause2 = clause(UserAttribute.NAME, DataModel.Operator.in, LDValue.of("bob"));
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
    Clause clause1 = clause(UserAttribute.EMAIL, DataModel.Operator.in, LDValue.of("test@example.com"));
    Clause clause2 = clause(UserAttribute.NAME, DataModel.Operator.in, LDValue.of("bill"));
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
  
  private static SegmentBuilder baseSegmentBuilder() {
    return segmentBuilder(SEGMENT_KEY).version(1).salt(ARBITRARY_SALT);
  }
  
  private boolean segmentMatchesContext(Segment segment, LDContext context) {
    Clause clause = clauseMatchingSegment(segment);
    FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    return e.evaluate(flag, context, expectNoPrerequisiteEvals()).getValue().booleanValue();
  }
}