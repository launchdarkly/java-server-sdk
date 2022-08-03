package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;

import org.junit.Test;

import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static com.launchdarkly.sdk.server.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingSegment;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentRuleBuilder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class EvaluatorSegmentMatchTest extends EvaluatorTestBase {

  private int maxWeight = 100000;
  
  @Test
  public void explicitIncludeUser() {
    Segment s = segmentBuilder("test")
        .included("foo")
        .salt("abcdef")
        .version(1)
        .build();
    LDContext c = LDContext.create("foo");
    
    assertTrue(segmentMatchesContext(s, c));
  }
  
  @Test
  public void explicitExcludeUser() {
    Segment s = segmentBuilder("test")
        .excluded("foo")
        .salt("abcdef")
        .version(1)
        .build();
    LDContext c = LDContext.create("foo");
    
    assertFalse(segmentMatchesContext(s, c));
  }
  
  @Test
  public void explicitIncludeHasPrecedence() {
    Segment s = segmentBuilder("test")
        .included("foo")
        .excluded("foo")
        .salt("abcdef")
        .version(1)
        .build();
    LDContext c = LDContext.create("foo");
    
    assertTrue(segmentMatchesContext(s, c));
  }
  
  @Test
  public void matchingRuleWithFullRollout() {
    Clause clause = clause(UserAttribute.EMAIL, DataModel.Operator.in, LDValue.of("test@example.com"));
    SegmentRule rule = segmentRuleBuilder().clauses(clause).weight(maxWeight).build();
    Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDContext c = LDContext.builder("foo").set("email", "test@example.com").build();
    
    assertTrue(segmentMatchesContext(s, c));
  }

  @Test
  public void matchingRuleWithZeroRollout() {
    Clause clause = clause(UserAttribute.EMAIL, DataModel.Operator.in, LDValue.of("test@example.com"));
    SegmentRule rule = segmentRuleBuilder().clauses(clause).weight(0).build();
    Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDContext c = LDContext.builder("foo").set("email", "test@example.com").build();
    
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
  
  private boolean segmentMatchesContext(Segment segment, LDContext context) {
    Clause clause = clauseMatchingSegment(segment);
    FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    return e.evaluate(flag, context, expectNoPrerequisiteEvals()).getValue().booleanValue();
  }
}