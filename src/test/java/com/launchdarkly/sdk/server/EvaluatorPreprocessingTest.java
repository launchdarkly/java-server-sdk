package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;

import org.junit.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class EvaluatorPreprocessingTest {
  // We deliberately use the data model constructors here instead of the more convenient ModelBuilders
  // equivalents, to make sure we're testing the afterDeserialization() behavior and not just the builder.

  private FeatureFlag flagFromClause(Clause c) {
    return new FeatureFlag("key", 0, false, null, null, null, rulesFromClause(c),
        null, null, null, false, false, false, null, false);
  }
  
  private List<Rule> rulesFromClause(Clause c) {
    return ImmutableList.of(new Rule("", ImmutableList.of(c), null, null, false));
  }
  
  @Test
  public void preprocessFlagCreatesClauseValuesMapForMultiValueEqualityTest() {
    Clause c = new Clause(
        UserAttribute.forName("x"),
        Operator.in,
        ImmutableList.of(LDValue.of("a"), LDValue.of(0)),
        false
        );
 
    FeatureFlag f = flagFromClause(c);
    assertNull(f.getRules().get(0).getClauses().get(0).getPreprocessed());
    
    f.afterDeserialized();
    
    EvaluatorPreprocessing.ClauseExtra ce = f.getRules().get(0).getClauses().get(0).getPreprocessed();
    assertNotNull(ce);
    assertEquals(ImmutableSet.of(LDValue.of("a"), LDValue.of(0)), ce.valuesSet);
  }
  
  @Test
  public void preprocessFlagDoesNotCreateClauseValuesMapForSingleValueEqualityTest() {
    Clause c = new Clause(
        UserAttribute.forName("x"),
        Operator.in,
        ImmutableList.of(LDValue.of("a")),
        false
        );
    
    FeatureFlag f = flagFromClause(c);
    assertNull(f.getRules().get(0).getClauses().get(0).getPreprocessed());
    
    f.afterDeserialized();
    
    assertNull(f.getRules().get(0).getClauses().get(0).getPreprocessed());
  }
  
  @Test
  public void preprocessFlagDoesNotCreateClauseValuesMapForEmptyEqualityTest() {
    Clause c = new Clause(
        UserAttribute.forName("x"),
        Operator.in,
        ImmutableList.of(),
        false
        );
    
    FeatureFlag f = flagFromClause(c);
    assertNull(f.getRules().get(0).getClauses().get(0).getPreprocessed());
    
    f.afterDeserialized();
    
    assertNull(f.getRules().get(0).getClauses().get(0).getPreprocessed());
  }
  
  @Test
  public void preprocessFlagDoesNotCreateClauseValuesMapForNonEqualityOperators() {
    for (Operator op: Operator.values()) {
      if (op == Operator.in) {
        continue;
      }
      Clause c = new Clause(
          UserAttribute.forName("x"),
          op,
          ImmutableList.of(LDValue.of("a"), LDValue.of("b")),
          false
          );
      // The values & types aren't very important here because we won't actually evaluate the clause; all that
      // matters is that there's more than one of them, so that it *would* build a map if the operator were "in"
      
      FeatureFlag f = flagFromClause(c);
      assertNull(op.name(), f.getRules().get(0).getClauses().get(0).getPreprocessed());
      
      f.afterDeserialized();
      
      EvaluatorPreprocessing.ClauseExtra ce = f.getRules().get(0).getClauses().get(0).getPreprocessed();
      // this might be non-null if we preprocessed the values list, but there should still not be a valuesSet
      if (ce != null) {
        assertNull(ce.valuesSet);
      }
    }
  }

  @Test
  public void preprocessFlagParsesClauseDate() {
    String time1Str = "2016-04-16T17:09:12-07:00";
    Instant time1 = ZonedDateTime.parse(time1Str).toInstant();
    int time2Num = 1000000;
    Instant time2 = Instant.ofEpochMilli(time2Num);

    for (Operator op: new Operator[] { Operator.after, Operator.before }) {
      Clause c = new Clause(
          UserAttribute.forName("x"),
          op,
          ImmutableList.of(LDValue.of(time1Str), LDValue.of(time2Num), LDValue.of("x"), LDValue.of(false)),
          false
          );
      
      FeatureFlag f = flagFromClause(c);
      assertNull(f.getRules().get(0).getClauses().get(0).getPreprocessed());
      
      f.afterDeserialized();
      
      EvaluatorPreprocessing.ClauseExtra ce = f.getRules().get(0).getClauses().get(0).getPreprocessed();
      assertNotNull(op.name(), ce);
      assertNotNull(op.name(), ce.valuesExtra);
      assertEquals(op.name(), 4, ce.valuesExtra.size());
      assertEquals(op.name(), time1, ce.valuesExtra.get(0).parsedDate);
      assertEquals(op.name(), time2, ce.valuesExtra.get(1).parsedDate);
      assertNull(op.name(), ce.valuesExtra.get(2).parsedDate);
      assertNull(op.name(), ce.valuesExtra.get(3).parsedDate);
    }
  }
  
  @Test
  public void preprocessFlagParsesClauseRegex() {
    Clause c = new Clause(
        UserAttribute.forName("x"),
        Operator.matches,
        ImmutableList.of(LDValue.of("x*"), LDValue.of("***not a regex"), LDValue.of(3)),
        false
        );
    
    FeatureFlag f = flagFromClause(c);
    assertNull(f.getRules().get(0).getClauses().get(0).getPreprocessed());
    
    f.afterDeserialized();
    
    EvaluatorPreprocessing.ClauseExtra ce = f.getRules().get(0).getClauses().get(0).getPreprocessed();
    assertNotNull(ce);
    assertNotNull(ce.valuesExtra);
    assertEquals(3, ce.valuesExtra.size());
    assertNotNull(ce.valuesExtra.get(0).parsedRegex);
    assertEquals("x*", ce.valuesExtra.get(0).parsedRegex.toString()); // Pattern doesn't support equals()
    assertNull(ce.valuesExtra.get(1).parsedRegex);
    assertNull(ce.valuesExtra.get(2).parsedRegex);
  }
  

  @Test
  public void preprocessFlagParsesClauseSemVer() {
    SemanticVersion expected = EvaluatorTypeConversion.valueToSemVer(LDValue.of("1.2.3"));
    assertNotNull(expected);

    for (Operator op: new Operator[] { Operator.semVerEqual, Operator.semVerGreaterThan, Operator.semVerLessThan }) {
      Clause c = new Clause(
          UserAttribute.forName("x"),
          op,
          ImmutableList.of(LDValue.of("1.2.3"), LDValue.of("x"), LDValue.of(false)),
          false
          );
      
      FeatureFlag f = flagFromClause(c);
      assertNull(f.getRules().get(0).getClauses().get(0).getPreprocessed());
      
      f.afterDeserialized();
      
      EvaluatorPreprocessing.ClauseExtra ce = f.getRules().get(0).getClauses().get(0).getPreprocessed();
      assertNotNull(op.name(), ce);
      assertNotNull(op.name(), ce.valuesExtra);
      assertEquals(op.name(), 3, ce.valuesExtra.size());
      assertNotNull(op.name(), ce.valuesExtra.get(0).parsedSemVer);
      assertEquals(op.name(), 0, ce.valuesExtra.get(0).parsedSemVer.compareTo(expected)); // SemanticVersion doesn't support equals()
      assertNull(op.name(), ce.valuesExtra.get(1).parsedSemVer);
      assertNull(op.name(), ce.valuesExtra.get(2).parsedSemVer);
    }
  }
  
  @Test
  public void preprocessSegmentPreprocessesClausesInRules() {
    // We'll just check one kind of clause, and assume that the preprocessing works the same as in flag rules
    Clause c = new Clause(
        UserAttribute.forName("x"),
        Operator.matches,
        ImmutableList.of(LDValue.of("x*")),
        false
        );    
    SegmentRule rule = new SegmentRule(ImmutableList.of(c), null, null);
    Segment s = new Segment("key", null, null, null, ImmutableList.of(rule), 0, false);
    
    assertNull(s.getRules().get(0).getClauses().get(0).getPreprocessed());
    
    s.afterDeserialized();
    
    EvaluatorPreprocessing.ClauseExtra ce = s.getRules().get(0).getClauses().get(0).getPreprocessed();
    assertNotNull(ce.valuesExtra);
    assertEquals(1, ce.valuesExtra.size());
    assertNotNull(ce.valuesExtra.get(0).parsedRegex);
    assertEquals("x*", ce.valuesExtra.get(0).parsedRegex.toString());
  }
}
