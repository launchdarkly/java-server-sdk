package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;

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
    DataModel.Segment s = segmentBuilder("test")
        .included("foo")
        .salt("abcdef")
        .version(1)
        .build();
    LDUser u = new LDUser.Builder("foo").build();
    
    assertTrue(segmentMatchesUser(s, u));
  }
  
  @Test
  public void explicitExcludeUser() {
    DataModel.Segment s = segmentBuilder("test")
        .excluded("foo")
        .salt("abcdef")
        .version(1)
        .build();
    LDUser u = new LDUser.Builder("foo").build();
    
    assertFalse(segmentMatchesUser(s, u));
  }
  
  @Test
  public void explicitIncludeHasPrecedence() {
    DataModel.Segment s = segmentBuilder("test")
        .included("foo")
        .excluded("foo")
        .salt("abcdef")
        .version(1)
        .build();
    LDUser u = new LDUser.Builder("foo").build();
    
    assertTrue(segmentMatchesUser(s, u));
  }
  
  @Test
  public void matchingRuleWithFullRollout() {
    DataModel.Clause clause = clause(UserAttribute.EMAIL, DataModel.Operator.in, LDValue.of("test@example.com"));
    DataModel.SegmentRule rule = segmentRuleBuilder().clauses(clause).weight(maxWeight).build();
    DataModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").build();
    
    assertTrue(segmentMatchesUser(s, u));
  }

  @Test
  public void matchingRuleWithZeroRollout() {
    DataModel.Clause clause = clause(UserAttribute.EMAIL, DataModel.Operator.in, LDValue.of("test@example.com"));
    DataModel.SegmentRule rule = segmentRuleBuilder().clauses(clause).weight(0).build();
    DataModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").build();
    
    assertFalse(segmentMatchesUser(s, u));
  }
  
  @Test
  public void matchingRuleWithMultipleClauses() {
    DataModel.Clause clause1 = clause(UserAttribute.EMAIL, DataModel.Operator.in, LDValue.of("test@example.com"));
    DataModel.Clause clause2 = clause(UserAttribute.NAME, DataModel.Operator.in, LDValue.of("bob"));
    DataModel.SegmentRule rule = segmentRuleBuilder().clauses(clause1, clause2).build();
    DataModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").name("bob").build();
    
    assertTrue(segmentMatchesUser(s, u));
  }

  @Test
  public void nonMatchingRuleWithMultipleClauses() {
    DataModel.Clause clause1 = clause(UserAttribute.EMAIL, DataModel.Operator.in, LDValue.of("test@example.com"));
    DataModel.Clause clause2 = clause(UserAttribute.NAME, DataModel.Operator.in, LDValue.of("bill"));
    DataModel.SegmentRule rule = segmentRuleBuilder().clauses(clause1, clause2).build();
    DataModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").name("bob").build();
    
    assertFalse(segmentMatchesUser(s, u));
  }
  
  private boolean segmentMatchesUser(DataModel.Segment segment, LDUser user) {
    DataModel.Clause clause = clauseMatchingSegment(segment);
    DataModel.FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    return e.evaluate(flag, user, expectNoPrerequisiteEvals()).getValue().booleanValue();
  }
}