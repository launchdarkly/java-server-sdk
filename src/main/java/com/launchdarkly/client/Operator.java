package com.launchdarkly.client;

import java.util.regex.Pattern;

import com.google.gson.JsonPrimitive;

/**
 * Operator value that can be applied to {@link JsonPrimitive} objects. Incompatible types or other errors
 * will always yield false. This enum can be directly deserialized from JSON, avoiding the need for a mapping
 * of strings to operators.
 */
enum Operator {
  in {
    @Override
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      if (uValue.equals(cValue)) {
          return true;
      }
      OperandType type = OperandType.bestGuess(uValue);
      if (type == OperandType.bestGuess(cValue)) {
        return compareValues(ComparisonOp.EQ, uValue, cValue, type);
      }
      return false;
    }
  },
  endsWith {
    @Override
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isString() && cValue.isString() && uValue.getAsString().endsWith(cValue.getAsString());
    }
  },
  startsWith {
    @Override
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isString() && cValue.isString() && uValue.getAsString().startsWith(cValue.getAsString());
    }
  },
  matches {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isString() && cValue.isString() &&
              Pattern.compile(cValue.getAsString()).matcher(uValue.getAsString()).find();
    }
  },
  contains {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isString() && cValue.isString() && uValue.getAsString().contains(cValue.getAsString());
    }
  },
  lessThan {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return compareValues(ComparisonOp.LT, uValue, cValue, OperandType.number);
    }
  },
  lessThanOrEqual {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return compareValues(ComparisonOp.LTE, uValue, cValue, OperandType.number);
    }
  },
  greaterThan {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return compareValues(ComparisonOp.GT, uValue, cValue, OperandType.number);
    }
  },
  greaterThanOrEqual {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return compareValues(ComparisonOp.GTE, uValue, cValue, OperandType.number);
    }
  },
  before {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return compareValues(ComparisonOp.LT, uValue, cValue, OperandType.date);
    }
  },
  after {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return compareValues(ComparisonOp.GT, uValue, cValue, OperandType.date);
    }
  },
  semVerEqual {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return compareValues(ComparisonOp.EQ, uValue, cValue, OperandType.semVer);
    }
  },
  semVerLessThan {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return compareValues(ComparisonOp.LT, uValue, cValue, OperandType.semVer);
    }
  },
  semVerGreaterThan {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return compareValues(ComparisonOp.GT, uValue, cValue, OperandType.semVer);
    }
  },
  segmentMatch {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      // We shouldn't call apply() for this operator, because it is really implemented in
      // Clause.matchesUser().
      return false;
    }
  };

  abstract boolean apply(JsonPrimitive uValue, JsonPrimitive cValue);
  
  private static boolean compareValues(ComparisonOp op, JsonPrimitive uValue, JsonPrimitive cValue, OperandType asType) {
    Object uValueObj = asType.getValueAsType(uValue);
    Object cValueObj = asType.getValueAsType(cValue);
    return uValueObj != null && cValueObj != null && op.apply(uValueObj, cValueObj);
  }
  
  private static enum ComparisonOp {
    EQ,
    LT,
    LTE,
    GT,
    GTE;
    
    @SuppressWarnings("unchecked")
    public boolean apply(Object a, Object b) {
      if (a instanceof Comparable && a.getClass() == b.getClass()) {
        int n = ((Comparable<Object>)a).compareTo(b);
        switch (this) {
        case EQ: return (n == 0);
        case LT: return (n < 0);
        case LTE: return (n <= 0);
        case GT: return (n > 0);
        case GTE: return (n >= 0);
        }
      }
      return false;
    }
  }
}
