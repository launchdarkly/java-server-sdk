package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Prerequisite;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;
import com.launchdarkly.sdk.server.DataModel.Target;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.launchdarkly.sdk.server.DataModel.Operator.after;
import static com.launchdarkly.sdk.server.DataModel.Operator.before;
import static com.launchdarkly.sdk.server.DataModel.Operator.in;
import static com.launchdarkly.sdk.server.DataModel.Operator.matches;
import static com.launchdarkly.sdk.server.DataModel.Operator.semVerEqual;
import static com.launchdarkly.sdk.server.DataModel.Operator.semVerGreaterThan;
import static com.launchdarkly.sdk.server.DataModel.Operator.semVerLessThan;

/**
 * Additional information that we attach to our data model to reduce the overhead of feature flag
 * evaluations. The methods that create these objects are called by the afterDeserialized() methods
 * of FeatureFlag and Segment, after those objects have been deserialized from JSON but before they
 * have been made available to any other code (so these methods do not need to be thread-safe).
 * <p>
 * If for some reason these methods have not been called before an evaluation happens, the evaluation
 * logic must still be able to work without the precomputed data.
 */
abstract class DataModelPreprocessing {
  private DataModelPreprocessing() {}
  
  static final class EvalResultsForSingleVariation {
    private final EvalResult regularResult;
    private final EvalResult inExperimentResult;
    
    EvalResultsForSingleVariation(
        LDValue value,
        int variationIndex,
        EvaluationReason regularReason,
        EvaluationReason inExperimentReason,
        boolean alwaysInExperiment
        ) {
      this.regularResult = EvalResult.of(value, variationIndex, regularReason).withForceReasonTracking(alwaysInExperiment);
      this.inExperimentResult = EvalResult.of(value, variationIndex, inExperimentReason).withForceReasonTracking(true);
    }
    
    EvalResult getResult(boolean inExperiment) {
      return inExperiment ? inExperimentResult : regularResult;
    }
  }
  
  static final class EvalResultFactoryMultiVariations {
    private final List<EvalResultsForSingleVariation> variations;
    
    EvalResultFactoryMultiVariations(
        List<EvalResultsForSingleVariation> variations
        ) {
      this.variations = variations;
    }
    
    EvalResult forVariation(int index, boolean inExperiment) {
      if (index < 0 || index >= variations.size()) {
        return EvalResult.error(ErrorKind.MALFORMED_FLAG);
      }

      if (variations.get(index) == null) {
        // getting here indicates that the preprocessor incorrectly processed data and another piece of code is
        // asking for a variation that was not populated ahead of time.  This is an unexpected bug if it happens.
        return EvalResult.error(ErrorKind.EXCEPTION);
      }

      return variations.get(index).getResult(inExperiment);
    }
  }
  
  static final class FlagPreprocessed {
    EvalResult offResult;
    EvalResultFactoryMultiVariations fallthroughResults;
    
    FlagPreprocessed(EvalResult offResult,
        EvalResultFactoryMultiVariations fallthroughResults) {
      this.offResult = offResult;
      this.fallthroughResults = fallthroughResults;
    }
  }
  
  static final class PrerequisitePreprocessed {
    final EvalResult prerequisiteFailedResult;
    
    PrerequisitePreprocessed(EvalResult prerequisiteFailedResult) {
      this.prerequisiteFailedResult = prerequisiteFailedResult;
    }
  }
  
  static final class TargetPreprocessed {
    final EvalResult targetMatchResult;
    
    TargetPreprocessed(EvalResult targetMatchResult) {
      this.targetMatchResult = targetMatchResult;
    }
  }
  
  static final class FlagRulePreprocessed {
    final EvalResultFactoryMultiVariations allPossibleResults;
    
    FlagRulePreprocessed(
        EvalResultFactoryMultiVariations allPossibleResults
        ) {
      this.allPossibleResults = allPossibleResults;
    }
  }
  
  static final class ClausePreprocessed {
    final Set<LDValue> valuesSet;
    final List<ValueData> valuesExtra;
    
    ClausePreprocessed(Set<LDValue> valuesSet, List<ValueData> valuesExtra) {
      this.valuesSet = valuesSet;
      this.valuesExtra = valuesExtra;
    }
    
    static final class ValueData {
      final Instant parsedDate;
      final Pattern parsedRegex;
      final SemanticVersion parsedSemVer;
      
      ValueData(Instant parsedDate, Pattern parsedRegex, SemanticVersion parsedSemVer) {
        this.parsedDate = parsedDate;
        this.parsedRegex = parsedRegex;
        this.parsedSemVer = parsedSemVer;
      }
    }
  }

