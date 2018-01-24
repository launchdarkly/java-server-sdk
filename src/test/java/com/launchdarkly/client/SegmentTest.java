package com.launchdarkly.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.gson.JsonPrimitive;

public class SegmentTest {

  private int maxWeight = 100000;
  
  @Test
  public void explicitIncludeUser() {
    Segment s = new Segment.Builder("test")
        .included(Arrays.asList("foo"))
        .salt("abcdef")
        .version(1)
        .build();
    LDUser u = new LDUser.Builder("foo").build();
    
    Segment.MatchResult r = s.matchUser(u);
    
    assertTrue(r.isMatch());
    assertEquals(Optional.of(Segment.MatchKind.INCLUDED), r.getKind());
  }
  
  @Test
  public void explicitExcludeUser() {
    Segment s = new Segment.Builder("test")
        .excluded(Arrays.asList("foo"))
        .salt("abcdef")
        .version(1)
        .build();
    LDUser u = new LDUser.Builder("foo").build();
    
    Segment.MatchResult r = s.matchUser(u);
    
    assertFalse(r.isMatch());
    assertEquals(Optional.of(Segment.MatchKind.EXCLUDED), r.getKind());
  }
  
  @Test
  public void explicitIncludeHasPrecedence() {
    Segment s = new Segment.Builder("test")
        .included(Arrays.asList("foo"))
        .excluded(Arrays.asList("foo"))
        .salt("abcdef")
        .version(1)
        .build();
    LDUser u = new LDUser.Builder("foo").build();
    
    Segment.MatchResult r = s.matchUser(u);
    
    assertTrue(r.isMatch());
    assertEquals(Optional.of(Segment.MatchKind.INCLUDED), r.getKind());
  }
  
  @Test
  public void matchingRuleWithFullRollout() {
    Clause clause = new Clause(
        "email",
        Operator.in,
        Arrays.asList(new JsonPrimitive("test@example.com")),
        false);
    SegmentRule rule = new SegmentRule(
        Arrays.asList(clause),
        maxWeight,
        null);
    Segment s = new Segment.Builder("test")
        .salt("abcdef")
        .rules(Arrays.asList(rule))
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").build();
    
    Segment.MatchResult r = s.matchUser(u);
    
    assertTrue(r.isMatch());
    assertEquals(Optional.of(Segment.MatchKind.RULE), r.getKind());
    assertEquals(Optional.of(rule), r.getMatchedRule());
  }

  @Test
  public void matchingRuleWithZeroRollout() {
    Clause clause = new Clause(
        "email",
        Operator.in,
        Arrays.asList(new JsonPrimitive("test@example.com")),
        false);
    SegmentRule rule = new SegmentRule(Arrays.asList(clause),
        0,
        null);
    Segment s = new Segment.Builder("test")
        .salt("abcdef")
        .rules(Arrays.asList(rule))
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").build();
    
    Segment.MatchResult r = s.matchUser(u);
    
    assertFalse(r.isMatch());
    assertEquals(Optional.<Segment.MatchKind>absent(), r.getKind());
    assertEquals(Optional.<SegmentRule>absent(), r.getMatchedRule());
  }
  
  @Test
  public void matchingRuleWithMultipleClauses() {
    Clause clause1 = new Clause(
        "email",
        Operator.in,
        Arrays.asList(new JsonPrimitive("test@example.com")),
        false);
    Clause clause2 = new Clause(
        "name",
        Operator.in,
        Arrays.asList(new JsonPrimitive("bob")),
        false);
    SegmentRule rule = new SegmentRule(
        Arrays.asList(clause1, clause2),
        null,
        null);
    Segment s = new Segment.Builder("test")
        .salt("abcdef")
        .rules(Arrays.asList(rule))
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").name("bob").build();
    
    Segment.MatchResult r = s.matchUser(u);
    
    assertTrue(r.isMatch());
    assertEquals(Optional.of(Segment.MatchKind.RULE), r.getKind());
    assertEquals(Optional.of(rule), r.getMatchedRule());
  }

  @Test
  public void nonMatchingRuleWithMultipleClauses() {
    Clause clause1 = new Clause(
        "email",
        Operator.in,
        Arrays.asList(new JsonPrimitive("test@example.com")),
        false);
    Clause clause2 = new Clause(
        "name",
        Operator.in,
        Arrays.asList(new JsonPrimitive("bill")),
        false);
    SegmentRule rule = new SegmentRule(
        Arrays.asList(clause1, clause2),
        null,
        null);
    Segment s = new Segment.Builder("test")
        .salt("abcdef")
        .rules(Arrays.asList(rule))
        .build();
    LDUser u = new LDUser.Builder("foo").email("test@example.com").name("bob").build();
    
    Segment.MatchResult r = s.matchUser(u);

    assertFalse(r.isMatch());
    assertEquals(Optional.<Segment.MatchKind>absent(), r.getKind());
    assertEquals(Optional.<SegmentRule>absent(), r.getMatchedRule());
  }
}