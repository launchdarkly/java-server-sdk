package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import static com.launchdarkly.client.EvaluatorTestUtil.evaluatorBuilder;
import static com.launchdarkly.client.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.client.ModelBuilders.clause;
import static com.launchdarkly.client.ModelBuilders.segmentBuilder;
import static com.launchdarkly.client.ModelBuilders.segmentRuleBuilder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class EvaluatorSegmentMatchTest {

  private int maxWeight = 100000;
  
  @Test
  public void explicitIncludeUser() {
    FlagModel.Segment s = segmentBuilder("test")
        .included("foo")
        .salt("abcdef")
        .version(1)
        .build();
    LDUser u = new LDUser.Builder("foo").build();
    
    assertTrue(segmentMatchesUser(s, u));
  }
  
  @Test
  public void explicitExcludeUser() {
    FlagModel.Segment s = segmentBuilder("test")
        .excluded("foo")
        .salt("abcdef")
        .version(1)
        .build();
    LDUser u = new LDUser.Builder("foo").build();
    
    assertFalse(segmentMatchesUser(s, u));
  }
  
  @Test
  public void explicitIncludeHasPrecedence() {
    FlagModel.Segment s = segmentBuilder("test")
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
    FlagModel.Clause clause = clause("email", Operator.in, LDValue.of("test@example.com"));
    FlagModel.SegmentRule rule = segmentRuleBuilder().clauses(clause).weight(maxWeight).build();
    FlagModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").build();
    
    assertTrue(segmentMatchesUser(s, u));
  }

  @Test
  public void matchingRuleWithZeroRollout() {
    FlagModel.Clause clause = clause("email", Operator.in, LDValue.of("test@example.com"));
    FlagModel.SegmentRule rule = segmentRuleBuilder().clauses(clause).weight(0).build();
    FlagModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").build();
    
    assertFalse(segmentMatchesUser(s, u));
  }
  
  @Test
  public void matchingRuleWithMultipleClauses() {
    FlagModel.Clause clause1 = clause("email", Operator.in, LDValue.of("test@example.com"));
    FlagModel.Clause clause2 = clause("name", Operator.in, LDValue.of("bob"));
    FlagModel.SegmentRule rule = segmentRuleBuilder().clauses(clause1, clause2).build();
    FlagModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").name("bob").build();
    
    assertTrue(segmentMatchesUser(s, u));
  }

  @Test
  public void nonMatchingRuleWithMultipleClauses() {
    FlagModel.Clause clause1 = clause("email", Operator.in, LDValue.of("test@example.com"));
    FlagModel.Clause clause2 = clause("name", Operator.in, LDValue.of("bill"));
    FlagModel.SegmentRule rule = segmentRuleBuilder().clauses(clause1, clause2).build();
    FlagModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").name("bob").build();
    
    assertFalse(segmentMatchesUser(s, u));
  }
  
  private static boolean segmentMatchesUser(FlagModel.Segment segment, LDUser user) {
    FlagModel.Clause clause = clause("", Operator.segmentMatch, LDValue.of(segment.getKey()));
    FlagModel.FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    return e.evaluate(flag, user, EventFactory.DEFAULT).getValue().booleanValue();
  }
}