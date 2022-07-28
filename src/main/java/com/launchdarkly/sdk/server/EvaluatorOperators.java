package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModelPreprocessing.ClausePreprocessed;

import java.time.Instant;
import java.util.regex.Pattern;

import static com.launchdarkly.sdk.server.EvaluatorTypeConversion.valueToDateTime;
import static com.launchdarkly.sdk.server.EvaluatorTypeConversion.valueToRegex;
import static com.launchdarkly.sdk.server.EvaluatorTypeConversion.valueToSemVer;

/**
 * Defines the behavior of all operators that can be used in feature flag rules and segment rules.
 */
abstract class EvaluatorOperators {
  private EvaluatorOperators() {}
  
  private static enum ComparisonOp {
    EQ,
    LT,
    LTE,
    GT,
    GTE;
    
    boolean test(int delta) {
      switch (this) {
      case EQ:
        return delta == 0;
      case LT:
        return delta < 0;
      case LTE:
        return delta <= 0;
      case GT:
        return delta > 0;
      case GTE:
        return delta >= 0;
      }
      // COVERAGE: the compiler insists on a fallthrough line here, even though it's unreachable
      return false;
    }
  }

  static boolean apply(
      DataModel.Operator op,
      LDValue userValue,
      LDValue clauseValue,
      ClausePreprocessed.ValueData preprocessed
      ) {
    switch (op) {
    case in:
      return userValue.equals(clauseValue);
      
    case endsWith:
      return userValue.isString() && clauseValue.isString() && userValue.stringValue().endsWith(clauseValue.stringValue());
      
    case startsWith:
      return userValue.isString() && clauseValue.isString() && userValue.stringValue().startsWith(clauseValue.stringValue());
    
    case matches:
      // If preprocessed is non-null, it means we've already tried to parse the clause value as a regex,
      // in which case if preprocessed.parsedRegex is null it was not a valid regex.
      Pattern clausePattern = preprocessed == null ? valueToRegex(clauseValue) : preprocessed.parsedRegex;
      return clausePattern != null && userValue.isString() &&
          clausePattern.matcher(userValue.stringValue()).find();

    case contains:
      return userValue.isString() && clauseValue.isString() && userValue.stringValue().contains(clauseValue.stringValue());

    case lessThan:
      return compareNumeric(ComparisonOp.LT, userValue, clauseValue);

    case lessThanOrEqual:
      return compareNumeric(ComparisonOp.LTE, userValue, clauseValue);

    case greaterThan:
      return compareNumeric(ComparisonOp.GT, userValue, clauseValue);

    case greaterThanOrEqual:
      return compareNumeric(ComparisonOp.GTE, userValue, clauseValue);

    case before:
      return compareDate(ComparisonOp.LT, userValue, clauseValue, preprocessed);

    case after:
      return compareDate(ComparisonOp.GT, userValue, clauseValue, preprocessed);

    case semVerEqual:
      return compareSemVer(ComparisonOp.EQ, userValue, clauseValue, preprocessed);

    case semVerLessThan:
      return compareSemVer(ComparisonOp.LT, userValue, clauseValue, preprocessed);

    case semVerGreaterThan:
      return compareSemVer(ComparisonOp.GT, userValue, clauseValue, preprocessed);

    case segmentMatch:
      // We shouldn't call apply() for this operator, because it is really implemented in
      // Evaluator.clauseMatchesUser().
      return false;
    };
    // COVERAGE: the compiler insists on a fallthrough line here, even though it's unreachable
    return false;
  }

  private static boolean compareNumeric(ComparisonOp op, LDValue userValue, LDValue clauseValue) {
    if (!userValue.isNumber() || !clauseValue.isNumber()) {
      return false;
    }
    double n1 = userValue.doubleValue();
    double n2 = clauseValue.doubleValue();
    int compare = n1 == n2 ? 0 : (n1 < n2 ? -1 : 1);
    return op.test(compare);
  }

  private static boolean compareDate(
      ComparisonOp op,
      LDValue userValue,
      LDValue clauseValue,
      ClausePreprocessed.ValueData preprocessed
      ) {
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
    return op.test(userDate.compareTo(clauseDate));
  }

  private static boolean compareSemVer(
      ComparisonOp op,
      LDValue userValue,
      LDValue clauseValue,
      ClausePreprocessed.ValueData preprocessed
      ) {
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
    return op.test(userVer.compareTo(clauseVer));
  }
}
