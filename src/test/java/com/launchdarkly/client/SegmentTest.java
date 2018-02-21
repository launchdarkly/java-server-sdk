package com.launchdarkly.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

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
    
    assertTrue(s.matchesUser(u));
  }
  
  @Test
  public void explicitExcludeUser() {
    Segment s = new Segment.Builder("test")
        .excluded(Arrays.asList("foo"))
        .salt("abcdef")
        .version(1)
        .build();
    LDUser u = new LDUser.Builder("foo").build();
    
    assertFalse(s.matchesUser(u));
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
    
    assertTrue(s.matchesUser(u));
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
    
    assertTrue(s.matchesUser(u));
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
    
    assertFalse(s.matchesUser(u));
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
    
    assertTrue(s.matchesUser(u));
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
    
    assertFalse(s.matchesUser(u));
  }
}