  static void preprocessFlag(FeatureFlag f) {
    f.preprocessed = new FlagPreprocessed(
        EvaluatorHelpers.offResult(f),
        precomputeMultiVariationResultsForFlag(f, EvaluationReason.fallthrough(false),
            EvaluationReason.fallthrough(true), f.isTrackEventsFallthrough())
        );
    
    for (Prerequisite p: f.getPrerequisites()) {
      preprocessPrerequisite(p, f);
    }
    for (Target t: f.getTargets()) {
      preprocessTarget(t, f);
    }
    for (Target t: f.getContextTargets()) {
      preprocessTarget(t, f);
    }
    List<Rule> rules = f.getRules();
    int n = rules.size();
    for (int i = 0; i < n; i++) {
      preprocessFlagRule(rules.get(i), i, f);
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
  
  static void preprocessPrerequisite(Prerequisite p, FeatureFlag f) {
    // Precompute an immutable EvaluationDetail instance that will be used if the prerequisite fails.
    // This behaves the same as an "off" result except for the reason.
    p.preprocessed = new PrerequisitePreprocessed(EvaluatorHelpers.prerequisiteFailedResult(f, p));
  }
  
  static void preprocessTarget(Target t, FeatureFlag f) {
    // Precompute an immutable EvalResult instance that will be used if this target matches.
    t.preprocessed = new TargetPreprocessed(EvaluatorHelpers.targetMatchResult(f, t));
  }
  
  static void preprocessFlagRule(Rule r, int ruleIndex, FeatureFlag f) {
    EvaluationReason ruleMatchReason = EvaluationReason.ruleMatch(ruleIndex, r.getId(), false);
    EvaluationReason ruleMatchReasonInExperiment = EvaluationReason.ruleMatch(ruleIndex, r.getId(), true);
    r.preprocessed = new FlagRulePreprocessed(precomputeMultiVariationResultsForRule(f, r,
        ruleMatchReason, ruleMatchReasonInExperiment, r.isTrackEvents()));
    
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
    if (op == in) {
      // This is a special case where the clause is testing for an exact match against any of the
      // clause values. Converting the value list to a Set allows us to do a fast lookup instead of
      // a linear search. We do not do this for other operators (or if there are fewer than two
      // values) because the slight extra overhead of a Set is not worthwhile in those case.
      if (values.size() > 1) {
        c.preprocessed = new ClausePreprocessed(ImmutableSet.copyOf(values), null);
      }
    } else if (op == matches) {
      c.preprocessed = preprocessClauseValues(c.getValues(), v ->
        new ClausePreprocessed.ValueData(null, EvaluatorTypeConversion.valueToRegex(v), null)
      );
    } else if (op == after || op == before) {
      c.preprocessed = preprocessClauseValues(c.getValues(), v ->
        new ClausePreprocessed.ValueData(EvaluatorTypeConversion.valueToDateTime(v), null, null)
      );
    } else if (op == semVerEqual || op == semVerGreaterThan || op == semVerLessThan) {
      c.preprocessed = preprocessClauseValues(c.getValues(), v ->
        new ClausePreprocessed.ValueData(null, null, EvaluatorTypeConversion.valueToSemVer(v))
      );
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
  
  private static ClausePreprocessed preprocessClauseValues(
      List<LDValue> values,
      Function<LDValue, ClausePreprocessed.ValueData> f
      ) {
    List<ClausePreprocessed.ValueData> valuesExtra = new ArrayList<>(values.size());
    for (LDValue v: values) {
      valuesExtra.add(f.apply(v));
    }
    return new ClausePreprocessed(null, valuesExtra);
  }
  
  private static EvalResultFactoryMultiVariations precomputeMultiVariationResultsForFlag(
      FeatureFlag f,
      EvaluationReason regularReason,
      EvaluationReason inExperimentReason,
      boolean alwaysInExperiment
      ) {
    ArrayList<EvalResultsForSingleVariation> variations = new ArrayList<>(f.getVariations().size());
    for (int i = 0; i < f.getVariations().size(); i++) {
      variations.add(new EvalResultsForSingleVariation(f.getVariations().get(i), i,
          regularReason, inExperimentReason, alwaysInExperiment));
    }
    return new EvalResultFactoryMultiVariations(Collections.unmodifiableList(variations));
  }

  private static EvalResultFactoryMultiVariations precomputeMultiVariationResultsForRule(
      FeatureFlag f,
      Rule r,
      EvaluationReason regularReason,
      EvaluationReason inExperimentReason,
      boolean alwaysInExperiment
  ) {
    // Here we create a list of nulls and then insert into that list variations from the rule at their associated index.
    // This allows the evaluator to then index into the array in constant time. Alternative options are to use a map or
    // a sparse array. The map has high memory footprint for most customer situations, so it was not used. There is no
    // standard implementation for sparse array, and it is also not always constant time. Most customers don't have
    // many variations per flag and so these arrays should not be large on average. This approach was part of a bugfix
    // and this approach cuts the memory footprint enough to meet the need.
    List<EvalResultsForSingleVariation> variations = new ArrayList<>(Collections.nCopies(f.getVariations().size(), null));
    if (r.getVariation() != null) {
      int index = r.getVariation();
      if (index >= 0 && index < f.getVariations().size()) {
        variations.set(index, new EvalResultsForSingleVariation(f.getVariations().get(index), index,
            regularReason, inExperimentReason, alwaysInExperiment));
      }
    }

    if (r.getRollout() != null && r.getRollout().getVariations() != null) {
      for (DataModel.WeightedVariation wv : r.getRollout().getVariations()) {
        int index = wv.getVariation();
        if (index >= 0 && index < f.getVariations().size()) {
          variations.set(index, new EvalResultsForSingleVariation(f.getVariations().get(index), index,
              regularReason, inExperimentReason, alwaysInExperiment));
        }
      }
    }

    return new EvalResultFactoryMultiVariations(Collections.unmodifiableList(variations));
  }
}
