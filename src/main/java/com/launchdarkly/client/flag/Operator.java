package com.launchdarkly.client.flag;

import com.google.gson.JsonPrimitive;

import java.util.regex.Pattern;

public enum Operator {
  in {
    @Override
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      if (uValue.isString() && cValue.isString()) {
        return uValue.getAsString().equals(cValue.getAsString());
      }
      if (uValue.isNumber() && cValue.isNumber()) {
        //TODO: deal with rounding.. maybe.
        return uValue.getAsNumber().doubleValue() == cValue.getAsNumber().doubleValue();
      }
      //TODO: deal with date type.
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
      //TODO: deal with rounding.. maybe.
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsNumber().doubleValue() < cValue.getAsNumber().doubleValue();
    }
  },
  lessThanOrEqual {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      //TODO: deal with rounding.. maybe.
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsNumber().doubleValue() <= cValue.getAsNumber().doubleValue();
    }
  },
  greaterThan {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      //TODO: deal with rounding.. maybe.
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsNumber().doubleValue() > cValue.getAsNumber().doubleValue();
    }
  },
  greaterThanOrEqual {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      //TODO: deal with rounding.. maybe.
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsNumber().doubleValue() >= cValue.getAsNumber().doubleValue();
    }
  },
  before {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      //TODO: deal with date type.
      return false;
    }
  },
  after {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      //TODO: deal with date type.
      return false;
    }
  };

  public abstract boolean apply(JsonPrimitive uValue, JsonPrimitive cValue);
}
