package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.client.EvaluatorTestUtil.evaluatorBuilder;
import static com.launchdarkly.client.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.client.ModelBuilders.segmentBuilder;
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
    FlagModel.Clause clause = new FlagModel.Clause(
        "email",
        Operator.in,
        Arrays.asList(LDValue.of("test@example.com")),
        false);
    FlagModel.SegmentRule rule = new FlagModel.SegmentRule(
        Arrays.asList(clause),
        maxWeight,
        null);
    FlagModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").build();
    
    assertTrue(segmentMatchesUser(s, u));
  }

  @Test
  public void matchingRuleWithZeroRollout() {
    FlagModel.Clause clause = new FlagModel.Clause(
        "email",
        Operator.in,
        Arrays.asList(LDValue.of("test@example.com")),
        false);
    FlagModel.SegmentRule rule = new FlagModel.SegmentRule(Arrays.asList(clause),
        0,
        null);
    FlagModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").build();
    
    assertFalse(segmentMatchesUser(s, u));
  }
  
  @Test
  public void matchingRuleWithMultipleClauses() {
    FlagModel.Clause clause1 = new FlagModel.Clause(
        "email",
        Operator.in,
        Arrays.asList(LDValue.of("test@example.com")),
        false);
    FlagModel.Clause clause2 = new FlagModel.Clause(
        "name",
        Operator.in,
        Arrays.asList(LDValue.of("bob")),
        false);
    FlagModel.SegmentRule rule = new FlagModel.SegmentRule(
        Arrays.asList(clause1, clause2),
        null,
        null);
    FlagModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").name("bob").build();
    
    assertTrue(segmentMatchesUser(s, u));
  }

  @Test
  public void nonMatchingRuleWithMultipleClauses() {
    FlagModel.Clause clause1 = new FlagModel.Clause(
        "email",
        Operator.in,
        Arrays.asList(LDValue.of("test@example.com")),
        false);
    FlagModel.Clause clause2 = new FlagModel.Clause(
        "name",
        Operator.in,
        Arrays.asList(LDValue.of("bill")),
        false);
    FlagModel.SegmentRule rule = new FlagModel.SegmentRule(
        Arrays.asList(clause1, clause2),
        null,
        null);
    FlagModel.Segment s = segmentBuilder("test")
        .salt("abcdef")
        .rules(rule)
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").name("bob").build();
    
    assertFalse(segmentMatchesUser(s, u));
  }
  
  private static boolean segmentMatchesUser(FlagModel.Segment segment, LDUser user) {
    FlagModel.Clause clause = new FlagModel.Clause("", Operator.segmentMatch, Arrays.asList(LDValue.of(segment.getKey())), false);
    FlagModel.FeatureFlag flag = booleanFlagWithClauses("flag", clause);
    Evaluator e = evaluatorBuilder().withStoredSegments(segment).build();
    return e.evaluate(flag, user, EventFactory.DEFAULT).getValue().booleanValue();
  }
}