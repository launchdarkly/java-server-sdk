package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModelPreprocessing.ClausePreprocessed;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.launchdarkly.sdk.server.EvaluatorTypeConversion.valueToDateTime;
import static com.launchdarkly.sdk.server.EvaluatorTypeConversion.valueToRegex;
import static com.launchdarkly.sdk.server.EvaluatorTypeConversion.valueToSemVer;

/**
 * Defines the behavior of all operators that can be used in feature flag rules and segment rules.
 */
abstract class EvaluatorOperators {
  private EvaluatorOperators() {}
  
  private static interface OperatorFn {
    boolean match(LDValue userValue, LDValue clauseValue, ClausePreprocessed.ValueData preprocessed);
  }
  
  private static final Map<Operator, OperatorFn> OPERATORS = new HashMap<>();
  static {
    OPERATORS.put(Operator.in, EvaluatorOperators::applyIn);
    OPERATORS.put(Operator.startsWith, EvaluatorOperators::applyStartsWith);
    OPERATORS.put(Operator.endsWith, EvaluatorOperators::applyEndsWith);
    OPERATORS.put(Operator.matches, EvaluatorOperators::applyMatches);
    OPERATORS.put(Operator.contains, EvaluatorOperators::applyContains);
    OPERATORS.put(Operator.lessThan, numericComparison(delta -> delta < 0));
    OPERATORS.put(Operator.lessThanOrEqual, numericComparison(delta -> delta <= 0));
    OPERATORS.put(Operator.greaterThan, numericComparison(delta -> delta > 0));
    OPERATORS.put(Operator.greaterThanOrEqual, numericComparison(delta -> delta >= 0));
    OPERATORS.put(Operator.before, dateComparison(delta -> delta < 0));
    OPERATORS.put(Operator.after, dateComparison(delta -> delta > 0));
    OPERATORS.put(Operator.semVerEqual, semVerComparison(delta -> delta == 0));
    OPERATORS.put(Operator.semVerLessThan, semVerComparison(delta -> delta < 0));
    OPERATORS.put(Operator.semVerGreaterThan, semVerComparison(delta -> delta > 0));
    // Operator.segmentMatch is deliberately not included here, because it is implemented
    // separately in Evaluator.
  }
  
  static boolean apply(
      DataModel.Operator op,
      LDValue userValue,
      LDValue clauseValue,
      ClausePreprocessed.ValueData preprocessed
      ) {
    OperatorFn fn = OPERATORS.get(op);
    return fn != null && fn.match(userValue, clauseValue, preprocessed);
  }

  static boolean applyIn(LDValue userValue, LDValue clauseValue, ClausePreprocessed.ValueData preprocessed) {
    return userValue.equals(clauseValue);
  }

  static boolean applyStartsWith(LDValue userValue, LDValue clauseValue, ClausePreprocessed.ValueData preprocessed) {
    return userValue.isString() && clauseValue.isString() && userValue.stringValue().startsWith(clauseValue.stringValue());
  }

  static boolean applyEndsWith(LDValue userValue, LDValue clauseValue, ClausePreprocessed.ValueData preprocessed) {
    return userValue.isString() && clauseValue.isString() && userValue.stringValue().endsWith(clauseValue.stringValue());
  }

  static boolean applyMatches(LDValue userValue, LDValue clauseValue, ClausePreprocessed.ValueData preprocessed) {
    // If preprocessed is non-null, it means we've already tried to parse the clause value as a regex,
    // in which case if preprocessed.parsedRegex is null it was not a valid regex.
    Pattern clausePattern = preprocessed == null ? valueToRegex(clauseValue) : preprocessed.parsedRegex;
    return clausePattern != null && userValue.isString() &&
        clausePattern.matcher(userValue.stringValue()).find();
  }

  static boolean applyContains(LDValue userValue, LDValue clauseValue, ClausePreprocessed.ValueData preprocessed) {
    return userValue.isString() && clauseValue.isString() && userValue.stringValue().contains(clauseValue.stringValue());
  }

  static OperatorFn numericComparison(Function<Integer, Boolean> comparisonTest) {
    return (userValue, clauseValue, preprocessed) -> {
      if (!userValue.isNumber() || !clauseValue.isNumber()) {
        return false;
      }
      double n1 = userValue.doubleValue();
      double n2 = clauseValue.doubleValue();
      int delta = n1 == n2 ? 0 : (n1 < n2 ? -1 : 1);
      return comparisonTest.apply(delta);
    };
  }

  static OperatorFn dateComparison(Function<Integer, Boolean> comparisonTest) {
    return (userValue, clauseValue, preprocessed) -> {
      // If preprocessed is non-null, it means we've already tried to parse the clause value as a date/time,
      // in which case if preprocessed.parsedDate is null it was not a valid date/time.
      Instant clauseDate = preprocessed == null ? valueToDateTime(clauseValue) : preprocessed.parsedDate;
      if (clauseDate == null) {
        return false;
      }
      Instant userDate = valueToDateTime(userValue);
      if (userDate == null) {
        return false;
      }
      return comparisonTest.apply(userDate.compareTo(clauseDate));
    };
  }
  
  static OperatorFn semVerComparison(Function<Integer, Boolean> comparisonTest) {
    return (userValue, clauseValue, preprocessed) -> {
      // If preprocessed is non-null, it means we've already tried to parse the clause value as a version,
      // in which case if preprocessed.parsedSemVer is null it was not a valid version.
      SemanticVersion clauseVer = preprocessed == null ? valueToSemVer(clauseValue) : preprocessed.parsedSemVer;
      if (clauseVer == null) {
        return false;
      }
      SemanticVersion userVer = valueToSemVer(userValue);
      if (userVer == null) {
        return false;
      }
      return comparisonTest.apply(userVer.compareTo(clauseVer));
    };
  }
}
