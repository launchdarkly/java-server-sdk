package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
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
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.server.EvaluatorHelpers.resultForVariation;

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

  static final class EvaluationDetailsForSingleVariation {
    private final EvaluationDetail<LDValue> regularResult;
    private final EvaluationDetail<LDValue> inExperimentResult;
    
    EvaluationDetailsForSingleVariation(
        LDValue value,
        int variationIndex,
        EvaluationReason regularReason,
        EvaluationReason inExperimentReason
        ) {
      this.regularResult = EvaluationDetail.fromValue(value, variationIndex, regularReason);
      this.inExperimentResult = EvaluationDetail.fromValue(value, variationIndex, inExperimentReason);
    }
    
    EvaluationDetail<LDValue> getResult(boolean inExperiment) {
      return inExperiment ? inExperimentResult : regularResult;
    }
  }
  
  static final class EvaluationDetailFactoryMultiVariations {
    private final ImmutableList<EvaluationDetailsForSingleVariation> variations;
    
    EvaluationDetailFactoryMultiVariations(
        ImmutableList<EvaluationDetailsForSingleVariation> variations
        ) {
      this.variations = variations;
    }
    
    EvaluationDetail<LDValue> forVariation(int index, boolean inExperiment) {
      if (index < 0 || index >= variations.size()) {
        return EvaluationDetail.error(ErrorKind.MALFORMED_FLAG, LDValue.ofNull());
      }
      return variations.get(index).getResult(inExperiment);
    }
  }
  
  static final class FlagPreprocessed {
    EvaluationDetail<LDValue> offResult;
    EvaluationDetailFactoryMultiVariations fallthroughResults;
    
    FlagPreprocessed(EvaluationDetail<LDValue> offResult,
        EvaluationDetailFactoryMultiVariations fallthroughResults) {
      this.offResult = offResult;
      this.fallthroughResults = fallthroughResults;
    }
  }
  
  static final class PrerequisitePreprocessed {
    final EvaluationDetail<LDValue> prerequisiteFailedResult;
    
    PrerequisitePreprocessed(EvaluationDetail<LDValue> prerequisiteFailedResult) {
      this.prerequisiteFailedResult = prerequisiteFailedResult;
    }
  }
  
  static final class TargetPreprocessed {
    final EvaluationDetail<LDValue> targetMatchResult;
    
    TargetPreprocessed(EvaluationDetail<LDValue> targetMatchResult) {
      this.targetMatchResult = targetMatchResult;
    }
  }
  
  static final class FlagRulePreprocessed {
    final EvaluationDetailFactoryMultiVariations allPossibleResults;
    
    FlagRulePreprocessed(
        EvaluationDetailFactoryMultiVariations allPossibleResults
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
        precomputeSingleVariationResult(f, f.getOffVariation(), EvaluationReason.off()),
        precomputeMultiVariationResults(f, EvaluationReason.fallthrough(false),
            EvaluationReason.fallthrough(true))
        );
    
    for (Prerequisite p: f.getPrerequisites()) {
      preprocessPrerequisite(p, f);
    }
    for (Target t: f.getTargets()) {
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
    EvaluationDetail<LDValue> failureResult = precomputeSingleVariationResult(f,
        f.getOffVariation(), EvaluationReason.prerequisiteFailed(p.getKey()));
    p.preprocessed = new PrerequisitePreprocessed(failureResult);
  }
  
  static void preprocessTarget(Target t, FeatureFlag f) {
    // Precompute an immutable EvaluationDetail instance that will be used if this target matches.
    t.preprocessed = new TargetPreprocessed(
        resultForVariation(t.getVariation(), f, EvaluationReason.targetMatch())
        );
  }
  
  static void preprocessFlagRule(Rule r, int ruleIndex, FeatureFlag f) {
    EvaluationReason ruleMatchReason = EvaluationReason.ruleMatch(ruleIndex, r.getId(), false);
    EvaluationReason ruleMatchReasonInExperiment = EvaluationReason.ruleMatch(ruleIndex, r.getId(), true);
    r.preprocessed = new FlagRulePreprocessed(precomputeMultiVariationResults(f,
        ruleMatchReason, ruleMatchReasonInExperiment));
    
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
        c.preprocessed = new ClausePreprocessed(ImmutableSet.copyOf(values), null);
      }
      break;
    case matches:
      c.preprocessed = preprocessClauseValues(c.getValues(), v ->
        new ClausePreprocessed.ValueData(null, EvaluatorTypeConversion.valueToRegex(v), null)
      );
      break;
    case after:
    case before:
      c.preprocessed = preprocessClauseValues(c.getValues(), v ->
        new ClausePreprocessed.ValueData(EvaluatorTypeConversion.valueToDateTime(v), null, null)
      );
      break;
    case semVerEqual:
    case semVerGreaterThan:
    case semVerLessThan:
      c.preprocessed = preprocessClauseValues(c.getValues(), v ->
        new ClausePreprocessed.ValueData(null, null, EvaluatorTypeConversion.valueToSemVer(v))
      );
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
  
  private static EvaluationDetail<LDValue> precomputeSingleVariationResult(
      FeatureFlag f,
      Integer index,
      EvaluationReason reason
      ) {
    if (index == null) {
      return EvaluationDetail.fromValue(LDValue.ofNull(), NO_VARIATION, reason);
    }
    int i = index.intValue();
    if (i < 0 || i >= f.getVariations().size()) {
      return EvaluationDetail.fromValue(LDValue.ofNull(), NO_VARIATION,
          EvaluationReason.error(ErrorKind.MALFORMED_FLAG));
    }
    return EvaluationDetail.fromValue(f.getVariations().get(i), index, reason);
  }
  
  private static EvaluationDetailFactoryMultiVariations precomputeMultiVariationResults(
      FeatureFlag f,
      EvaluationReason regularReason,
      EvaluationReason inExperimentReason
      ) {
    ImmutableList.Builder<EvaluationDetailsForSingleVariation> builder =
        ImmutableList.builderWithExpectedSize(f.getVariations().size());
    for (int i = 0; i < f.getVariations().size(); i++) {
      builder.add(new EvaluationDetailsForSingleVariation(f.getVariations().get(i), i,
          regularReason, inExperimentReason));
    }
    return new EvaluationDetailFactoryMultiVariations(builder.build());
  }
}
