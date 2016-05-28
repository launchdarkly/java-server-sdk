package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import org.joda.time.DateTime;

import java.util.regex.Pattern;

/**
 * Operator value that can be applied to {@link JsonPrimitive} objects. Incompatible types or other errors
 * will always yield false. This enum can be directly deserialized from JSON, avoiding the need for a mapping
 * of strings to operators.
 */
enum Operator {
  in {
    @Override
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      if (uValue.isString() && cValue.isString()) {
        if (uValue.getAsString().equals(cValue.getAsString()))
          return true;
      }
      if (uValue.isNumber() && cValue.isNumber()) {
        return uValue.getAsDouble() == cValue.getAsDouble();
      }
      DateTime uDateTime = Util.jsonPrimitiveToDateTime(uValue);
      if (uDateTime != null) {
        DateTime cDateTime = Util.jsonPrimitiveToDateTime(cValue);
        if (cDateTime != null) {
          return uDateTime.getMillis() == cDateTime.getMillis();
        }
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
      return uValue.isString() && cValue.isString() && Pattern.matches(cValue.getAsString(), uValue.getAsString());
    }
  },
  contains {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isString() && cValue.isString() && uValue.getAsString().contains(cValue.getAsString());
    }
  },
  lessThan {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsDouble() < cValue.getAsDouble();
    }
  },
  lessThanOrEqual {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsDouble() <= cValue.getAsDouble();
    }
  },
  greaterThan {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsDouble() > cValue.getAsDouble();
    }
  },
  greaterThanOrEqual {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsDouble() >= cValue.getAsDouble();
    }
  },
  before {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      DateTime uDateTime = Util.jsonPrimitiveToDateTime(uValue);
      if (uDateTime != null) {
        DateTime cDateTime = Util.jsonPrimitiveToDateTime(cValue);
        if (cDateTime != null) {
          return uDateTime.isBefore(cDateTime);
        }
      }
      return false;
    }
  },
  after {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      DateTime uDateTime = Util.jsonPrimitiveToDateTime(uValue);
      if (uDateTime != null) {
        DateTime cDateTime = Util.jsonPrimitiveToDateTime(cValue);
        if (cDateTime != null) {
          return uDateTime.isAfter(cDateTime);
        }
      }
      return false;
    }
  };
  abstract boolean apply(JsonPrimitive uValue, JsonPrimitive cValue);
}
