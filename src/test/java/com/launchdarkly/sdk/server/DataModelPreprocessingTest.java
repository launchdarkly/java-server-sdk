package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Prerequisite;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;
import com.launchdarkly.sdk.server.DataModel.Target;
import com.launchdarkly.sdk.server.DataModelPreprocessing.ClausePreprocessed;

import org.junit.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.launchdarkly.sdk.server.ModelBuilders.clause;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentRuleBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class DataModelPreprocessingTest {
  // We deliberately use the data model constructors here instead of the more convenient ModelBuilders
  // equivalents, to make sure we're testing the afterDeserialization() behavior and not just the builder.

  private static final LDValue aValue = LDValue.of("a"), bValue = LDValue.of("b");
  
  private FeatureFlag flagFromClause(Clause c) {
    return new FeatureFlag("key", 0, false, null, null, null, null, rulesFromClause(c),
        null, null, null, false, false, false, null, false, null, null, false);
  }
  
  private List<Rule> rulesFromClause(Clause c) {
    return ImmutableList.of(new Rule("", ImmutableList.of(c), null, null, false));
  }

  @Test
  public void preprocessFlagAddsPrecomputedOffResult() {
    FeatureFlag f = new FeatureFlag("key", 0, false, null, null, null, null,
        ImmutableList.of(), null,
        0,
        ImmutableList.of(aValue, bValue),
        false, false, false, null, false, null, null, false);
    
    f.afterDeserialized();
    
    assertThat(f.preprocessed, notNullValue());
    assertThat(f.preprocessed.offResult,
        equalTo(EvalResult.of(aValue, 0, EvaluationReason.off())));
  }

  @Test
  public void preprocessFlagAddsPrecomputedOffResultForNullOffVariation() {
    FeatureFlag f = new FeatureFlag("key", 0, false, null, null, null, null,
        ImmutableList.of(), null,
        null,
        ImmutableList.of(aValue, bValue),
        false, false, false, null, false, null, null, false);
    
    f.afterDeserialized();
    
    assertThat(f.preprocessed, notNullValue());
    assertThat(f.preprocessed.offResult,
        equalTo(EvalResult.of(LDValue.ofNull(), EvaluationDetail.NO_VARIATION, EvaluationReason.off())));
  }

  @Test
  public void preprocessFlagAddsPrecomputedFallthroughResults() {
    FeatureFlag f = new FeatureFlag("key", 0, false, null, null, null, null,
        ImmutableList.of(), null, 0,
        ImmutableList.of(aValue, bValue),
        false, false, false, null, false, null, null, false);
    
    f.afterDeserialized();
    
    assertThat(f.preprocessed, notNullValue());
    assertThat(f.preprocessed.fallthroughResults, notNullValue());
    EvaluationReason regularReason = EvaluationReason.fallthrough(false);
    EvaluationReason inExperimentReason = EvaluationReason.fallthrough(true);

    assertThat(f.preprocessed.fallthroughResults.forVariation(0, false),
        equalTo(EvalResult.of(aValue, 0, regularReason)));
    assertThat(f.preprocessed.fallthroughResults.forVariation(0, true),
        equalTo(EvalResult.of(aValue, 0, inExperimentReason)));
    
    assertThat(f.preprocessed.fallthroughResults.forVariation(1, false),
        equalTo(EvalResult.of(bValue, 1, regularReason)));
    assertThat(f.preprocessed.fallthroughResults.forVariation(1, true),
        equalTo(EvalResult.of(bValue, 1, inExperimentReason)));
  }

  @Test
  public void preprocessFlagAddsPrecomputedTargetMatchResults() {
    FeatureFlag f = new FeatureFlag("key", 0, false, null, null,
        ImmutableList.of(new Target(null, ImmutableSet.of(), 1)),
        null, ImmutableList.of(), null, 0,
        ImmutableList.of(aValue, bValue),
        false, false, false, null, false, null, null, false);
    
    f.afterDeserialized();
    
    Target t = f.getTargets().get(0);
    assertThat(t.preprocessed, notNullValue());
    assertThat(t.preprocessed.targetMatchResult,
        equalTo(EvalResult.of(bValue, 1, EvaluationReason.targetMatch())));
  }

  @Test
  public void preprocessFlagAddsPrecomputedPrerequisiteFailedResults() {
    FeatureFlag f = new FeatureFlag("key", 0, false,
        ImmutableList.of(new Prerequisite("abc", 1)),
        null, null, null,
        ImmutableList.of(), null, 0,
        ImmutableList.of(aValue, bValue),
        false, false, false, null, false, null, null, false);
    
    f.afterDeserialized();
    
    Prerequisite p = f.getPrerequisites().get(0);
    assertThat(p.preprocessed, notNullValue());
    assertThat(p.preprocessed.prerequisiteFailedResult,
        equalTo(EvalResult.of(aValue, 0, EvaluationReason.prerequisiteFailed("abc"))));
  }

  @Test
  public void preprocessFlagAddsPrecomputedResultsToFlagRulesWithRollout() {

    List<DataModel.WeightedVariation> variations = new ArrayList<>();
    variations.add(new DataModel.WeightedVariation(0, 50000, false));
    variations.add(new DataModel.WeightedVariation(1, 50000, false));
    DataModel.RolloutKind kind = DataModel.RolloutKind.rollout;
    Integer seed = 123;
    DataModel.Rollout rollout = new DataModel.Rollout(null, variations, null, kind, seed);

    FeatureFlag f = new FeatureFlag("key", 0, false, null, null, null, null,
        ImmutableList.of(new Rule("ruleid0", ImmutableList.of(), null, rollout, false)),
        null, null,
        ImmutableList.of(aValue, bValue),
        false, false, false, null, false, null, null, false);

    f.afterDeserialized();

    Rule rule = f.getRules().get(0);
    assertThat(rule.preprocessed, notNullValue());
    assertThat(rule.preprocessed.allPossibleResults, notNullValue());
    EvaluationReason regularReason = EvaluationReason.ruleMatch(0, "ruleid0", false);
    EvaluationReason inExperimentReason = EvaluationReason.ruleMatch(0, "ruleid0", true);

    assertThat(rule.preprocessed.allPossibleResults.forVariation(0, false),
        equalTo(EvalResult.of(aValue, 0, regularReason)));
    assertThat(rule.preprocessed.allPossibleResults.forVariation(0, true),
        equalTo(EvalResult.of(aValue, 0, inExperimentReason)));

    assertThat(rule.preprocessed.allPossibleResults.forVariation(1, false),
        equalTo(EvalResult.of(bValue, 1, regularReason)));
    assertThat(rule.preprocessed.allPossibleResults.forVariation(1, true),
        equalTo(EvalResult.of(bValue, 1, inExperimentReason)));
  }

  @Test
  public void preprocessFlagAddsPrecomputedResultsToFlagRulesWithJustVariation() {
    FeatureFlag f = new FeatureFlag("key", 0, false, null, null, null, null,
        ImmutableList.of(new Rule("ruleid0", ImmutableList.of(), 0, null, false)),
        null, null,
        ImmutableList.of(aValue, bValue),
        false, false, false, null, false, null, null, false);
    
    f.afterDeserialized();
    
    Rule rule = f.getRules().get(0);
    assertThat(rule.preprocessed, notNullValue());
    assertThat(rule.preprocessed.allPossibleResults, notNullValue());
    EvaluationReason regularReason = EvaluationReason.ruleMatch(0, "ruleid0", false);
    EvaluationReason inExperimentReason = EvaluationReason.ruleMatch(0, "ruleid0", true);
    
    assertThat(rule.preprocessed.allPossibleResults.forVariation(0, false),
        equalTo(EvalResult.of(aValue, 0, regularReason)));
    assertThat(rule.preprocessed.allPossibleResults.forVariation(0, true),
        equalTo(EvalResult.of(aValue, 0, inExperimentReason)));
    
    assertThat(rule.preprocessed.allPossibleResults.forVariation(1, false),
        equalTo(EvalResult.error(EvaluationReason.ErrorKind.EXCEPTION)));
    assertThat(rule.preprocessed.allPossibleResults.forVariation(1, true),
        equalTo(EvalResult.error(EvaluationReason.ErrorKind.EXCEPTION)));
  }
  
  @Test
  public void preprocessFlagCreatesClauseValuesMapForMultiValueEqualityTest() {
    Clause c = clause(
        "x",
        Operator.in,
        LDValue.of("a"), LDValue.of(0) 
        );
 
    FeatureFlag f = flagFromClause(c);
    assertNull(f.getRules().get(0).getClauses().get(0).preprocessed);
    
    f.afterDeserialized();
    
    ClausePreprocessed ce = f.getRules().get(0).getClauses().get(0).preprocessed;
    assertNotNull(ce);
    assertEquals(ImmutableSet.of(LDValue.of("a"), LDValue.of(0)), ce.valuesSet);
  }
  
  @Test
  public void preprocessFlagDoesNotCreateClauseValuesMapForSingleValueEqualityTest() {
    Clause c = clause(
        "x",
        Operator.in,
        LDValue.of("a")
        );
    
    FeatureFlag f = flagFromClause(c);
    assertNull(f.getRules().get(0).getClauses().get(0).preprocessed);
    
    f.afterDeserialized();
    
    assertNull(f.getRules().get(0).getClauses().get(0).preprocessed);
  }
  
  @Test
  public void preprocessFlagDoesNotCreateClauseValuesMapForEmptyEqualityTest() {
    Clause c = clause(
        "x",
        Operator.in
        );
    
    FeatureFlag f = flagFromClause(c);
    assertNull(f.getRules().get(0).getClauses().get(0).preprocessed);
    
    f.afterDeserialized();
    
    assertNull(f.getRules().get(0).getClauses().get(0).preprocessed);
  }
  
  @Test
  public void preprocessFlagDoesNotCreateClauseValuesMapForNonEqualityOperators() {
    for (Operator op: Operator.getBuiltins()) {
      if (op == Operator.in) {
        continue;
      }
      Clause c = clause(
          "x",
          op,
          LDValue.of("a"), LDValue.of("b")
          );
      // The values & types aren't very important here because we won't actually evaluate the clause; all that
      // matters is that there's more than one of them, so that it *would* build a map if the operator were "in"
      
      FeatureFlag f = flagFromClause(c);
      assertNull(op.toString(), f.getRules().get(0).getClauses().get(0).preprocessed);
      
      f.afterDeserialized();
      
      ClausePreprocessed ce = f.getRules().get(0).getClauses().get(0).preprocessed;
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
      Clause c = clause(
          "x",
          op,
          LDValue.of(time1Str), LDValue.of(time2Num), LDValue.of("x"), LDValue.of(false)
          );
      
      FeatureFlag f = flagFromClause(c);
      assertNull(f.getRules().get(0).getClauses().get(0).preprocessed);
      
      f.afterDeserialized();
      
      ClausePreprocessed ce = f.getRules().get(0).getClauses().get(0).preprocessed;
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
    Clause c = clause(
        "x",
        Operator.matches,
        LDValue.of("x*"), LDValue.of("***not a regex"), LDValue.of(3)
        );
    
    FeatureFlag f = flagFromClause(c);
    assertNull(f.getRules().get(0).getClauses().get(0).preprocessed);
    
    f.afterDeserialized();
    
    ClausePreprocessed ce = f.getRules().get(0).getClauses().get(0).preprocessed;
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
      Clause c = clause(
          "x",
          op,
          LDValue.of("1.2.3"), LDValue.of("x"), LDValue.of(false)
          );
      
      FeatureFlag f = flagFromClause(c);
      assertNull(f.getRules().get(0).getClauses().get(0).preprocessed);
      
      f.afterDeserialized();
      
      ClausePreprocessed ce = f.getRules().get(0).getClauses().get(0).preprocessed;
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
    Clause c = clause(
        "x",
        Operator.matches,
        LDValue.of("x*")
        );    
    SegmentRule rule = segmentRuleBuilder().clauses(c).build();
    Segment s = segmentBuilder("key").disablePreprocessing(true).rules(rule).build();
    
    assertNull(s.getRules().get(0).getClauses().get(0).preprocessed);
    
    s.afterDeserialized();
    
    ClausePreprocessed ce = s.getRules().get(0).getClauses().get(0).preprocessed;
    assertNotNull(ce.valuesExtra);
    assertEquals(1, ce.valuesExtra.size());
    assertNotNull(ce.valuesExtra.get(0).parsedRegex);
    assertEquals("x*", ce.valuesExtra.get(0).parsedRegex.toString());
  }
}
