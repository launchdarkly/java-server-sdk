package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

/**
 * Defines the behavior of all operators that can be used in feature flag rules and segment rules.
 */
abstract class EvaluatorOperators {
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
      return false;
    }
  }

  static boolean apply(DataModel.Operator op, LDValue userValue, LDValue clauseValue) {
    switch (op) {
    case in:
      return userValue.equals(clauseValue);
      
    case endsWith:
      return userValue.isString() && clauseValue.isString() && userValue.stringValue().endsWith(clauseValue.stringValue());
      
    case startsWith:
      return userValue.isString() && clauseValue.isString() && userValue.stringValue().startsWith(clauseValue.stringValue());
    
    case matches:
      return userValue.isString() && clauseValue.isString() &&
              Pattern.compile(clauseValue.stringValue()).matcher(userValue.stringValue()).find();

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
      return compareDate(ComparisonOp.LT, userValue, clauseValue);

    case after:
      return compareDate(ComparisonOp.GT, userValue, clauseValue);

    case semVerEqual:
      return compareSemVer(ComparisonOp.EQ, userValue, clauseValue);

    case semVerLessThan:
      return compareSemVer(ComparisonOp.LT, userValue, clauseValue);

    case semVerGreaterThan:
      return compareSemVer(ComparisonOp.GT, userValue, clauseValue);

    case segmentMatch:
      // We shouldn't call apply() for this operator, because it is really implemented in
      // Evaluator.clauseMatchesUser().
      return false;
    };
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

  private static boolean compareDate(ComparisonOp op, LDValue userValue, LDValue clauseValue) {
    ZonedDateTime dt1 = valueToDateTime(userValue);
    ZonedDateTime dt2 = valueToDateTime(clauseValue);
    if (dt1 == null || dt2 == null) {
      return false;
    }
    return op.test(dt1.compareTo(dt2));
  }

  private static boolean compareSemVer(ComparisonOp op, LDValue userValue, LDValue clauseValue) {
    SemanticVersion sv1 = valueToSemVer(userValue);
    SemanticVersion sv2 = valueToSemVer(clauseValue);
    if (sv1 == null || sv2 == null) {
      return false;
    }
    return op.test(sv1.compareTo(sv2));
  }
  
  private static ZonedDateTime valueToDateTime(LDValue value) {
    if (value.isNumber()) {
      return ZonedDateTime.ofInstant(Instant.ofEpochMilli(value.longValue()), ZoneOffset.UTC);
    } else if (value.isString()) {
      try {
        return ZonedDateTime.parse(value.stringValue());
      } catch (Throwable t) {
        return null;
      }
    } else {
      return null;
    }
  }
  
  private static SemanticVersion valueToSemVer(LDValue value) {
    if (!value.isString()) {
      return null;
    }
    try {
      return SemanticVersion.parse(value.stringValue(), true);
    } catch (SemanticVersion.InvalidVersionException e) {
      return null;
    }
  }
}
