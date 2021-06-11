package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Prerequisite;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * These methods precompute data that may help to reduce the overhead of feature flag evaluations. They
 * are called from the afterDeserialized() methods of FeatureFlag and Segment, after those objects have
 * been deserialized from JSON but before they have been made available to any other code (so these
 * methods do not need to be thread-safe).
 * <p>
 * If for some reason these methods have not been called before an evaluation happens, the evaluation
 * logic must still be able to work without the precomputed data.
 */
abstract class EvaluatorPreprocessing {
  private EvaluatorPreprocessing() {}
  
  static final class ClauseExtra {
    final Set<LDValue> valuesSet;
    final List<ValueExtra> valuesExtra;
    
    ClauseExtra(Set<LDValue> valuesSet, List<ValueExtra> valuesExtra) {
      this.valuesSet = valuesSet;
      this.valuesExtra = valuesExtra;
    }
    
    static final class ValueExtra {
      final ZonedDateTime parsedDate;
      final Pattern parsedRegex;
      final SemanticVersion parsedSemVer;
      
      ValueExtra(ZonedDateTime parsedDate, Pattern parsedRegex, SemanticVersion parsedSemVer) {
        this.parsedDate = parsedDate;
        this.parsedRegex = parsedRegex;
        this.parsedSemVer = parsedSemVer;
      }
    }
  }
  
  static void preprocessFlag(FeatureFlag f) {
    for (Prerequisite p: f.getPrerequisites()) {
      EvaluatorPreprocessing.preprocessPrerequisite(p);
    }
    List<Rule> rules = f.getRules();
    int n = rules.size();
    for (int i = 0; i < n; i++) {
      preprocessFlagRule(rules.get(i), i);
    }
    preprocessValueList(f.getVariations());
  }
  
  static void preprocessSegment(Segment s) {
    List<SegmentRule> rules = s.getRules();
    int n = rules.size();
    for (int i = 0; i < n; i++) {
      preprocessSegmentRule(rules.get(i), i);
    }
  }
  
  static void preprocessPrerequisite(Prerequisite p) {
    // Precompute an immutable EvaluationReason instance that will be used if the prerequisite fails.
    p.setPrerequisiteFailedReason(EvaluationReason.prerequisiteFailed(p.getKey()));
  }
  
  static void preprocessFlagRule(Rule r, int ruleIndex) {
    // Precompute an immutable EvaluationReason instance that will be used if a user matches this rule.
    r.setRuleMatchReason(EvaluationReason.ruleMatch(ruleIndex, r.getId()));
    
    for (Clause c: r.getClauses()) {
      preprocessClause(c);
    }
  }

  static void preprocessSegmentRule(SegmentRule r, int ruleIndex) {
    for (Clause c: r.getClauses()) {
      preprocessClause(c);
    }
  }
  
  static void preprocessClause(Clause c) {
    // If the clause values contain a null (which is valid in terms of the JSON schema, even if it
    // can't ever produce a true result), Gson will give us an actual null. Change this to
    // LDValue.ofNull() to avoid NPEs down the line. It's more efficient to do this just once at
    // deserialization time than to do it in every clause match.
    List<LDValue> values = c.getValues();
    preprocessValueList(values);
    
    Operator op = c.getOp();
    if (op == null) {
      return;
    }
    switch (op) {
    case in:
      // This is a special case where the clause is testing for an exact match against any of the
      // clause values. Converting the value list to a Set allows us to do a fast lookup instead of
      // a linear search. We do not do this for other operators (or if there are fewer than two
      // values) because the slight extra overhead of a Set is not worthwhile in those case.
      if (values.size() > 1) {
        c.setPreprocessed(new ClauseExtra(ImmutableSet.copyOf(values), null));
      }
      break;
    case matches:
      c.setPreprocessed(preprocessClauseValues(c.getValues(), v ->
        new ClauseExtra.ValueExtra(null, EvaluatorTypeConversion.valueToRegex(v), null)
      ));
      break;
    case after:
    case before:
      c.setPreprocessed(preprocessClauseValues(c.getValues(), v ->
        new ClauseExtra.ValueExtra(EvaluatorTypeConversion.valueToDateTime(v), null, null)
      ));
      break;
    case semVerEqual:
    case semVerGreaterThan:
    case semVerLessThan:
      c.setPreprocessed(preprocessClauseValues(c.getValues(), v ->
        new ClauseExtra.ValueExtra(null, null, EvaluatorTypeConversion.valueToSemVer(v))
      ));
      break;
    default:
      break;
    }
  }
  
  static void preprocessValueList(List<LDValue> values) {
    // If a list of values contains a null (which is valid in terms of the JSON schema, even if it
    // isn't useful because the SDK considers this a non-value), Gson will give us an actual null.
    // Change this to LDValue.ofNull() to avoid NPEs down the line. It's more efficient to do this
    // just once at deserialization time than to do it in every clause match.
    for (int i = 0; i < values.size(); i++) {
      if (values.get(i) == null) {
        values.set(i, LDValue.ofNull());
      }
    }
  }
  
  private static ClauseExtra preprocessClauseValues(
      List<LDValue> values,
      Function<LDValue, ClauseExtra.ValueExtra> f
      ) {
    List<ClauseExtra.ValueExtra> valuesExtra = new ArrayList<>(values.size());
    for (LDValue v: values) {
      valuesExtra.add(f.apply(v));
    }
    return new ClauseExtra(null, valuesExtra);
  }
}
